package org.gable.blendata.nextmove.client.service.connection;

import lombok.Getter;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.signature.Signature;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionManagerUtil {

    private static final Set<String> keyAlgorithmSet = new HashSet<>(Arrays.asList(
            "rsa-sha2-512","rsa-sha2-256","ssh-rsa"));

    @Getter
    private static List<NamedFactory<Signature>> DEFAULT_SIGNATURE_FACTORIES;

    static{
        try(SshClient client = SshClient.setUpDefaultClient()) {
            DEFAULT_SIGNATURE_FACTORIES = client.getSignatureFactories();
        }
        catch (Exception ignored) {

        }
    }


    private static ConcurrentHashMap<String,Set<String>> sftpAlgroithmMap = new ConcurrentHashMap<>();

    public static String getServerMapKey(String host, Integer port) {
        return host + port;
    }

    public static void setSftpAlogirthmSet(Set<String> keyAccept, String key) {
        sftpAlgroithmMap.put(key,keyAccept);
    }

    public static Set<String> getSftpAlgorithmMap(String key) {
        return sftpAlgroithmMap.computeIfAbsent(key, k -> {
            Set<String> set = ConcurrentHashMap.newKeySet();
            set.addAll(keyAlgorithmSet);
            return set;
        });
    }

}
