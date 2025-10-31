package org.gable.blendata.nextmove.client.service.connection;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.gable.blendata.nextmove.client.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.client.adapter.impl.SftpFileSystemAdapter;
import org.gable.blendata.nextmove.client.config.SftpConnectionProperties;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SftpConnectionManager implements ConnectionManager {

    private final SftpConnectionProperties properties;
    private static final SshClient client;

    // Static initializer to set up the SshClient once
    static {
        client = SshClient.setUpDefaultClient();
        client.start();
        // Optional: Register a shutdown hook to stop the client when the JVM exits
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                client.stop();
                log.info("SshClient stopped during shutdown");
            } catch (Exception e) {
                log.error("Error stopping SshClient", e);
            }
        }));
    }

    public SftpConnectionManager(SftpConnectionProperties properties) {
        this.properties = properties;

    }

    @Override
    public FileSystemAdapter createConnection() throws IOException {
        ClientSession session = client.connect(properties.getUsername(),
                        properties.getHost(),
                        properties.getPort())
                .verify(properties.getConnectionTimeout())
                .getSession();
        if (properties.getPrivateKeyPath() != null) {
            Path keyPath = Paths.get(properties.getPrivateKeyPath());
            FileKeyPairProvider provider = new FileKeyPairProvider(keyPath);
            Iterable<KeyPair> keys = provider.loadKeys(null);

            session.addPublicKeyIdentity(keys.iterator().next());
            session.auth().verify(10, TimeUnit.SECONDS);
        } else {
            session.addPasswordIdentity(properties.getPassword());
            session.auth().verify(10, TimeUnit.SECONDS);
        }

        SftpClient sftpClient = SftpClientFactory.instance().createSftpClient(session);
        return new SftpFileSystemAdapter(sftpClient);
    }

    @Override
    public void closeConnection(FileSystemAdapter adapter) throws IOException {
        if (adapter != null) {
            adapter.close();
        }
    }
}