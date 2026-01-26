package org.gable.blendata.nextmove.client.adapter;


import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;

public interface FileSystemAdapter {
    List<FileInfo> listFiles(
            String rootPath, FileListingCriteria criteria, Boolean usedCheckpoint,
            Timestamp checkpointTime, Integer filePartitionDate) throws IOException;
    boolean exists(String filePath) throws IOException;
    FileInfo getFileInfo(String filePath) throws IOException;
    void close() throws IOException;
}
