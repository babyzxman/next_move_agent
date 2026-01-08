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
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
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


    public List<String> listNoneCtrlFile(FileSystemAdapter fileSystemAdapter, String rootPath,
                                         Integer filePerRound, LocalDateTime afterDate,
                                         List<String> srcExtensions, List<String> wildcardPatterns,
                                         boolean isOverwrite, TaskConst.MoveType moveType, String taskId,
                                         TaskConst.SourceType sourceType, String host,String destPath) throws IOException {

        FileListingCriteria criteria = FileListingCriteria.builder()
                .maxFiles(filePerRound)
                .afterDate(afterDate)
                .extensions(srcExtensions)
                .wildcardPatterns(wildcardPatterns)
                .build();

        List<FileInfo> files = fileSystemAdapter.listFiles(rootPath, criteria);

        if (files.isEmpty()) {
            log.warn("[Blendata] !!! Not found match files on path {} of task id {}", rootPath, taskId);
            return new ArrayList<>();
        }

        List<TransferHistoryView> excludeFiles = getExcludeFiles(
                sourceType, rootPath, moveType, host, isOverwrite,
                destPath);

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
                    else if(file.getModificationTime().isAfter(
                            transferHistoryView.getFileModifiedTime().toLocalDateTime())) {
                        filePath.add(file.getPath());
                        count++;
                    }
                }
            }
            else {
                filePath.add(file.getPath());
            }
        }

        return filePath;
    }

    private List<TransferHistoryView> getExcludeFiles(TaskConst.SourceType sourceType, String rootPath,
                                                      TaskConst.MoveType moveType, String host, boolean isOverwrite,
                                                      String destPath) {
//        if (moveType.equals(TaskConst.MoveType.MOVE) && isOverwrite) {
        if (isOverwrite) {
            return transferHistoryService.getProcessingFiles(sourceType, rootPath, host,destPath);
        } else {
            return transferHistoryService.getSuccessOrProcessingFiles(sourceType, rootPath, host,destPath);
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

    public String getControlFilePath(FileInfo file, String ctrlExtensions, String controlPath) {
        if (controlPath != null) {
            String controlAbsolutePath = controlPath.endsWith("/") ? controlPath + file.getName() :
                    controlPath + File.separator + file.getName();
            String basePath = FilenameUtils.removeExtension(controlAbsolutePath);
            return basePath + "." + ctrlExtensions;
        }
        else {
            String basePath = FilenameUtils.removeExtension(file.getPath());
            return basePath + "." + ctrlExtensions;
        }
    }

    public List<String> listZeroSizeCtrlFile(FileSystemAdapter fileSystemAdapter, String rootPath,
                                             Integer filePerRound, LocalDateTime afterDate,
                                             List<String> ctrlExtensions, List<String> srcExtensions,
                                             List<String> wildcardPatterns, boolean isOverwrite,
                                             TaskConst.MoveType moveType, String taskId,
                                             TaskConst.SourceType sourceType, String host,
                                             String controlPath, List<String> controlFileNamePattern,
                                             String destPath) throws IOException {

        FileListingCriteria criteria =  FileListingCriteria.builder()
                .maxFiles(filePerRound)
                .afterDate(afterDate)
                .extensions(srcExtensions)
                .wildcardPatterns(wildcardPatterns)
                .ctrlExtensions(ctrlExtensions)
                .build();

        List<FileInfo> files = fileSystemAdapter.listFiles(rootPath, criteria);

        if (files.isEmpty()) {
            log.warn("[Blendata] !!! Not found match files on path {} of task id {}", rootPath, taskId);
            return new ArrayList<>();
        }

        List<TransferHistoryView> excludeFiles = getExcludeFiles(
                sourceType, rootPath, moveType, host, isOverwrite,destPath);
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
            if (transferHistoryView != null && transferHistoryView.getFileModifiedTime() != null
                && !file.getModificationTime().truncatedTo(ChronoUnit.SECONDS).
                    isAfter(transferHistoryView.getFileModifiedTime().toLocalDateTime().truncatedTo(ChronoUnit.SECONDS))) {
                if(controlFileNamePattern == null) {
                    if(transferHistoryViewMap.get(getControlFilePath(file,ctrlExtensions.get(0),controlPath)) == null) {
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

            String extension = FilenameUtils.getExtension(file.getName());
            if(controlFileNamePattern != null) {
                result.add(file.getPath());
            }
            else {
                if (!ctrlExtensions.contains(extension)) {
                    if (controlPath != null) {
                        String controlAbsolutePath = controlPath.endsWith("/") ? controlPath + file.getName() :
                                controlPath + File.separator + file.getName();
                        if (hasControlFile(fileSystemAdapter, controlAbsolutePath, ctrlExtensions)) {
                            if(!isControlFileFailedToCopy)
                                result.add(file.getPath());
                            result.add(FilenameUtils.removeExtension(
                                    controlAbsolutePath) + "." + ctrlExtensions.get(0));
                        }
                    } else {
                        if (hasControlFile(fileSystemAdapter, file.getPath(), ctrlExtensions)) {
                            if(!isControlFileFailedToCopy)
                                result.add(file.getPath());
                            result.add(FilenameUtils.removeExtension(
                                    file.getPath()) + "." + ctrlExtensions.get(0));
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
                controlFiles = fileSystemAdapter.listFiles(controlPath, controlCriteria);
            }
            else {
                controlFiles = fileSystemAdapter.listFiles(rootPath, controlCriteria);
            }
            if(controlFiles.isEmpty()) {
                return new ArrayList<>();
            }
            for(FileInfo fileInfo: controlFiles) {
                TransferHistoryView transferHistoryView = transferHistoryViewMap.get(fileInfo.getPath());
                if (null != transferHistoryView && transferHistoryView.getFileModifiedTime() != null
                        && !fileInfo.getModificationTime().truncatedTo(ChronoUnit.SECONDS).
                        isAfter(transferHistoryView.getFileModifiedTime().toLocalDateTime().truncatedTo(ChronoUnit.SECONDS))) {
                    continue;
                }
                result.add(fileInfo.getPath());
            }
        }

        return result;
    }

    private boolean hasControlFile(FileSystemAdapter adapter, String filePath, List<String> ctrlExtensions) throws IOException {
        String basePath = FilenameUtils.removeExtension(filePath);

        return ctrlExtensions.stream()
                .anyMatch(ctrlExt -> {
                    try {
                        return adapter.exists(basePath + "." + ctrlExt);
                    } catch (IOException e) {
                        log.error("{} !!!Error checking control file existence", AppConst.PREFIX_LOG, e);
                        return false;
                    }
                });
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
