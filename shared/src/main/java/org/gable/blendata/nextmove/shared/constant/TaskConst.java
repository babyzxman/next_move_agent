package org.gable.blendata.nextmove.shared.constant;

public class TaskConst {

    public enum TaskType {
        ZERO_SIZE_CONTROL, NONE_CONTROL
    }
    public enum MoveType {
        COPY, MOVE
    }
    public enum SourceType {
        HADOOP, SFTP
    }

    public enum SourceProperties {
        HOST("host"), PORT("port"), USERNAME("username")
        , PASSWORD("password"), PRIVATE_KEY_PATH("privateKeyPath"), CONNECTION_TIMEOUT("connectionTimeout");
        private final String propertyName;
        SourceProperties(String propertyName) {
            this.propertyName = propertyName;
        }
        public String getPropertyName() {
            return propertyName;
        }
    }
}
