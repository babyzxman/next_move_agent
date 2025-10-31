package org.gable.blendata.nextmove.client.service.connection;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FileSystem;
import org.gable.blendata.nextmove.client.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.client.adapter.factory.FileSystemAdapterFactory;
import org.gable.blendata.nextmove.client.adapter.impl.HadoopFileSystemAdapter;

import java.io.IOException;

@Slf4j
public class HadoopConnectionManager implements ConnectionManager {

    private final FileSystem sourceFileSystem;
    private final FileSystemAdapterFactory adapterFactory;

    public HadoopConnectionManager(FileSystem sourceFileSystem, FileSystemAdapterFactory adapterFactory) {
        this.adapterFactory = adapterFactory;
        this.sourceFileSystem = sourceFileSystem;
    }

    @Override
    public FileSystemAdapter createConnection() throws IOException {
        log.debug("Creating Hadoop adapter using existing FileSystem bean");
        return adapterFactory.createHadoopAdapter(sourceFileSystem);
    }

    @Override
    public void closeConnection(FileSystemAdapter adapter) throws IOException {
        log.debug("Hadoop adapter cleanup completed");
    }
}
