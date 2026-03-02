package org.gable.blendata.nextmove.client.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.hadoop.fs.*;
import org.gable.blendata.nextmove.client.adapter.FileInfo;
import org.gable.blendata.nextmove.client.adapter.FileListingCriteria;
import org.gable.blendata.nextmove.client.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.client.dto.TransferHistoryView;
import org.gable.blendata.nextmove.shared.constant.AppConst;
import org.gable.blendata.nextmove.shared.constant.TaskConst;
import org.gable.blendata.nextmove.shared.entity.PathCheckpoint;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.gable.blendata.nextmove.shared.constant.FileStatus.PROCESSING;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListFileService {

    private final TransferHistoryService transferHistoryService;

    /*private PathFilter generateSrcExtensionPathFilter(String[] srcExtensions){
        if(ArrayUtils.isEmpty(srcExtensions)){
            srcExtensions = new String[]{"*"};
        }
        return new OrPathFilter(Arrays.stream(srcExtensions)
                .map(srcExtension -> {
                    try {
                        return new GlobFilter("*."+srcExtension);
                    } catch (IOException e) {
                        throw new RuntimeException(String.format("!!!Invalid source extension %s", srcExtension));
                    }
                })
                .toArray(PathFilter[]::new));
    }
    private PathFilter generateWildCardPatternPathFilter(String[] wildcardPatterns){
        if(ArrayUtils.isEmpty(wildcardPatterns)){
            wildcardPatterns = new String[]{"*"};
        }
        return new OrPathFilter(Arrays.stream(wildcardPatterns)
                .map(wildcardPattern -> {
                    try {
                        return new GlobFilter(wildcardPattern + ".*");
                    } catch (IOException e) {
                        throw new RuntimeException(String.format("!!!Invalid wildcard pattern file name %s", wildcardPattern));
                    }
                })
                .toArray(PathFilter[]::new));
    }
*/


    public ReturnListFile listNoneCtrlFile(FileSystemAdapter fileSystemAdapter, String rootPath,
                                         Integer filePerRound, LocalDateTime afterDate,
                                         List<String> srcExtensions, List<String> wildcardPatterns,
                                         boolean isOverwrite, TaskConst.MoveType moveType, String taskId,
                                         TaskConst.SourceType sourceType, String host, String destPath,
                                         Integer filePartitionDate, Boolean usedCheckpoint,
                                         Timestamp checkpointTime) throws IOException {
        ReturnListFile returnListFile = new ReturnListFile();
        FileListingCriteria criteria = FileListingCriteria.builder()
                .maxFiles(filePerRound)
                .afterDate(afterDate)
                .extensions(srcExtensions)
                .wildcardPatterns(wildcardPatterns)
                .build();

        List<FileInfo> files = fileSystemAdapter.listFiles(
                rootPath, criteria,usedCheckpoint,checkpointTime,filePartitionDate);

        if (files.isEmpty()) {
            log.warn("[Blendata] !!! Not found match files on path {} of task id {}", rootPath, taskId);
            return returnListFile;
        }
        List<TransferHistoryView> excludeFiles = new ArrayList<>();
        excludeFiles = getExcludeFiles(
                sourceType, rootPath, moveType, host, isOverwrite,
                destPath, filePartitionDate);

        Map<String,TransferHistoryView> transferHistoryViewMap = new HashMap<>();
        for(TransferHistoryView transferHistoryView: excludeFiles) {
            transferHistoryViewMap.put(transferHistoryView.getFilePath(),transferHistoryView);
        }
        long criteriaMaxFiles = criteria.getMaxFiles() != null ? criteria.getMaxFiles() : Long.MAX_VALUE;
        int count = 0;
        List<String> filePath = new ArrayList<>();
        for(FileInfo file: files) {
            if(count >= criteriaMaxFiles) {
                break;
            }
            TransferHistoryView transferHistoryView = transferHistoryViewMap.get(file.getPath());
            if(transferHistoryView != null) {
                if (!transferHistoryView.getStatus().equals(PROCESSING.name())) {
                    if(transferHistoryView.getFileModifiedTime() == null) {
                        filePath.add(file.getPath());
                        count++;
                    }
                    else if(file.getModificationTime().minusSeconds(1).truncatedTo(ChronoUnit.SECONDS).isAfter(
                            transferHistoryView.getFileModifiedTime().toLocalDateTime().truncatedTo(ChronoUnit.SECONDS))) {
                        FileInfo newFile = fileSystemAdapter.getFileInfo(file.getPath());
                        if(newFile.getModificationTime().truncatedTo(ChronoUnit.SECONDS).
                                isAfter(transferHistoryView.getFileModifiedTime().toLocalDateTime().truncatedTo(ChronoUnit.SECONDS))) {
                            filePath.add(file.getPath());
                            count++;
                        }
                    }
                }
            }
            else {
                filePath.add(file.getPath());
            }
        }
        returnListFile.setFileList(filePath);
        if(criteria.getCheckpointTime() != null)
            returnListFile.setLatestModifiedTime(Timestamp.from(criteria.getCheckpointTime().toInstant()));
        return returnListFile;
    }

    private List<TransferHistoryView> getExcludeFiles(TaskConst.SourceType sourceType, String rootPath,
                                                      TaskConst.MoveType moveType, String host, boolean isOverwrite,
                                                      String destPath,Integer filePartitionDate) {
//        if (moveType.equals(TaskConst.MoveType.MOVE) && isOverwrite) {
        if (isOverwrite) {
            return transferHistoryService.getProcessingFiles(sourceType, rootPath, host,destPath,filePartitionDate);
        } else {
            return transferHistoryService.getSuccessOrProcessingFiles(sourceType, rootPath, host,destPath,filePartitionDate);
        }
    }

/*

    public List<Path> listNoneCtrlFile(Path rootPath, Integer filePerRound, LocalDateTime afterDate, List<String> srcExtensions, List<String> wildcardPatterns, boolean isOverwrite, TaskConst.MoveType moveType, String taskId) throws IOException {
        List<Path> sourceFilePaths = new ArrayList<>();
//        RemoteIterator<LocatedFileStatus> fileStatusListIterator = fs.listFiles(rootPath, true);
        FileStatus[] fileStatusList = fs.globStatus(new Path(rootPath.toString()+"/*.*")
                , new AndPathFilter(generateSrcExtensionPathFilter(srcExtensions.toArray(new String[0]))
                        , generateWildCardPatternPathFilter(wildcardPatterns.toArray(new String[0])))
        );
        if(ArrayUtils.isEmpty(fileStatusList)) {
            log.warn("[Blendata] !!! Not found match files on path {} of task id {}", rootPath.toString(), taskId);
            return Collections.EMPTY_LIST;
        }
        log.debug("[Query Transfer History][NONE][Start] {}", DateUtil.convertToString(DateUtil.getCurrentDateWithTime(), DateUtil.DD_sl_MM_sl_YYYY_HH_mm_ss));
        Set<String> allSuccessOrProcessingFiles = new HashSet<>();
        if(moveType.equals(TaskConst.MoveType.MOVE) && isOverwrite) {
            allSuccessOrProcessingFiles = transferHistoryService.getProcessingFiles(rootPath.toString());
        }else{
            allSuccessOrProcessingFiles = transferHistoryService.getSuccessOrProcessingFiles(rootPath.toString());
        }
        log.debug("[Query Transfer History][NONE][End] {}", DateUtil.convertToString(DateUtil.getCurrentDateWithTime(), DateUtil.DD_sl_MM_sl_YYYY_HH_mm_ss));
        int countFile = 0;
//        while (fileStatusListIterator.hasNext()) {
        for (FileStatus fileStatus : fileStatusList) {
            if (null != filePerRound && countFile >= filePerRound) {
                break;
            }
//            LocatedFileStatus fileStatus = fileStatusListIterator.next();
            if (allSuccessOrProcessingFiles.contains(fileStatus.getPath().toString())
//                    || (!CollectionUtils.isEmpty(srcExtensions) && !srcExtensions.contains(FileNameExtUtil.getExtension(fileStatus.getPath().toString())))
                    || (null != afterDate && afterDate.isAfter(DateUtil.convertToLocalDateTime(fileStatus.getModificationTime())))
//                    || (!isMatchWildcardPatterns(wildcardPatterns, FilenameUtils.getBaseName(fileStatus.getPath().toString())))
            ) {
                continue;
            }
            sourceFilePaths.add(fileStatus.getPath());
            countFile++;
        }
        return sourceFilePaths;
    }
*/

    public String getControlFilePath(FileInfo file, List<String> ctrlExtensions, String controlPath,FileSystemAdapter adapter) throws IOException {
        if (controlPath != null) {
            String controlAbsolutePath = controlPath.endsWith("/") ? controlPath + file.getName() :
                    controlPath + File.separator + file.getName();
            return hasControlFile(adapter,controlAbsolutePath,ctrlExtensions);
        }
        else {
            return hasControlFile(adapter,file.getPath(),ctrlExtensions);
        }
    }

    public ReturnListFile listZeroSizeCtrlFile(FileSystemAdapter fileSystemAdapter, String rootPath,
                                             Integer filePerRound, LocalDateTime afterDate,
                                             List<String> ctrlExtensions, List<String> srcExtensions,
                                             List<String> wildcardPatterns, boolean isOverwrite,
                                             TaskConst.MoveType moveType, String taskId,
                                             TaskConst.SourceType sourceType, String host,
                                             String controlPath, List<String> controlFileNamePattern,
                                             String destPath, Integer filePartitionDate,Boolean usedCheckpoint,
                                             Timestamp checkpointTime) throws IOException {
        ReturnListFile returnListFile = new ReturnListFile();
        long t0 = System.currentTimeMillis();
        FileListingCriteria criteria =  FileListingCriteria.builder()
                .maxFiles(filePerRound)
                .afterDate(afterDate)
                .extensions(srcExtensions)
                .wildcardPatterns(wildcardPatterns)
                .ctrlExtensions(ctrlExtensions)
                .build();

        List<FileInfo> files = fileSystemAdapter.listFiles(
                rootPath, criteria,usedCheckpoint,checkpointTime,filePartitionDate);
        log.info("list file took {} ms", System.currentTimeMillis()-t0);
        if (files.isEmpty()) {
            log.warn("[Blendata] !!! Not found match files on path {} of task id {}", rootPath, taskId);
            return returnListFile;
        }
        t0 = System.currentTimeMillis();
        List<TransferHistoryView> excludeFiles = getExcludeFiles(
                sourceType, rootPath, moveType, host, isOverwrite,destPath,
                filePartitionDate);
        log.info("get exclude files take {} ms", System.currentTimeMillis()-t0);
        Map<String,TransferHistoryView> transferHistoryViewMap = new HashMap<>();
        for(TransferHistoryView transferHistoryView: excludeFiles) {
            transferHistoryViewMap.put(transferHistoryView.getFilePath(),transferHistoryView);
        }
        List<String> result = new ArrayList<>();

        for (FileInfo file : files) {
            TransferHistoryView transferHistoryView = transferHistoryViewMap.get(file.getPath());
            boolean isControlFileFailedToCopy = false;
            if (result.size() >= (criteria.getMaxFiles() != null ? criteria.getMaxFiles() : Integer.MAX_VALUE)) {
                break;
            }
            if (transferHistoryView != null && transferHistoryView.getFileModifiedTime() != null) {
                if(!file.getModificationTime().minusSeconds(1).truncatedTo(ChronoUnit.SECONDS).
                        isAfter(transferHistoryView.getFileModifiedTime().toLocalDateTime().truncatedTo(ChronoUnit.SECONDS))) {
                    if(controlFileNamePattern == null) {
                        if(transferHistoryViewMap.get(getControlFilePath(file,ctrlExtensions,
                                controlPath,fileSystemAdapter)) == null) {
                            isControlFileFailedToCopy = true;
                        }
                        else {
                            continue;
                        }
                    }
                    else {
                        log.info("check inside continue");
                        continue;
                    }
                }
                else {
                    log.info("has new file when list new time = {}",file.getModificationTime());
                    FileInfo newFile = fileSystemAdapter.getFileInfo(file.getPath());
                    log.info("new file = {}",newFile.getModificationTime());
                    if(!newFile.getModificationTime().truncatedTo(ChronoUnit.SECONDS).
                            isAfter(transferHistoryView.getFileModifiedTime().toLocalDateTime().
                                    truncatedTo(ChronoUnit.SECONDS))) {
                        if(controlFileNamePattern == null) {
                            if(transferHistoryViewMap.get(getControlFilePath(file,ctrlExtensions,
                                    controlPath,fileSystemAdapter)) == null) {
                                isControlFileFailedToCopy = true;
                            }
                            else {
                                continue;
                            }
                        }
                        else {
                            log.info("check inside continue");
                            continue;
                        }
                    }
                }
            }

            String extension = FilenameUtils.getExtension(file.getName());
            if(controlFileNamePattern != null) {
                result.add(file.getPath());
            }
            else {
                if (!ctrlExtensions.contains(extension)) {
                    if (controlPath != null) {
                        String controlAbsolutePath = controlPath.endsWith("/") ? controlPath + file.getName() :
                                controlPath + File.separator + file.getName();
                        String controlFileName = hasControlFile(fileSystemAdapter, controlAbsolutePath, ctrlExtensions);
                        if (controlFileName != null) {
                            if(!isControlFileFailedToCopy)
                                result.add(file.getPath());
                            result.add(controlFileName);
                        }
                    } else {
                        String controlFileName = hasControlFile(fileSystemAdapter, file.getPath(), ctrlExtensions);
                        if (controlFileName != null) {
                            if(!isControlFileFailedToCopy)
                                result.add(file.getPath());
                            result.add(controlFileName);
                        }
                    }
                }
            }
        }

        if(controlFileNamePattern != null) {
            FileListingCriteria controlCriteria =  FileListingCriteria.builder()
                    .maxFiles(filePerRound)
                    .afterDate(afterDate)
                    .extensions(ctrlExtensions)
                    .wildcardPatterns(controlFileNamePattern)
                    .ctrlExtensions(ctrlExtensions)
                    .build();
            List<FileInfo> controlFiles;
            if(controlPath != null) {
                controlFiles = fileSystemAdapter.listFiles(controlPath, controlCriteria,usedCheckpoint,checkpointTime,filePartitionDate);
            }
            else {
                controlFiles = fileSystemAdapter.listFiles(rootPath, controlCriteria,usedCheckpoint,checkpointTime,filePartitionDate);
            }
            if(controlFiles.isEmpty()) {
                return returnListFile;
            }
            for(FileInfo fileInfo: controlFiles) {
                TransferHistoryView transferHistoryView = transferHistoryViewMap.get(fileInfo.getPath());
                if (null != transferHistoryView && transferHistoryView.getFileModifiedTime() != null) {
                    if(!fileInfo.getModificationTime().truncatedTo(ChronoUnit.SECONDS).
                            isAfter(transferHistoryView.getFileModifiedTime().toLocalDateTime().truncatedTo(ChronoUnit.SECONDS))) {
                        continue;
                    }
                    else {
                        FileInfo newFile = fileSystemAdapter.getFileInfo(fileInfo.getPath());
                        if(!newFile.getModificationTime().truncatedTo(ChronoUnit.SECONDS).isAfter(transferHistoryView.getFileModifiedTime().toLocalDateTime().truncatedTo(ChronoUnit.SECONDS))) {
                            continue;
                        }
                    }
                }
                result.add(fileInfo.getPath());
            }
        }
        returnListFile.setFileList(result);
        if(criteria.getCheckpointTime() != null) {
//            log.info("epoch milli = {}",criteria.getCheckpointTime());
            returnListFile.setLatestModifiedTime(Timestamp.from(criteria.getCheckpointTime().toInstant()));
//            log.info("return list file milli = {}",Timestamp.from(criteria.getCheckpointTime().toInstant()));
        }
        return returnListFile;
    }

    private String hasControlFile(FileSystemAdapter adapter, String filePath, List<String> ctrlExtensions) throws IOException {
        String basePath = FilenameUtils.removeExtension(filePath);
        for(String ctrlExt: ctrlExtensions) {
            try {
                if(!adapter.exists(basePath + "." + ctrlExt)) {
                    int firstDot = basePath.indexOf('.');
                    String baseName = (firstDot == -1)
                            ? basePath
                            : basePath.substring(0, firstDot);
                    if(adapter.exists(baseName + "." + ctrlExt)){
                        return baseName + "." + ctrlExt;
                    }
                    return null;
                }
                else {
                    return basePath + "." + ctrlExt;
                }
            } catch (IOException e) {
                log.error("{} !!!Error checking control file existence", AppConst.PREFIX_LOG, e);
                return null;
            }
        }
        return null;
    }
/*

    public List<Path> listZeroSizeCtrlFile( Path rootPath, Integer filePerRound, LocalDateTime afterDate
            , List<String> ctrlExtensions, List<String> srcExtensions, List<String> wildcardPatterns, boolean isOverwrite, TaskConst.MoveType moveType, String taskId) throws IOException {
        List<Path> sourceFilePaths = new ArrayList<>();
//        RemoteIterator<LocatedFileStatus> fileStatusListIterator = fs.listFiles(rootPath, true);
        FileStatus[] fileStatusList = fs.globStatus(new Path(rootPath.toString()+"/*.*")
                , new AndPathFilter(generateSrcExtensionPathFilter(srcExtensions.toArray(new String[0]))
                        , generateWildCardPatternPathFilter(wildcardPatterns.toArray(new String[0])))
        );
        if(ArrayUtils.isEmpty(fileStatusList)) {
            log.warn("[Blendata] !!! Not found match files on path {} of task id {}", rootPath.toString(), taskId);
            return Collections.EMPTY_LIST;
        }
        log.info("[Query Transfer History][ZERO][Start] {}", DateUtil.convertToString(DateUtil.getCurrentDateWithTime(), DateUtil.DD_sl_MM_sl_YYYY_HH_mm_ss));
        Set<String> allSuccessOrProcessingFiles = new HashSet<>();
        if(moveType.equals(org.gable.blendata.nextmove.shared.constant.TaskConst.MoveType.MOVE) && isOverwrite) {
            allSuccessOrProcessingFiles = transferHistoryService.getProcessingFiles(rootPath.toString());
        }else{
            allSuccessOrProcessingFiles = transferHistoryService.getSuccessOrProcessingFiles(rootPath.toString());
        }

        log.info("[Query Transfer History][ZERO][End] {}", DateUtil.convertToString(DateUtil.getCurrentDateWithTime(), DateUtil.DD_sl_MM_sl_YYYY_HH_mm_ss));
        int countFile = 0;
//        while (fileStatusListIterator.hasNext()) {
        for (FileStatus fileStatus : fileStatusList) {
            if(null != filePerRound &&  countFile >= filePerRound){
                break;
            }
//            LocatedFileStatus fileStatus = fileStatusListIterator.next();
            if(allSuccessOrProcessingFiles.contains(fileStatus.getPath().toString())
                    || (!CollectionUtils.isEmpty(srcExtensions) && !srcExtensions.contains(FilePathUtil.getExtension(fileStatus.getPath().toString())))
                    || (null != afterDate && afterDate.isAfter(DateUtil.convertToLocalDateTime(fileStatus.getModificationTime())))
                    || (!isMatchWildcardPatterns(wildcardPatterns, FilenameUtils.getName(fileStatus.getPath().toString())))
            ){
                continue;
            }
            String extension = FilenameUtils.getExtension(fileStatus.getPath().getName());
            if(!ctrlExtensions.contains(extension)){
                boolean isCtrlFileExist = ctrlExtensions.stream().anyMatch(ctrlExtension -> {
                    try {
                        return fs.exists(new Path(FilenameUtils.removeExtension(fileStatus.getPath().toString()) + "." + ctrlExtension));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
               if(isCtrlFileExist){
                   sourceFilePaths.add(fileStatus.getPath());
                   countFile++;
               }
            }
        }
        return sourceFilePaths;
    }
*/

//    private boolean isMatchWildcardPatterns(List<String> wildcardPatterns, String filename){
//        if(CollectionUtils.isEmpty(wildcardPatterns)){
//            return true;
//        }
//        return wildcardPatterns.stream().
//                anyMatch(wildcardPattern -> FilenameUtils.wildcardMatch(filename, wildcardPattern));
//    }
}
