package org.gable.blendata.nextmove.client.adapter.factory;

import org.apache.hadoop.fs.FileSystem;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClient;
import org.gable.blendata.nextmove.client.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.client.adapter.impl.HadoopFileSystemAdapter;
import org.gable.blendata.nextmove.client.adapter.impl.SftpFileSystemAdapter;
import org.springframework.stereotype.Component;

@Component
public class FileSystemAdapterFactory {

    public FileSystemAdapter createHadoopAdapter(FileSystem fs) {
        return new HadoopFileSystemAdapter(fs);
    }

    public FileSystemAdapter createSftpAdapter(SftpClient sftpClient, ClientSession session) {
        return new SftpFileSystemAdapter(sftpClient, session);
    }
}
