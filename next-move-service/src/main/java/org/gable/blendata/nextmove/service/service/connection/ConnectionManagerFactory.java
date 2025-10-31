package org.gable.blendata.nextmove.service.service.connection;

import lombok.RequiredArgsConstructor;
import org.apache.hadoop.fs.FileSystem;
import org.gable.blendata.nextmove.service.adapter.factory.FileSystemAdapterFactory;
import org.gable.blendata.nextmove.service.config.SftpConnectionProperties;
import org.gable.blendata.nextmove.shared.constant.TaskConst.SourceType;
import org.gable.blendata.nextmove.shared.util.FileSystemUtil;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ConnectionManagerFactory {

    private final Map<String, FileSystem> fileSystems;
    private final FileSystemAdapterFactory adapterFactory;

    public ConnectionManager createConnectionManager(SourceType sourceType
            , String sourceRootPath
            , String destinationRootPath
            ,  Map<String, Object> sourceProperties) {
        FileSystem destFileSystem = fileSystems.get(FileSystemUtil.getFileSystemKey(destinationRootPath));
        switch (sourceType) {
            case SFTP:
                SftpConnectionProperties sftpProps = SftpConnectionProperties.fromMap(sourceProperties);
                return new SftpConnectionManager(sftpProps, destFileSystem, adapterFactory);
            case HADOOP:
                FileSystem sourceFileSystem = fileSystems.get(FileSystemUtil.getFileSystemKey(sourceRootPath));
                return new HadoopConnectionManager(sourceFileSystem, destFileSystem, adapterFactory);
            default:
                throw new IllegalArgumentException("Unsupported source type: " + sourceType);
        }
    }
}
