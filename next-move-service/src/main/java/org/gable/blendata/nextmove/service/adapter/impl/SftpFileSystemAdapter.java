package org.gable.blendata.nextmove.service.adapter.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClient.OpenMode;
import org.gable.blendata.nextmove.service.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.shared.dto.FileInfoDTO;
import org.gable.blendata.nextmove.shared.util.FileInfoUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;

/**
 * Adapter for SFTP file system operations.
 * Source file system is null
 */
@Slf4j
public class SftpFileSystemAdapter implements FileSystemAdapter {

    private static final int COPY_BUFFER_SIZE = 8 * 1024 * 1024; // 8 MiB blocks to reduce round trips
    private static final int LOCAL_COPY_BUFFER_SIZE = 512 * 1024;

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
        org.apache.hadoop.fs.Path destPath = new org.apache.hadoop.fs.Path(destFilePath);

        // Check if source file exists
        SftpClient.Attributes srcAttrs = sftpClient.stat(srcFilePath);
        if (srcAttrs == null) {
            throw new IOException("Source file not found: " + srcFilePath);
        }

        // Check if destination exists and handle overwrite
        if (destFileSystem.exists(destPath)) {
            if (!overwrite) {
                throw new IOException("Destination file already exists: " + destFilePath);
            }
            destFileSystem.delete(destPath, false);
        }

        try (java.io.InputStream sftpStream = sftpClient.read(srcFilePath, COPY_BUFFER_SIZE, EnumSet.of(OpenMode.Read));
             FSDataOutputStream hdfsOutputStream = destFileSystem.create(destPath, true)) {

            Configuration configuration = destFileSystem.getConf();
            log.debug("Using Hadoop configuration {} for destination {}", configuration, destFilePath);

            // Stream copy with shared buffer to minimize JNI transitions
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            IOUtils.copyLarge(sftpStream, hdfsOutputStream, buffer);

            hdfsOutputStream.hflush();
        } catch (Exception e) {
            throw new IOException("Error copying from SFTP to Hadoop", e);
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

        try (SftpClient.CloseableHandle handle = sftpClient.open(srcFilePath, OpenMode.Read);
             java.io.OutputStream outputStream = Files.newOutputStream(destPath)) {

            byte[] buffer = new byte[LOCAL_COPY_BUFFER_SIZE];
            long offset = 0;
            int bytesRead;

            while ((bytesRead = sftpClient.read(handle, offset, buffer, 0, buffer.length)) > 0) {
                outputStream.write(buffer, 0, bytesRead);
                offset += bytesRead;
            }
        }
    }

}