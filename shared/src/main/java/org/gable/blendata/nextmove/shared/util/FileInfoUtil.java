package org.gable.blendata.nextmove.shared.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.FileNameUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.sshd.sftp.client.SftpClient;
import org.gable.blendata.nextmove.shared.constant.AppConst;
import org.gable.blendata.nextmove.shared.dto.FileInfoDTO;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class FileInfoUtil {


    public static String extractRelativeSourcePath(String fullPath) {
        if(fullPath.startsWith("\\/")){
            return fullPath;
        }else{
            try {
                URI uri = new URI(fullPath);
                return uri.getPath();
            } catch (URISyntaxException e) {
                log.error("{} !!!Error cannot extract relative source path , full path = {}", AppConst.PREFIX_LOG, fullPath);
                return null;
            }
        }
    }


    public static String getFileExtension(String relativeSourceFilePath) {
        return FileNameUtils.getExtension(relativeSourceFilePath).toLowerCase();
    }

    public static FileStatus getFileStatus(FileSystem fs, Path sourceFilePath) throws IOException {
        return fs.getFileStatus(sourceFilePath);
    }

    public static Long getModifiedTime(FileSystem fs, Path sourceFilePath) throws IOException {
        return fs.getFileStatus(sourceFilePath).getModificationTime();
    }

    public static Long getFileSize(FileSystem fs, Path sourceFilePath) throws IOException {
        return fs.getFileStatus(sourceFilePath).getLen();
    }

    /**
     *
     * @param filePathStr : Ex. viewfs://blendata/src-local-01/main/zero/demo.txt
     */
    public static FileInfoDTO getFileInfo(FileSystem fs, String filePathStr, String srcRootPathStr){
        try {
            //...Ex. /src-local-01/main/zero/demo.txt
            Path sourceFilePath = new Path(FileInfoUtil.extractRelativeSourcePath(filePathStr));
            //...Ex. demo.txt
            String sourceFileName = sourceFilePath.getName();
            long fileSizeInBytes = getFileStatus(fs, sourceFilePath).getLen();
            String fileExtension = getFileExtension(sourceFilePath.toString());
            return FileInfoDTO.builder()
                    .rootPathStr(srcRootPathStr)
                    .relativeFilePath(sourceFilePath.toString())
                    .size(fileSizeInBytes)
                    .fileName(sourceFileName)
                    .extension(fileExtension)
                    .absoluteFilePath(fs.resolvePath(sourceFilePath).toString())
                    .build();
        }catch (Exception e){
            log.error("{} !!!Error get source file info {}", AppConst.PREFIX_LOG, filePathStr, e);
        }
        return null;
    }

    public static FileInfoDTO getFileInfo(SftpClient sftpClient, String filePath, String srcRootPathStr) {
        try {
            //...Ex. demo.txt
            String sourceFileName = FilenameUtils.getName(filePath);
            SftpClient.Attributes stat = sftpClient.stat(filePath);
            long fileSizeInBytes = stat.getSize();
            String fileExtension = getFileExtension(filePath);
            return FileInfoDTO.builder()
                    .rootPathStr(srcRootPathStr)
                    .relativeFilePath(filePath)
                    .size(fileSizeInBytes)
                    .fileName(sourceFileName)
                    .extension(fileExtension)
                    .absoluteFilePath(filePath)
                    .build();
        }catch (Exception e){
            log.error("{} !!!Error get source file info {}", AppConst.PREFIX_LOG, filePath, e);
        }
        return null;
    }
}
