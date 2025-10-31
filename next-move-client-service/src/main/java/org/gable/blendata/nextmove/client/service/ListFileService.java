package org.gable.blendata.nextmove.client.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.hadoop.fs.*;
import org.gable.blendata.nextmove.client.adapter.FileInfo;
import org.gable.blendata.nextmove.client.adapter.FileListingCriteria;
import org.gable.blendata.nextmove.client.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.shared.constant.AppConst;
import org.gable.blendata.nextmove.shared.constant.TaskConst;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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
                                         TaskConst.SourceType sourceType, String host) throws IOException {

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

        Set<String> excludeFiles = getExcludeFiles(sourceType, rootPath, moveType, host, isOverwrite);

        return files.stream()
                .map(FileInfo::getPath)
                .filter(path -> !excludeFiles.contains(path))
                .limit(criteria.getMaxFiles() != null ? criteria.getMaxFiles() : Long.MAX_VALUE)
                .collect(Collectors.toList());
    }

    private Set<String> getExcludeFiles(TaskConst.SourceType sourceType, String rootPath, TaskConst.MoveType moveType, String host, boolean isOverwrite) {
//        if (moveType.equals(TaskConst.MoveType.MOVE) && isOverwrite) {
        if (isOverwrite) {
            return transferHistoryService.getProcessingFiles(sourceType, rootPath, host);
        } else {
            return transferHistoryService.getSuccessOrProcessingFiles(sourceType, rootPath, host);
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

    public List<String> listZeroSizeCtrlFile(FileSystemAdapter fileSystemAdapter, String rootPath,
                                             Integer filePerRound, LocalDateTime afterDate,
                                             List<String> ctrlExtensions, List<String> srcExtensions,
                                             List<String> wildcardPatterns, boolean isOverwrite,
                                             TaskConst.MoveType moveType, String taskId,
                                             TaskConst.SourceType sourceType, String host,
                                             String controlPath, List<String> controlFileNamePattern) throws IOException {

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

        Set<String> excludeFiles = getExcludeFiles(sourceType, rootPath, moveType, host, isOverwrite);
        List<String> result = new ArrayList<>();

        for (FileInfo file : files) {
            log.info("file name = {}",file.getName());
            if(result.size() >= (criteria.getMaxFiles() != null ? criteria.getMaxFiles() : Integer.MAX_VALUE)) {
                break;
            }
            if (excludeFiles.contains(file.getPath())) {
                continue;
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
                            result.add(file.getPath());
                            result.add(FilenameUtils.removeExtension(
                                    controlAbsolutePath) + "." + ctrlExtensions.get(0));
                        }
                    } else {
                        if (hasControlFile(fileSystemAdapter, file.getPath(), ctrlExtensions)) {
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
                if (excludeFiles.contains(fileInfo.getPath())) {
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
