package org.gable.blendata.nextmove.service.test;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.core.CoreModuleProperties;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSessionImpl;
import org.apache.sshd.server.session.SessionFactory;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ReproduceBug {

    private static final int PORT = 2222;
    private static final String HOST = "localhost";
    private static final String USERNAME = "testuser";
    private static final String PASSWORD = "password";
    private static final int CONCURRENT_THREADS = 400;

    // --- SERVER-SIDE COUNTERS ---
    private static final AtomicInteger serverSuccessCount = new AtomicInteger(0);
    private static final AtomicInteger serverFailureCount = new AtomicInteger(0);

    // --- THE FLAWED CLIENT DESIGN ---
    private static final SshClient sharedClient = SshClient.setUpDefaultClient();

    public static ClientSession createFlawedConnection() throws IOException {
        CoreModuleProperties.HEARTBEAT_INTERVAL.set(sharedClient, Duration.ofSeconds(15));
        CoreModuleProperties.HEARTBEAT_NO_REPLY_MAX.set(sharedClient, 3);

        ClientSession session = sharedClient.connect(USERNAME, HOST, PORT).verify().getSession();
        session.addPasswordIdentity(PASSWORD);
        session.auth().verify();
        return session;
    }

    public static void main(String[] args) throws Exception {
        // --- 1. SETUP AND START THE EMBEDDED SFTP SERVER ---
        SshClient client = SshClient.setUpDefaultClient();
        log.info("default key pair = {}",client.getKeyIdentityProvider());
        SshServer sshd = SshServer.setUpDefaultServer();
        log.info("default key pair = {}",sshd.getKeyPairProvider());
        sshd.setPort(PORT);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(Paths.get("hostkey.ser")));
        sshd.setPasswordAuthenticator((username, password, session) -> true);
        sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));

        // ** THIS IS THE CORRECTED PROOF **
        // We override the SessionFactory to intercept every single connection attempt.
//        sshd.setSessionFactory(new SessionFactory(sshd) {
//            @Override
//            protected ServerSessionImpl doCreateSession(IoSession ioSession) throws Exception {
//                try {
//                    ServerSessionImpl session = super.doCreateSession(ioSession);
//                    // If we get here, the initial handshake was successful.
//                    serverSuccessCount.incrementAndGet();
//                    return session;
//                } catch (Exception e) {
//                    // If an exception is thrown, it means the handshake failed.
//                    // This is where the corrupted handshakes from the client race condition are caught.
//                    serverFailureCount.incrementAndGet();
//                    throw e;
//                }
//            }
//        });
//
//        sshd.start();
//        System.out.println("Embedded SFTP Server started on port " + PORT);
//
//        // --- 2. SETUP AND START THE CLIENT ---
//        sharedClient.start();
//
//        // --- 3. RUN THE CONCURRENT TEST ---
//        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_THREADS);
//        CountDownLatch latch = new CountDownLatch(CONCURRENT_THREADS);
//        AtomicInteger clientSuccessOperations = new AtomicInteger(0);
//        AtomicInteger clientFailedOperations = new AtomicInteger(0);
//
//        System.out.println("Starting " + CONCURRENT_THREADS + " concurrent connection attempts...");
//
//        for (int i = 0; i < CONCURRENT_THREADS; i++) {
//            executor.submit(() -> {
//                try (ClientSession session = createFlawedConnection();
//                     SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
//                    sftp.readDir("/");
//                    clientSuccessOperations.incrementAndGet();
//                } catch (IOException e) {
//                    log.error(e.getMessage(),e);
//                    clientFailedOperations.incrementAndGet();
//                } finally {
//                    latch.countDown();
//                }
//            });
//        }
//
//        latch.await();
//
//        // --- 4. SHUTDOWN AND PRINT RESULTS ---
//        System.out.println("\n--- TEST COMPLETE ---");
//        executor.shutdown();
//        sharedClient.stop();
//        sshd.stop();
//
//        System.out.println("\n--- RESULTS ---");
//        System.out.println("SERVER-SIDE Handshake Successes: " + serverSuccessCount.get());
//        System.out.println("SERVER-SIDE Handshake Failures:  " + serverFailureCount.get());
//        System.out.println("------------------------------------");
//        System.out.println("CLIENT-SIDE Operation Successes:   " + clientSuccessOperations.get());
//        System.out.println("CLIENT-SIDE Operation Failures:    " + clientFailedOperations.get());
//
//        System.out.println("\nCONCLUSION: The server correctly logs a ~50/50 split of success/failure due to the client-side race condition, but the client application sees 100% success because the SSHD library silently recovers from the failures.");
    }
}
