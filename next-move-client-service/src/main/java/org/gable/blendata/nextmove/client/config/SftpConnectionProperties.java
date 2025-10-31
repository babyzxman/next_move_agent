package org.gable.blendata.nextmove.client.config;

import lombok.Data;
import org.gable.blendata.nextmove.shared.constant.TaskConst.SourceProperties;

import java.util.Map;

@Data
public class SftpConnectionProperties {
    private String host;
    private int port = 22;
    private String username;
    private String password;
    private String privateKeyPath;
    private String knownHostsPath;
    private int connectionTimeout = 30000;
    private int readTimeout = 30000;

    public static SftpConnectionProperties fromMap(Map<String, Object> properties) {
        SftpConnectionProperties props = new SftpConnectionProperties();
        props.host = (String) properties.get(SourceProperties.HOST.getPropertyName());
        props.port = properties.containsKey(SourceProperties.PORT.getPropertyName()) ? (Integer) properties.get("port") : 22;
        props.username = (String) properties.get(SourceProperties.USERNAME.getPropertyName());
        props.password = (String) properties.get(SourceProperties.PASSWORD.getPropertyName());
        props.privateKeyPath = (String) properties.get(SourceProperties.PRIVATE_KEY_PATH.getPropertyName());
        props.connectionTimeout = properties.containsKey(SourceProperties.CONNECTION_TIMEOUT.getPropertyName()) ?
                (Integer) properties.get(SourceProperties.CONNECTION_TIMEOUT.getPropertyName()) : 30000;
        return props;
    }
}
