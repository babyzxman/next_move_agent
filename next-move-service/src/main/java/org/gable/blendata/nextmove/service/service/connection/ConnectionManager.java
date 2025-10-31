package org.gable.blendata.nextmove.service.service.connection;


import org.apache.hadoop.fs.FileSystem;
import org.gable.blendata.nextmove.service.adapter.FileSystemAdapter;

import java.io.IOException;

public interface ConnectionManager {
    FileSystemAdapter createConnection() throws IOException;

    void closeConnection(FileSystemAdapter adapter) throws IOException;

    FileSystemAdapter ensureConnectionAlive(FileSystemAdapter fileSystemAdapter, FileSystem fs, String taskKey) throws IOException;
}
