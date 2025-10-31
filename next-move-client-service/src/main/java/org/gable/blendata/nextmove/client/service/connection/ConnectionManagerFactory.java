package org.gable.blendata.nextmove.client.service.connection;

import lombok.RequiredArgsConstructor;
import org.apache.hadoop.fs.FileSystem;
import org.gable.blendata.nextmove.client.adapter.factory.FileSystemAdapterFactory;
import org.gable.blendata.nextmove.client.config.SftpConnectionProperties;
import org.gable.blendata.nextmove.client.dto.TaskDTO;
import org.gable.blendata.nextmove.shared.util.FileSystemUtil;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConnectionManagerFactory {

    private final Map<String, FileSystem> fileSystems;
    private final FileSystemAdapterFactory adapterFactory;

    public ConnectionManager createConnectionManager(TaskDTO taskDTO) {
        switch (taskDTO.getSourceType()) {
            case SFTP:
                SftpConnectionProperties sftpProps = SftpConnectionProperties.fromMap(taskDTO.getSourceProperties());
                return new SftpConnectionManager(sftpProps);
            case HADOOP:
                FileSystem sourceFileSystem = fileSystems.get(FileSystemUtil.getFileSystemKey(taskDTO.getRootPath().getSource()));
                return new HadoopConnectionManager(sourceFileSystem, adapterFactory);
            default:
                throw new IllegalArgumentException("Unsupported source type: " + taskDTO.getSourceType());
        }
    }
}
