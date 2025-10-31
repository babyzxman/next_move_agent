package org.gable.blendata.nextmove.client.adapter;


import java.io.IOException;
import java.util.List;

public interface FileSystemAdapter {
    List<FileInfo> listFiles(String rootPath, FileListingCriteria criteria) throws IOException;
    boolean exists(String filePath) throws IOException;
    FileInfo getFileInfo(String filePath) throws IOException;
    void close() throws IOException;
}
