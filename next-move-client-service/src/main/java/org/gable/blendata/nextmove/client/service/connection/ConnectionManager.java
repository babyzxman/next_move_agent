package org.gable.blendata.nextmove.client.service.connection;

import org.gable.blendata.nextmove.client.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.client.config.SftpConnectionProperties;

import java.io.IOException;

public interface ConnectionManager {
    FileSystemAdapter createConnection() throws IOException;
    void closeConnection(FileSystemAdapter adapter) throws IOException;
}
