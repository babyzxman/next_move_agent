package org.gable.blendata.nextmove.client.service.connection;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.common.kex.KeyExchangeFactory;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.signature.Signature;
import org.apache.sshd.common.signature.SignatureFactory;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.gable.blendata.nextmove.client.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.client.adapter.factory.FileSystemAdapterFactory;
import org.gable.blendata.nextmove.client.config.SftpConnectionProperties;

import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SftpConnectionManager implements ConnectionManager {

    private final SftpConnectionProperties properties;
    private final FileSystemAdapterFactory adapterFactory;

    // Two profiles
    private static final SshClient modernClient;
    private static final SshClient legacyClient;

    // host:port -> whether legacy is required
    private static final ConcurrentHashMap<String, Boolean> useLegacyByHost = new ConcurrentHashMap<>();

    private static void logClientKex(String name, SshClient c) {
        log.error("{} KEX = {}", name,
                c.getKeyExchangeFactories().stream().map(KeyExchangeFactory::getName).toArray());
    }

    static {
        // MODERN: defaults
        modernClient = SshClient.setUpDefaultClient();
        modernClient.start();
        logClientKex("MODERN", modernClient);

        // LEGACY: defaults + enable group14-sha1 (and optionally gex-sha1)
        legacyClient = SshClient.setUpDefaultClient();

// Build KEX list from DH factories, then set it
        List<KeyExchangeFactory> legacyKex =
                NamedFactory.setUpTransformedFactories(
                        false,
                        BuiltinDHFactories.VALUES,
                        ClientBuilder.DH2KEX
                );

// Force preference order: group14-sha1 first, then gex-sha1, etc.
        legacyKex.sort((a, b) -> {
            String an = a.getName();
            String bn = b.getName();
            if ("diffie-hellman-group14-sha1".equals(an)) return -1;
            if ("diffie-hellman-group14-sha1".equals(bn)) return 1;
            if ("diffie-hellman-group-exchange-sha1".equals(an)) return -1;
            if ("diffie-hellman-group-exchange-sha1".equals(bn)) return 1;
            return 0;
        });

        legacyClient.setKeyExchangeFactories(legacyKex);
        List<NamedFactory<Signature>> sigs = legacyClient.getSignatureFactories();
        if (sigs.stream().noneMatch(f -> "ssh-dss".equalsIgnoreCase(f.getName()))) {
            sigs.add(0, BuiltinSignatures.dsa); // enables ssh-dss
        }
        legacyClient.setSignatureFactories(sigs);
        legacyClient.start();

        logClientKex("LEGACY", legacyClient);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                modernClient.stop();
                log.info("modernClient stopped during shutdown");
            } catch (Exception e) {
                log.error("Error stopping modernClient", e);
            }
            try {
                legacyClient.stop();
                log.info("legacyClient stopped during shutdown");
            } catch (Exception e) {
                log.error("Error stopping legacyClient", e);
            }
        }));
    }

    public SftpConnectionManager(SftpConnectionProperties properties) {
        this.properties = properties;
        this.adapterFactory = new FileSystemAdapterFactory();
    }

    private static boolean shouldContinueToNextAlgo(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof IllegalArgumentException) return true;

            String m = cur.getMessage();
            if (m == null) continue;
            m = m.toLowerCase();

            // Your exact error
            if (m.contains("no verifier located for algorithm")) return true;

            // Common MINA wraps
        }
        return false;
    }

    @Override
    public FileSystemAdapter createConnection() throws IOException {
        String key = ConnectionManagerUtil.getServerMapKey(properties.getHost(), properties.getPort());

        // If using private key and you have a list of signature algos to try:
        if (properties.getPrivateKeyPath() != null) {
            Set<String> algorithmSet = ConnectionManagerUtil.getSftpAlgorithmMap(key);
            Set<String> successKeySet = ConcurrentHashMap.newKeySet();

            if (algorithmSet != null && !algorithmSet.isEmpty()) {
                int count = 0;

                for (String algo : algorithmSet) {
                    count++;
                    log.info("Trying signature algorithm = {}", algo);

                    ClientSession session = null;
                    try {
                        session = connectAndAuthSmart(algo);  // auth happens here ONCE
                        SftpClient sftpClient = SftpClientFactory.instance().createSftpClient(session);

                        if (algorithmSet.size() != 1) {
                            successKeySet.add(algo);
                            ConnectionManagerUtil.setSftpAlogirthmSet(successKeySet, key);
                        }
                        return adapterFactory.createSftpAdapter(sftpClient, session);

                    } catch (Exception ex) {
                        if (session != null) {
                            try { session.close(); } catch (Exception ignore) {}
                        }

                        // keep your previous behavior: only continue on IllegalArgumentException
                        if (shouldContinueToNextAlgo(ex)) {
                            if (count >= algorithmSet.size()) {
                                ConnectionManagerUtil.setSftpAlogirthmSet(successKeySet, key);
                                throw new IOException(ex);
                            }
                            continue;
                        }
                        throw (ex instanceof IOException) ? (IOException) ex : new IOException(ex);
                    }
                }

                throw new IOException("Unable to authenticate using provided signature algorithms.");
            }

            // No per-host signature list -> default signature factories
            ClientSession session = connectAndAuthSmart(null);
            SftpClient sftpClient = SftpClientFactory.instance().createSftpClient(session);
            return adapterFactory.createSftpAdapter(sftpClient, session);
        }

        // Password auth (no re-auth)
        ClientSession session = connectAndAuthSmart(null);
        SftpClient sftpClient = SftpClientFactory.instance().createSftpClient(session);
        return adapterFactory.createSftpAdapter(sftpClient, session);
    }


    private ClientSession connectAndAuthSmart(String signatureAlgoOrNull) throws IOException {
        String hostKey = ConnectionManagerUtil.getServerMapKey(properties.getHost(), properties.getPort());
        boolean preferLegacy = useLegacyByHost.getOrDefault(hostKey, false);

        try {
            return connectAndAuth(preferLegacy ? legacyClient : modernClient, signatureAlgoOrNull);
        } catch (Exception first) {
            if (!preferLegacy && looksLikeLegacyNegotiation(first)) {
                try {
                    ClientSession s = connectAndAuth(legacyClient, signatureAlgoOrNull);
                    useLegacyByHost.put(hostKey, true);
                    log.info("Host {} classified as LEGACY after retry", hostKey);
                    return s;
                } catch (Exception second) {
                    throw new IOException(second);
                }
            }
            throw new IOException(first);
        }
    }

    private ClientSession connectAndAuth(SshClient client, String signatureAlgoOrNull) throws Exception {
        ClientSession session = client.connect(properties.getUsername(), properties.getHost(), properties.getPort())
                .verify(properties.getConnectionTimeout())
                .getSession();

        // IMPORTANT: set signature algorithms BEFORE auth (if provided)
        if (signatureAlgoOrNull != null) {
            session.setSignatureFactoriesNameList(signatureAlgoOrNull);
        } else {
            session.setSignatureFactories(ConnectionManagerUtil.getDEFAULT_SIGNATURE_FACTORIES());
        }

        // Add identity BEFORE auth
        if (properties.getPrivateKeyPath() != null) {
            Path keyPath = Paths.get(properties.getPrivateKeyPath());
            FileKeyPairProvider provider = new FileKeyPairProvider(keyPath);
            Iterable<KeyPair> keys = provider.loadKeys(null);
            session.addPublicKeyIdentity(keys.iterator().next());
        } else {
            session.addPasswordIdentity(properties.getPassword());
        }

        session.auth().verify(properties.getConnectionTimeout(), TimeUnit.MILLISECONDS);
        return session;
    }



    private boolean looksLikeLegacyNegotiation(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) cur = cur.getCause();
        String msg = (cur.getMessage() == null) ? "" : cur.getMessage().toLowerCase();

        return msg.contains("unable to negotiate key exchange")
                || (msg.contains("unable to negotiate") && msg.contains("kex"))
                || (msg.contains("no matching") && msg.contains("kex"));
    }

    @Override
    public void closeConnection(FileSystemAdapter adapter) throws IOException {
        if (adapter != null) {
            adapter.close();
        }
    }
}
