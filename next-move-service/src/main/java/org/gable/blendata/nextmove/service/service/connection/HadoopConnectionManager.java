package org.gable.blendata.nextmove.service.service.connection;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FileSystem;
import org.gable.blendata.nextmove.service.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.service.adapter.factory.FileSystemAdapterFactory;

import java.io.IOException;

@Slf4j
public class HadoopConnectionManager implements ConnectionManager {

    private final FileSystem sourceFileSystem;
    private final FileSystem destFileSystem;
    private final FileSystemAdapterFactory adapterFactory;

    public HadoopConnectionManager(FileSystem sourceFileSystem, FileSystem destFileSystem, FileSystemAdapterFactory adapterFactory) {
        this.adapterFactory = adapterFactory;
        this.sourceFileSystem = sourceFileSystem;
        this.destFileSystem = destFileSystem;
    }

    @Override
    public FileSystemAdapter createConnection() throws IOException {
        log.debug("Creating Hadoop adapter using existing FileSystem bean");
        return adapterFactory.createHadoopAdapter(sourceFileSystem, destFileSystem);
    }

    @Override
    public void closeConnection(FileSystemAdapter adapter) throws IOException {
        log.debug("Hadoop adapter cleanup completed");
    }


    @Override
    public FileSystemAdapter ensureConnectionAlive(
            FileSystemAdapter fileSystemAdapter, FileSystem fs, String taskKey, String sourceRootPath) {
        log.debug("Hadoop adapter connection is always alive");
        return fileSystemAdapter;
    }
}
