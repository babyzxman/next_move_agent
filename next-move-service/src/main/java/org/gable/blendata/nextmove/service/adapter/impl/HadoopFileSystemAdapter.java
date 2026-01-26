package org.gable.blendata.nextmove.service.adapter.impl;

import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.gable.blendata.nextmove.service.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.shared.constant.TaskConst;
import org.gable.blendata.nextmove.shared.dto.FileInfoDTO;
import org.gable.blendata.nextmove.shared.dto.TransferRequestWrapper;
import org.gable.blendata.nextmove.shared.util.FileInfoUtil;

import java.io.IOException;

@Slf4j
public class HadoopFileSystemAdapter implements FileSystemAdapter {
    private final FileSystem sourceFileSystem;
    private final FileSystem destFileSystem;

    public HadoopFileSystemAdapter(FileSystem sourceFileSystem, FileSystem destFileSystem) {
        this.sourceFileSystem = sourceFileSystem;
        this.destFileSystem = destFileSystem;
    }


    @Override
    public String getFilePath(String pathRef) {
        return new Path(pathRef).toString();
    }

    @Override
    public void close() throws IOException {
        if (sourceFileSystem != null) {
            sourceFileSystem.close();
        }
        if (destFileSystem != null) {
            destFileSystem.close();
        }
    }

    @Override
    public FileInfoDTO getSourceFileInfo(String filePath, String sourceRootPathStr) {
        return FileInfoUtil.getFileInfo(this.sourceFileSystem, filePath, sourceRootPathStr);
    }

    @Override
    public long getFileSize(FileSystem fs, String filePath) throws IOException {
        return fs.getFileStatus(new Path(filePath)).getLen();
    }

    @Override
    public boolean exists(FileSystem fs, String filePath) throws IOException {
        return fs.exists(new Path(filePath));
    }

    @Override
    public void copy(FileSystem destFileSystem, String srcFilePath, String destFilePath, TransferRequestWrapper request) throws IOException {
        boolean isDeleteSrc = TaskConst.MoveType.MOVE.name().equalsIgnoreCase(request.getMoveType());
        FileUtil.copy(this.sourceFileSystem, new Path(srcFilePath), destFileSystem, new Path(destFilePath), isDeleteSrc, request.isOverwrite(), sourceFileSystem.getConf());
    }

    @Override
    public void delete(FileSystem fs, String relativeFilePath) throws IOException {
        fs.delete(new Path(relativeFilePath), true);
    }

    @Override
    public Long getModifiedTime(FileSystem fs, String filePath) throws IOException {
        return fs.getFileStatus(new Path(filePath)).getModificationTime();
    }

    @Override
    public void copyToLocal(String srcFilePath, String destFilePath) throws IOException {
        sourceFileSystem.copyToLocalFile(new Path(srcFilePath), new Path(destFilePath));
    }

    @Override
    public String resolvePath(FileSystem fs, String filePath) throws IOException {
        return fs.resolvePath(new Path(filePath)).toString();
    }

    @Override
    public FileSystem getSourceFileSystem() {
        return this.sourceFileSystem;
    }

    @Override
    public FileSystem getDestFileSystem() {
        return this.destFileSystem;
    }

    @Override
    public void copyFromLocalFile(boolean b, boolean overwrite, Path path, Path destFilePathProcessing) throws IOException {
        this.destFileSystem.copyFromLocalFile(b, overwrite, path, destFilePathProcessing);
    }
}
