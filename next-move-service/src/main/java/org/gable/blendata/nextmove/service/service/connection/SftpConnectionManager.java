package org.gable.blendata.nextmove.service.service.connection;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FileSystem;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
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
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SftpConnectionManager implements ConnectionManager {

    private final SftpConnectionProperties properties;
    private final FileSystem destFileSystem;
    private final FileSystemAdapterFactory adapterFactory;
    private static final SshClient client;

    // Static initializer to set up the SshClient once
    static {
        client = SshClient.setUpDefaultClient();
        CoreModuleProperties.WINDOW_SIZE.set(client, (long) (8 * 1024 * 1024)); // 4 MB
        // Increase the packet size for data channels.
        CoreModuleProperties.MAX_PACKET_SIZE.set(client,(long)  (2 * 1024 * 1024)); // 256 KB
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


    public SftpConnectionManager(SftpConnectionProperties properties, FileSystem destFileSystem, FileSystemAdapterFactory adapterFactory) {
        this.adapterFactory = adapterFactory;
        this.destFileSystem = destFileSystem;
        this.properties = properties;

    }

    @Override
    public FileSystemAdapter createConnection() throws IOException {
        CoreModuleProperties.HEARTBEAT_INTERVAL.set(client, Duration.ofSeconds(15));
        CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX.set(client, 3);
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
        return adapterFactory.createSftpAdapter(sftpClient, destFileSystem);
    }

    public FileSystemAdapter ensureConnectionAlive(FileSystemAdapter currentAdapter, FileSystem fs, String taskKey) throws IOException {
        if (currentAdapter == null) {
            log.warn("{} Connection is null, creating new connection for task {}", AppConst.PREFIX_LOG, taskKey);
            return createConnection();
        }

        try {
            if (!isConnectionAlive(currentAdapter, fs)) {
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

    private boolean isConnectionAlive(FileSystemAdapter adapter, FileSystem fs) {
        try {
            adapter.exists(fs, "/"); // หรือ path ที่เหมาะสม
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