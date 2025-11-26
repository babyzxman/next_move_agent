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
import java.io.File;
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
    private final String sftpTempDir;

    public SftpFileSystemAdapter(SftpClient sftpClient, FileSystem destFileSystem, String sftpTempDir) {
        this.sftpClient = sftpClient;
        this.destFileSystem = destFileSystem;
        this.sftpTempDir = sftpTempDir;
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

    /**
     * Copies a file from SFTP to a destination file system.
     * <p>
     * This implementation is optimized for large files. It first downloads the file from SFTP to a temporary local file,
     * and then uploads the local file to the destination file system. This approach is significantly faster than streaming
     * the file directly from SFTP to the destination.
     * <p>
     * **NOTE:** This method requires sufficient free space on the local disk to store the temporary file.
     *
     * @param destFileSystem The destination file system.
     * @param srcFilePath    The path to the source file on the SFTP server.
     * @param destFilePath   The path to the destination file on the destination file system.
     * @param isDeleteSrc    If true, the source file will be deleted from the SFTP server after a successful copy.
     * @param overwrite      If true, the destination file will be overwritten if it already exists.
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public void copy(FileSystem destFileSystem, String srcFilePath, String destFilePath, boolean isDeleteSrc, boolean overwrite) throws IOException {
        File tmp = null;
        try {
            // check disk space before download
            long fileSize = sftpClient.stat(srcFilePath).getSize();
            File tempDir = new File(sftpTempDir);
            long usableSpace = tempDir.getUsableSpace();
            if (usableSpace < fileSize) {
                throw new IOException("Not enough disk space to download the file. Required: " + fileSize + ", Available: " + usableSpace);
            }

            // download from sftp to temp file
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }
            Path srcPath = Paths.get(srcFilePath);
            String tmpFileName = "." + srcPath.getFileName().toString();
            tmp = File.createTempFile(tmpFileName, "", tempDir);
            log.info("Start download from {} to {}", srcFilePath, tmp.getAbsolutePath());
            copyToLocal(srcFilePath, tmp.getAbsolutePath());
            log.info("Done download from {} to {}", srcFilePath, tmp.getAbsolutePath());

            // upload from temp file to s3
            log.info("Start upload from {} to {}", tmp.getAbsolutePath(), destFilePath);
            destFileSystem.copyFromLocalFile(true, overwrite, new org.apache.hadoop.fs.Path(tmp.toURI()), new org.apache.hadoop.fs.Path(destFilePath));
            log.info("Done upload from {} to {}", tmp.getAbsolutePath(), destFilePath);

            if (isDeleteSrc) {
                this.delete(null, srcFilePath);
            }
        } catch (Exception e) {
            throw new IOException("Error copying from SFTP to Hadoop", e);
        } finally {
            // Close resources in reverse order
            if (tmp != null) {
                tmp.delete();
            }
        }
    }

    @Override
    public void delete(FileSystem fs, String relativeFilePath) throws IOException {
        sftpClient.remove(relativeFilePath);
    }

    @Override
    public Long getModifiedTime(FileSystem fs, String srcFilePath) throws IOException {
        if(fs == null) {
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

        try (SftpClient.CloseableHandle handle = sftpClient.open(srcFilePath, SftpClient.OpenMode.Read);
             OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(destPath))) {

            byte[] buffer = new byte[8 * 1024 * 1024]; // 8MB buffer
            long offset = 0;
            int bytesRead;

            while ((bytesRead = sftpClient.read(handle, offset, buffer, 0, buffer.length)) > 0) {
                outputStream.write(buffer, 0, bytesRead);
                offset += bytesRead;
            }
        }
    }

}