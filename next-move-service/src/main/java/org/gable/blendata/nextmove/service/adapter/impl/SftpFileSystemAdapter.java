package org.gable.blendata.nextmove.service.adapter.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.sshd.sftp.client.SftpClient;
import org.gable.blendata.nextmove.service.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.shared.dto.FileInfoDTO;
import org.gable.blendata.nextmove.shared.util.FileInfoUtil;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Adapter for SFTP file system operations.
 * Source file system is null
 */
@Slf4j
public class SftpFileSystemAdapter implements FileSystemAdapter {

    private final SftpClient sftpClient;
    private final FileSystem destFileSystem;

    public SftpFileSystemAdapter(SftpClient sftpClient, FileSystem destFileSystem) {
        this.sftpClient = sftpClient;
        this.destFileSystem = destFileSystem;
    }


    @Override
    public String getFilePath(String pathRef) {
        return pathRef;
    }

    @Override
    public void close() throws IOException {
        if (sftpClient != null) {
            sftpClient.close();
        }
    }

    @Override
    public FileInfoDTO getSourceFileInfo(String filePath, String sourceRootPathStr) {
        return FileInfoUtil.getFileInfo(sftpClient, filePath, sourceRootPathStr);
    }

    @Override
    public long getFileSize(FileSystem fs, String filePath) throws IOException {
        if(fs == null){
            return sftpClient.stat(filePath).getSize();
        }else{
            return fs.getFileStatus(new org.apache.hadoop.fs.Path(filePath)).getLen();
        }
    }

    @Override
    public boolean exists(FileSystem fs, String filePath) throws IOException {
        if(fs == null){
            return sftpClient.stat(filePath) != null;
        }else{
            return fs.exists(new org.apache.hadoop.fs.Path(filePath));
        }
    }

    @Override
    public void copy(FileSystem destFileSystem, String srcFilePath, String destFilePath, boolean isDeleteSrc, boolean overwrite) throws IOException {
        Path tempFile = null;
        try {
            // Check if source file exists
            SftpClient.Attributes srcAttrs = sftpClient.stat(srcFilePath);
            if (srcAttrs == null) {
                throw new IOException("Source file not found: " + srcFilePath);
            }

            org.apache.hadoop.fs.Path destPath = new org.apache.hadoop.fs.Path(destFilePath);

            // Check if destination exists and handle overwrite
            if (destFileSystem.exists(destPath)) {
                if (!overwrite) {
                    throw new IOException("Destination file already exists: " + destFilePath);
                }
                destFileSystem.delete(destPath, false);
            }

            // Create a temporary file
            tempFile = Files.createTempFile("sftp-", ".tmp");
            String tempFilePath = tempFile.toString();

            // Download from SFTP to temporary file
            copyWithBuffer(sftpClient, srcFilePath, tempFilePath);

            // Upload from temporary file to S3
            this.destFileSystem.copyFromLocalFile(false, overwrite, new org.apache.hadoop.fs.Path(tempFile.toUri()), destPath);

        } catch (Exception e) {
            throw new IOException("Error copying from SFTP to Hadoop", e);
        } finally {
            // Delete the temporary file
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("Failed to delete temporary file: {}", tempFile, e);
                }
            }
        }
    }

    @Override
    public void delete(FileSystem fs, String relativeFilePath) throws IOException {
        //...not implemented
        log.debug("SFTP delete operation is not implemented.");
    }

    @Override
    public Long getModifiedTime(FileSystem fs, String srcFilePath) throws IOException {
        if(fs != null) {
            return sftpClient.stat(srcFilePath).getModifyTime().toMillis();
        }else{
            return fs.getFileStatus(new org.apache.hadoop.fs.Path(srcFilePath)).getModificationTime();
        }
    }

    @Override
    public void copyToLocal(String srcFilePath, String destFilePath) throws IOException {
        copyWithBuffer(sftpClient, srcFilePath, destFilePath);
    }

    @Override
    public String resolvePath(FileSystem fs, String filePath) throws IOException {
        if(fs == null) {
            return filePath;
        }else{
            return fs.resolvePath(new org.apache.hadoop.fs.Path(filePath)).toString();
        }
    }

    @Override
    public FileSystem getDestFileSystem() {
        return this.destFileSystem;
    }

    @Override
    public void copyFromLocalFile(boolean b, boolean overwrite, org.apache.hadoop.fs.Path path, org.apache.hadoop.fs.Path destFilePathProcessing) throws IOException {
        this.destFileSystem.copyFromLocalFile(b, overwrite, path, destFilePathProcessing);
    }

    @Override
    public FileSystem getSourceFileSystem() {
        return null;
    }

    private static void copyWithBuffer(SftpClient sftpClient,
                                       String srcFilePath,
                                       String destFilePath) throws IOException {

        Path destPath = Paths.get(destFilePath);
        Files.createDirectories(destPath.getParent());

        // Use a BufferedOutputStream for efficient local writes
        try (SftpClient.CloseableHandle handle = sftpClient.open(srcFilePath, SftpClient.OpenMode.Read);
             OutputStream fileOutputStream = Files.newOutputStream(destPath);
             BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream, 4 * 1024 * 1024)) { // 4MB write buffer

            // Use a large buffer for SFTP reads to minimize network round-trips
            byte[] buffer = new byte[4 * 1024 * 1024]; // 4MB read buffer
            long offset = 0;
            int bytesRead;

            while ((bytesRead = sftpClient.read(handle, offset, buffer, 0, buffer.length)) > 0) {
                bufferedOutputStream.write(buffer, 0, bytesRead);
                offset += bytesRead;
            }
        }
    }

}