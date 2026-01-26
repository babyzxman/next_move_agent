package org.gable.blendata.nextmove.service.adapter;


import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.gable.blendata.nextmove.shared.dto.FileInfoDTO;
import org.gable.blendata.nextmove.shared.dto.TransferRequestWrapper;

import java.io.IOException;

public interface FileSystemAdapter {
    String getFilePath(String filePath);
    void close() throws IOException;

    FileInfoDTO getSourceFileInfo(String filePath, String sourceRootPathStr);

    long getFileSize(FileSystem fs, String filePath) throws IOException;

    boolean exists(FileSystem fs, String filePath) throws IOException;

    void copy(FileSystem destFileSystem, TransferRequestWrapper request) throws IOException;

    void delete(FileSystem fs, String relativeFilePath) throws IOException;

    Long getModifiedTime(FileSystem fs, String filePath) throws IOException;

    void copyToLocal(String srcFilePath, String destFilePath) throws IOException;

    String resolvePath(FileSystem fs, String filePath) throws IOException;

    FileSystem getSourceFileSystem();

    FileSystem getDestFileSystem();

    void copyFromLocalFile(boolean b, boolean overwrite, Path path, Path destFilePathProcessing) throws IOException;
}
