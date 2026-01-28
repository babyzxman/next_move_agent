package org.gable.blendata.nextmove.service.service.connection;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FileSystem;
import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.SshException;
import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.common.kex.KeyExchangeFactory;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.gable.blendata.nextmove.service.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.service.adapter.factory.FileSystemAdapterFactory;
import org.gable.blendata.nextmove.service.config.SftpConnectionProperties;
import org.gable.blendata.nextmove.shared.constant.AppConst;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SftpConnectionManager implements ConnectionManager {

    private final SftpConnectionProperties properties;
    private final FileSystem destFileSystem;
    private final FileSystemAdapterFactory adapterFactory;

    private static final ConcurrentHashMap<String, Boolean> useLegacyByHost = new ConcurrentHashMap<>();

    // Two profiles
    private static final SshClient modernClient;
    private static final SshClient legacyClient;

    // host:port -> whether legacy is required
    static {
        // MODERN: defaults
        modernClient = SshClient.setUpDefaultClient();
        modernClient.start();

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
        legacyClient.start();

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


    public SftpConnectionManager(SftpConnectionProperties properties, FileSystem destFileSystem, FileSystemAdapterFactory adapterFactory) {
        this.adapterFactory = adapterFactory;
        this.destFileSystem = destFileSystem;
        this.properties = properties;

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
                        return adapterFactory.createSftpAdapter(sftpClient, session,destFileSystem);

                    } catch (Exception ex) {
                        if (session != null) {
                            try { session.close(); } catch (Exception ignore) {}
                        }

                        // keep your previous behavior: only continue on IllegalArgumentException
                        if (ex.getCause() instanceof IllegalArgumentException) {
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
            return adapterFactory.createSftpAdapter(sftpClient, session,destFileSystem);
        }

        // Password auth (no re-auth)
        ClientSession session = connectAndAuthSmart(null);
        SftpClient sftpClient = SftpClientFactory.instance().createSftpClient(session);
        return adapterFactory.createSftpAdapter(sftpClient, session,destFileSystem);
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

    private ClientSession connect(SshClient c) throws Exception {
        return c.connect(properties.getUsername(), properties.getHost(), properties.getPort())
                .verify(properties.getConnectionTimeout())
                .getSession();
    }

    public FileSystemAdapter ensureConnectionAlive(FileSystemAdapter currentAdapter, FileSystem fs, String taskKey, String sourceRootPath) throws IOException {
        if (currentAdapter == null) {
            log.warn("{} Connection is null, creating new connection for task {}", AppConst.PREFIX_LOG, taskKey);
            return createConnection();
        }

        try {
            if (!isConnectionAlive(currentAdapter, fs,sourceRootPath)) {
                log.warn("{} Connection appears to be dead, recreating for task {}", AppConst.PREFIX_LOG, taskKey);
                closeConnectionSafely(currentAdapter, taskKey);
                return createConnection();
            }
        } catch (Exception e) {
            log.warn("{} Connection test failed, recreating for task {}: {}", AppConst.PREFIX_LOG, taskKey, e.getMessage());
            closeConnectionSafely(currentAdapter, taskKey);
            return createConnection();
        }

        return currentAdapter;
    }

    private void closeConnectionSafely(FileSystemAdapter adapter, String taskKey) {
        if (adapter != null) {
            try {
                closeConnection(adapter);
                log.debug("{} Connection closed successfully for task {}", AppConst.PREFIX_LOG, taskKey);
            } catch (Exception e) {
                log.error("{} Error closing connection for task {}: {}", AppConst.PREFIX_LOG, taskKey, e.getMessage());
            }
        }
    }

    private boolean isConnectionAlive(FileSystemAdapter adapter, FileSystem fs, String path) {
        try {
            adapter.exists(fs, path); // หรือ path ที่เหมาะสม
            return true;
        } catch (Exception e) {
            log.debug("{} Connection test failed: {}", AppConst.PREFIX_LOG, e.getMessage());
            return false;
        }
    }

    @Override
    public void closeConnection(FileSystemAdapter adapter) throws IOException {
        if (adapter != null) {
            adapter.close();
        }
    }
}
