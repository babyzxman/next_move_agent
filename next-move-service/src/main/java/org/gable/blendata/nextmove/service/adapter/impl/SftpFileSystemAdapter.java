package org.gable.blendata.nextmove.service.adapter.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClient;
import org.gable.blendata.nextmove.service.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.shared.dto.FileInfoDTO;
import org.gable.blendata.nextmove.shared.util.FileInfoUtil;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Adapter for SFTP file system operations.
 * Source file system is null
 */
@Slf4j
public class SftpFileSystemAdapter implements FileSystemAdapter {

    private final SftpClient sftpClient;
    private final ClientSession session;
    private final FileSystem destFileSystem;

    public SftpFileSystemAdapter(SftpClient sftpClient, ClientSession session, FileSystem destFileSystem) {
        this.sftpClient = sftpClient;
        this.session = session;
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
        if (session != null) {
            session.close();
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
        FSDataOutputStream hdfsOutputStream = null;
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

            // Open SFTP file for reading (streaming)
            log.info("hadoop file = {}",destFileSystem.getConf());
            Configuration c = destFileSystem.getConf();
            log.info("fast.upload=" + c.get("fs.s3a.fast.upload"));
            log.info("fast.upload.buffer=" + c.get("fs.s3a.fast.upload.buffer"));
            log.info("active.blocks=" + c.get("fs.s3a.fast.upload.active.blocks"));
            log.info("multipart.size=" + c.get("fs.s3a.multipart.size"));
            log.info("conn.max=" + c.get("fs.s3a.connection.maximum"));
            log.info("threads.max=" + c.get("fs.s3a.threads.max"));
            log.info("current hadoop file = {}",this.destFileSystem.getConf());
            Configuration cn = this.destFileSystem.getConf();
            log.info("fast.upload=" + cn.get("fs.s3a.fast.upload"));
            log.info("fast.upload.buffer=" + cn.get("fs.s3a.fast.upload.buffer"));
            log.info("active.blocks=" + cn.get("fs.s3a.fast.upload.active.blocks"));
            log.info("multipart.size=" + cn.get("fs.s3a.multipart.size"));
            log.info("conn.max=" + cn.get("fs.s3a.connection.maximum"));
            log.info("threads.max=" + cn.get("fs.s3a.threads.max"));
            log.info("current hadoop file = {}",this.destFileSystem.getConf());
            // Create output stream to Hadoop
            hdfsOutputStream = this.destFileSystem.create(destPath, overwrite);

            // Stream copy with buffer
            try (InputStream sftpInputStream = sftpClient.read(srcFilePath)) {

                byte[] buffer = new byte[64 * 1024]; // 256KB buffer
                int bytesRead;
                while ((bytesRead = sftpInputStream.read(buffer)) != -1) {
                    hdfsOutputStream.write(buffer, 0, bytesRead);
                }
            }

            // Flush and sync
            hdfsOutputStream.close();


        } catch (Exception e) {
            throw new IOException("Error copying from SFTP to Hadoop", e);
        } finally {
            // Close resources in reverse order
            if (hdfsOutputStream != null) {
                try { hdfsOutputStream.close(); } catch (IOException e) { /* ignore */ }
            }
        }
    }

    public void copyWithTempDownload(String srcFilePath, String destFilePath, boolean overwrite, String tempDir) throws IOException {
        Objects.requireNonNull(tempDir, "Temp directory must be configured for SFTP copy.");
        Path tempDirPath = Paths.get(tempDir);
        Files.createDirectories(tempDirPath);

        SftpClient.Attributes srcAttrs = sftpClient.stat(srcFilePath);
        if (srcAttrs == null) {
            throw new IOException("Source file not found: " + srcFilePath);
        }

        org.apache.hadoop.fs.Path destPath = new org.apache.hadoop.fs.Path(destFilePath);
        if (destFileSystem.exists(destPath)) {
            if (!overwrite) {
                throw new IOException("Destination file already exists: " + destFilePath);
            }
            destFileSystem.delete(destPath, false);
        }

        Path tempFilePath = Files.createTempFile(tempDirPath, "sftp-copy-", "-" + destPath.getName());
        try {
            copyWithBuffer(sftpClient, srcFilePath, tempFilePath.toString());
            destFileSystem.copyFromLocalFile(false, overwrite, new org.apache.hadoop.fs.Path(tempFilePath.toUri()), destPath);
        } finally {
            Files.deleteIfExists(tempFilePath);
        }
    }

    @Override
    public void delete(FileSystem fs, String relativeFilePath) throws IOException {
        //...not implemented
        log.debug("SFTP delete operation is not implemented.");
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

        try (InputStream sftpInputStream = sftpClient.read(srcFilePath);
             OutputStream localOutputStream = new BufferedOutputStream(Files.newOutputStream(destPath))) {

            byte[] buffer = new byte[64 * 1024]; // 256KB buffer
            int bytesRead;
            while ((bytesRead = sftpInputStream.read(buffer)) != -1) {
                localOutputStream.write(buffer, 0, bytesRead);
            }
        }
    }

}
