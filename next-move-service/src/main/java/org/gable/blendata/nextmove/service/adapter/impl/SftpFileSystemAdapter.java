package org.gable.blendata.nextmove.service.adapter.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.sshd.sftp.client.SftpClient;
import org.gable.blendata.nextmove.service.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.shared.constant.TaskConst;
import org.gable.blendata.nextmove.shared.dto.FileInfoDTO;
import org.gable.blendata.nextmove.shared.dto.TransferRequestWrapper;
import org.gable.blendata.nextmove.shared.util.FileInfoUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Adapter for SFTP file system operations.
 * Source file system is null
 */
@Slf4j
public class SftpFileSystemAdapter implements FileSystemAdapter {

    private final SftpClient sftpClient;
    private final FileSystem destFileSystem;
    private final String tempDir;

    public SftpFileSystemAdapter(SftpClient sftpClient, FileSystem destFileSystem, String tempDir) {
        this.sftpClient = sftpClient;
        this.destFileSystem = destFileSystem;
        this.tempDir = tempDir;
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
    public void copy(FileSystem destFileSystem, String srcFilePath, String destFilePath, TransferRequestWrapper request) throws IOException {
        if (request.isUseTempFileDownload()) {
            copyViaTempFile(destFileSystem, srcFilePath, destFilePath, request);
        } else {
            copyViaDirectStream(destFileSystem, srcFilePath, destFilePath, request);
        }
    }

    private void copyViaTempFile(FileSystem destFileSystem, String srcFilePath, String destFilePath, TransferRequestWrapper request) throws IOException {
        Path tempFile = null;
        try {
            // 1. Create a temporary local file path
            String tempFileName = UUID.randomUUID().toString();
            tempFile = Paths.get(tempDir, tempFileName);

            log.info("Starting SFTP download from '{}' to temporary file '{}'", srcFilePath, tempFile);

            // 2. Download the file from SFTP to the local temporary file
            copyWithBuffer(sftpClient, srcFilePath, tempFile.toString());

            log.info("SFTP download complete. Starting upload from '{}' to '{}'", tempFile, destFilePath);

            org.apache.hadoop.fs.Path destPath = new org.apache.hadoop.fs.Path(destFilePath);

            // 3. Upload the local file to Hadoop/S3
            destFileSystem.copyFromLocalFile(false, request.isOverwrite(), new org.apache.hadoop.fs.Path(tempFile.toUri()), destPath);

            log.info("Upload to Hadoop complete.");

            // 4. (Optional) Delete the source file if requested
            if (TaskConst.MoveType.MOVE.name().equalsIgnoreCase(request.getMoveType())) {
                sftpClient.remove(srcFilePath);
                log.info("Deleted source SFTP file '{}'", srcFilePath);
            }

        } catch (Exception e) {
            throw new IOException("Error copying from SFTP to Hadoop via temporary file", e);
        } finally {
            // 5. Clean up the temporary file
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    log.info("Successfully deleted temporary file '{}'", tempFile);
                } catch (IOException e) {
                    log.error("Failed to delete temporary file '{}'", tempFile, e);
                }
            }
        }
    }

    private void copyViaDirectStream(FileSystem destFileSystem, String srcFilePath, String destFilePath, TransferRequestWrapper request) throws IOException {
        FSDataOutputStream hdfsOutputStream = null;
        SftpClient.CloseableHandle handle = null;
        try {

            // Check if source file exists
            SftpClient.Attributes srcAttrs = sftpClient.stat(srcFilePath);
            if (srcAttrs == null) {
                throw new IOException("Source file not found: " + srcFilePath);
            }

            org.apache.hadoop.fs.Path destPath = new org.apache.hadoop.fs.Path(destFilePath);

            // Check if destination exists and handle overwrite
            if (destFileSystem.exists(destPath)) {
                if (!request.isOverwrite()) {
                    throw new IOException("Destination file already exists: " + destFilePath);
                }
                destFileSystem.delete(destPath, false);
            }

            // Open SFTP file for reading (streaming)
            handle = sftpClient.open(srcFilePath, SftpClient.OpenMode.Read);

            // Create output stream to Hadoop
            hdfsOutputStream = this.destFileSystem.create(destPath, request.isOverwrite());

            // Stream copy with buffer
            byte[] buffer = new byte[64 * 1024]; // 64 KiB
            int bytesRead;
            long fileOffset = 0;
            while ((bytesRead = sftpClient.read(handle, fileOffset, buffer, 0, buffer.length)) > 0) {
                hdfsOutputStream.write(buffer, 0, bytesRead);
                fileOffset += bytesRead;
            }

        } catch (Exception e) {
            throw new IOException("Error copying from SFTP to Hadoop via direct stream", e);
        } finally {
            // Close resources in reverse order
            if (hdfsOutputStream != null) {
                try { hdfsOutputStream.close(); } catch (IOException e) { /* ignore */ }
            }
            if (handle != null) {
                try { handle.close(); } catch (IOException e) { /* ignore */ }
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
             OutputStream outputStream = Files.newOutputStream(destPath)) {

            byte[] buffer = new byte[1024 * 1024]; // 1MB buffer for faster download
            long offset = 0;
            int bytesRead;

            while ((bytesRead = sftpClient.read(handle, offset, buffer, 0, buffer.length)) > 0) {
                outputStream.write(buffer, 0, bytesRead);
                offset += bytesRead;
            }
        }
    }

}
