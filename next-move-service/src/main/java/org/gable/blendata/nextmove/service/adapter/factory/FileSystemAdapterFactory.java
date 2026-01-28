package org.gable.blendata.nextmove.service.adapter.factory;

import org.apache.hadoop.fs.FileSystem;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClient;
import org.gable.blendata.nextmove.service.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.service.adapter.impl.HadoopFileSystemAdapter;
import org.gable.blendata.nextmove.service.adapter.impl.SftpFileSystemAdapter;
import org.springframework.stereotype.Component;

@Component
public class FileSystemAdapterFactory {

    public FileSystemAdapter createHadoopAdapter(FileSystem sourceFileSystem, FileSystem destFileSystem) {
        return new HadoopFileSystemAdapter(sourceFileSystem, destFileSystem);
    }

    public FileSystemAdapter createSftpAdapter(SftpClient sftpClient, ClientSession session, FileSystem destFileSystem) {
        return new SftpFileSystemAdapter(sftpClient, session, destFileSystem);
    }
}
