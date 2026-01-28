package org.gable.blendata.nextmove.service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Options;
import org.apache.hadoop.fs.Path;
import org.gable.blendata.nextmove.service.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.service.adapter.impl.SftpFileSystemAdapter;
import org.gable.blendata.nextmove.service.config.AppConfig;
import org.gable.blendata.nextmove.service.config.HadoopConfig;
import org.gable.blendata.nextmove.service.config.SftpConnectionProperties;
import org.gable.blendata.nextmove.service.dto.ReconcileInfoDTO;
import org.gable.blendata.nextmove.service.repo.CheckpointMasterRepository;
import org.gable.blendata.nextmove.service.service.connection.ConnectionManager;
import org.gable.blendata.nextmove.service.service.connection.ConnectionManagerFactory;
import org.gable.blendata.nextmove.shared.constant.AppConst;
import org.gable.blendata.nextmove.shared.constant.FileStatus;
import org.gable.blendata.nextmove.shared.constant.TaskConst;
import org.gable.blendata.nextmove.shared.dto.FileInfoDTO;
import org.gable.blendata.nextmove.shared.dto.StopTaskRequestWrapper;
import org.gable.blendata.nextmove.shared.dto.TransferRequestWrapper;
import org.gable.blendata.nextmove.shared.entity.TransferHistory;
import org.gable.blendata.nextmove.shared.exception.DuplicateException;
import org.gable.blendata.nextmove.shared.exception.FileSizeMisMatchException;
import org.gable.blendata.nextmove.shared.exception.TaskCancelledException;
import org.gable.blendata.nextmove.shared.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZeroCtrlService extends MoveService{

    @Autowired
    private TaskExecutor taskExecutor;

    private final int RETRY_COUNT = 5;
    private final int WAIT_RETRY = 3000;
    private final AppConfig appConfig;
    private final TransferHistoryService transferHistoryService;
    private final ReconcileLogService reconcileLogService;
    private final ConnectionManagerFactory connectionManagerFactory;
    private final CheckpointMasterRepository checkpointMasterRepository;

    private final Map<String, CompletableFuture<Void>> runningTasks = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> cancellationFlags = new ConcurrentHashMap<>();
    private final HadoopConfig hadoopConfig;
    private final Map<String, FileSystem> fileSystems;


    private void logExecutorStats(String taskKey) {
        if (taskExecutor instanceof ThreadPoolTaskExecutor) {
            ThreadPoolTaskExecutor tp = (ThreadPoolTaskExecutor) taskExecutor;
            ThreadPoolExecutor ex = tp.getThreadPoolExecutor();

            if (ex == null) {
                log.warn("{} TaskKey({}) ThreadPoolExecutor not initialized yet",
                        AppConst.PREFIX_LOG, taskKey);
                return;
            }

            log.info("{} TaskKey({}) ExecutorStats: active={}, poolSize={}, core={}, max={}, " +
                            "largest={}, completed={}, taskCount={}, queueSize={}, queueRemaining={}",
                    AppConst.PREFIX_LOG,
                    taskKey,
                    ex.getActiveCount(),
                    ex.getPoolSize(),
                    ex.getCorePoolSize(),
                    ex.getMaximumPoolSize(),
                    ex.getLargestPoolSize(),
                    ex.getCompletedTaskCount(),
                    ex.getTaskCount(),
                    ex.getQueue().size(),
                    ex.getQueue().remainingCapacity()
            );

        } else {
            log.warn("{} TaskKey({}) TaskExecutor is {} (no thread stats available)",
                    AppConst.PREFIX_LOG,
                    taskKey,
                    taskExecutor.getClass().getName());
        }
    }

    public void moveFiles(TransferRequestWrapper transferRequestWrapper) {
        String srcRootPath = transferRequestWrapper.getSourceRootPathStr();
        String taskKey = transferRequestWrapper.getClientId() + "_" + transferRequestWrapper.getTaskId() + "_" + StringUtil.getRandomAlphanumericString(8);
        log.info("{} Source root path: {}", AppConst.PREFIX_LOG, srcRootPath);

        //...P'Ban Request
        if(CollectionUtils.isNotEmpty(transferRequestWrapper.getFilePathStrs())) {
            transferRequestWrapper.getFilePathStrs().forEach(filePathStr -> {
                log.info("{} Task ID({}) Request to move file {} to {}", AppConst.PREFIX_LOG
                        , transferRequestWrapper.getTaskId()
                        , filePathStr
                        , transferRequestWrapper.getDestinationRootPathStr());
            });
        }

        hadoopConfig.registerFileSystem(fileSystems, FileSystemUtil.getFileSystemKey(transferRequestWrapper.getSourceRootPathStr()));
        hadoopConfig.registerFileSystem(fileSystems, FileSystemUtil.getFileSystemKey(transferRequestWrapper.getDestinationRootPathStr()));

        //...Save into table and mark them to processing status

        List<TransferHistory> transferHistories = transferHistoryService.saveProcessing(transferRequestWrapper);
        final List<Long> transferHistoryIds = transferHistories.stream().map(TransferHistory::getId).collect(Collectors.toList());
        AtomicBoolean cancellationFlag = new AtomicBoolean(false);
        synchronized (runningTasks) {
            cancellationFlags.put(taskKey, cancellationFlag);
        }
        logExecutorStats(taskKey);

        CompletableFuture<Void> future = CompletableFuture.supplyAsync(
                processFileTransfers(transferHistoryIds, transferRequestWrapper, cancellationFlag, taskKey),
                taskExecutor);
        runningTasks.put(taskKey, future);
    }

    private Supplier<Void> processFileTransfers(List<Long> transferHistoryIds, TransferRequestWrapper transferRequestWrapper, AtomicBoolean cancellationFlag, String taskKey){
        return () -> {
            log.info("{} Task {} running on thread {}", AppConst.PREFIX_LOG, taskKey, Thread.currentThread().getName());
            List<ReconcileInfoDTO> reconcileInfos = new ArrayList<>();
            List<TransferHistory> transferHistories = new ArrayList<>();
            ConnectionManager connectionManager = null;
            Set<Long> transferHistoryIdSet = new HashSet<>(transferHistoryIds);
            FileSystemAdapter adapter = null;
            try {
                String destRootPathStr = transferRequestWrapper.getDestinationRootPathStr();
                boolean isDeleteSrc = TaskConst.MoveType.MOVE.name().equalsIgnoreCase(transferRequestWrapper.getMoveType());
                transferHistories = transferHistoryService.findByTaskId(transferRequestWrapper.getTaskId());
                transferHistories = transferHistories.stream().filter(f -> {
                    return transferHistoryIdSet.contains(f.getId());
                }).collect(Collectors.toList());
                connectionManager = connectionManagerFactory.createConnectionManager(transferRequestWrapper.getSourceType()
                        , transferRequestWrapper.getSourceRootPathStr()
                        , transferRequestWrapper.getDestinationRootPathStr()
                        , transferRequestWrapper.getSourceProperties());
                for(int i =0;i<RETRY_COUNT;i++) {
                    try {
                        adapter = connectionManager.createConnection();
                        break;
                    }
                    catch (Exception ex) {
                        Thread.sleep(WAIT_RETRY);
                        if(i == RETRY_COUNT-1) {
                            throw new Exception();
                        }
                    }
                }

                int maxRetry = Objects.isNull(transferRequestWrapper.getRetry())? 1 : transferRequestWrapper.getRetry() +1; //...normal + retry
                int retry = 0;
                boolean hasError = true;
                do{
                    if (cancellationFlag.get() || Thread.currentThread().isInterrupted()) {
                        log.info("{} Task {} cancelled during retry loop", AppConst.PREFIX_LOG, taskKey);
                        throw new TaskCancelledException("Task was cancelled or interrupted during retry loop");
                    }
                    for (TransferHistory transferHistory : transferHistories) {
                        if (cancellationFlag.get() || Thread.currentThread().isInterrupted()) {
                            log.info("{} Task {} cancelled while processing files", AppConst.PREFIX_LOG, taskKey);
                            throw new TaskCancelledException("Task was cancelled or interrupted while processing files");
                        }
                        if(FileStatus.SUCCESS.name().equals(transferHistory.getStatus())){
                            continue;
                        }
                        if(retry > 0){
                            transferHistory.setRetry(retry);
                        }
                        FileInfoDTO srcFile = adapter.getSourceFileInfo(transferHistory.getFilePath(), transferRequestWrapper.getSourceRootPathStr());
                        boolean isCompressFile = CompressFileUtil.isSupportedFormat(srcFile.getFileName());
                        transferHistory.setFileSize(srcFile.getSize());
                        transferHistory.setFileModifiedTime(srcFile.getModifyTime());

                        //...Check connection is alive or not for SFTP source
                        adapter = connectionManager.ensureConnectionAlive(
                                adapter, adapter.getSourceFileSystem(), taskKey,transferRequestWrapper.getSourceRootPathStr());

                        if (!transferRequestWrapper.isNotExtract() && isCompressFile) {
                            processCompressFile(reconcileInfos, transferHistory, destRootPathStr, isDeleteSrc
                                    , srcFile, retry==0? null : retry
                                    , transferRequestWrapper.isCreateTargetZipBaseDir()
                                    , transferRequestWrapper.isOverwrite()
                                    , cancellationFlag
                                    , adapter, transferRequestWrapper.getCtrlExtensions()
                                    , transferRequestWrapper.getDestinationCtrlRootPathStr());

                        } else {  //...Regular File
                            processRegularFile(reconcileInfos, transferHistory, destRootPathStr, isDeleteSrc
                                    , srcFile, retry==0? null : retry
                                    , transferRequestWrapper.isOverwrite()
                                    , transferRequestWrapper.isDownloadToTmpBeforeUpload()
                                    , appConfig.getSftpCopyDir()
                                    , cancellationFlag
                                    , adapter, transferRequestWrapper.getCtrlExtensions()
                                    , transferRequestWrapper.getDestinationCtrlRootPathStr());
                        }
                    }
                    hasError = transferHistories.stream().anyMatch(transferHistory -> !FileStatus.SUCCESS.name().equals(transferHistory.getStatus()));
                    retry++;

                }while(retry < maxRetry && hasError && !cancellationFlag.get() && !Thread.currentThread().isInterrupted());

            } catch (Throwable e){
                String errorNo = ErrorUtil.generateErrorNo(appConfig.getAppId());
                if(e instanceof TaskCancelledException){
                    log.error("{} !!!ErrorNo({}) : Task {} was cancelled: {}, interrupt status: {}", AppConst.PREFIX_LOG, errorNo, taskKey, e.getMessage(), Thread.currentThread().isInterrupted());
                }else {
                    log.error("{} !!!ErrorNo({}) : Task {} error : {} ", AppConst.PREFIX_LOG, errorNo, taskKey, e.getMessage(), e);
                }
                List<TransferHistory> processingTransferHistories = transferHistories.stream()
                        .filter(t -> t.getStatus().equalsIgnoreCase(FileStatus.PROCESSING.name()))
                        .collect(Collectors.toList());
                markTransferHistoriesAsFailed(processingTransferHistories, errorNo);
                addRemainingReconciledInfo(adapter, processingTransferHistories, reconcileInfos
                        , errorNo, ErrorUtil.getErrorMessage(e), transferRequestWrapper.getSourceRootPathStr());
            } finally{
                if(connectionManager != null){
                    try {
                        connectionManager.closeConnection(adapter);
                    } catch (IOException e) {
                        log.error("{} !!!Error cannot close connection : {}", AppConst.PREFIX_LOG, e.getMessage(), e);
                    }
                }
                SftpConnectionProperties sftpConnectionProperties = SftpConnectionProperties.fromMap(
                        transferRequestWrapper.getSourceProperties());
                saveFinish(transferHistories, sftpConnectionProperties.getHost(),
                        sftpConnectionProperties.getPort(),transferRequestWrapper.getUsedCheckpoint());
                //...Write log file
                if (!reconcileInfos.isEmpty()) {
                    reconcileLogService.writeLogFile(reconcileInfos, transferRequestWrapper.getSourceRootPathStr(), transferRequestWrapper.getTaskId());
                }
            }
            return null;
        };
    }

    private void addRemainingReconciledInfo(FileSystemAdapter adapter, List<TransferHistory> processingTransferHistories, List<ReconcileInfoDTO> reconcileInfos, String errorNo, String errorMsg, String sourceRootPath) {
        for(TransferHistory processingTransferHistory : processingTransferHistories){
            FileInfoDTO srcFile = adapter.getSourceFileInfo(processingTransferHistory.getFilePath(), sourceRootPath);
            reconcileInfos.add( ReconcileInfoDTO.builder()
                    .createDate(DateUtil.convertToString(LocalDateTime.now(), DateUtil.YYYYMMDDHHmmssSSS))
                    .fileSizeInBytes(srcFile.getSize())
                    .srcAbsoluteFilePathStr(srcFile.getAbsoluteFilePath())
                    .status(FileStatus.FAILED.name())
                    .retry(0)
                    .errorNo(errorNo)
                    .errMsg(errorMsg)
                    .build());
        }
    }

    private void processCompressFile(List<ReconcileInfoDTO> reconcileInfos, TransferHistory transferHistory, String destRootPathStr
            , boolean isDeleteSrc, FileInfoDTO srcFile, Integer retry
            , boolean isCreateTargetZipBaseDir, boolean overwrite, AtomicBoolean cancellationFlag
            , FileSystemAdapter adapter, List<String> ctrlExtensions, String destCtrlPath) throws IOException {
        LocalDateTime startTime = LocalDateTime.now();
        //...Define decompress directory
        String uniqueId = UUID.randomUUID().toString();
        String parentDecompressDir = appConfig.getDecompressDir() + "/" + appConfig.getAppId() + "_" +
                DateUtil.convertToString(DateUtil.getCurrentDateWithTime(), DateUtil.YYYYMMDDHHmmssSSS) + "_" +
                uniqueId;
        boolean isMoveSuccess = true;
        try {
            if (cancellationFlag.get() || Thread.currentThread().isInterrupted()) {
                log.info("{} Task cancelled before processing compress file", AppConst.PREFIX_LOG);
                throw new TaskCancelledException("Task was cancelled or interrupted before processing compress file");
            }
            String compressDirPath = parentDecompressDir + "/compress";
            String compressSrcFilePath = srcFile.getRelativeFilePath();
            String compressSrcFileName = srcFile.getFileName();
            String tempDestinationCompressFilePath = compressDirPath + "/" + compressSrcFileName;
            long srcFileSize = adapter.getFileSize(adapter.getSourceFileSystem(), compressSrcFilePath);

            if (cancellationFlag.get() || Thread.currentThread().isInterrupted()) {
                log.info("{} Task cancelled before copying compress file", AppConst.PREFIX_LOG);
                throw new TaskCancelledException("Task was cancelled or interrupted before copying compress file");
            }

            //...Copy compress file to local
            adapter.copyToLocal(compressSrcFilePath, tempDestinationCompressFilePath);

            long destinationFileSize = FileUtils.sizeOf(new File(tempDestinationCompressFilePath.toString()));
            if(srcFileSize != destinationFileSize){
                throw new FileSizeMisMatchException(new Path(tempDestinationCompressFilePath)
                        , String.format("Source and target file sizes do not match. "
                        + "Source: %s bytes, Target: %s bytes", srcFileSize+"", destinationFileSize+""));
            }

            if (cancellationFlag.get() || Thread.currentThread().isInterrupted()) {
                log.info("{} Task cancelled before decompressing file", AppConst.PREFIX_LOG);
                throw new TaskCancelledException("Task was cancelled or interrupted before decompressing file");
            }

            //...Decompress file into local
            String uncompressDirPath = parentDecompressDir + "/uncompress";
            FileUtils.forceMkdir(new File(uncompressDirPath));
            CompressFileUtil.decompressFile(compressDirPath + "/" + compressSrcFileName, uncompressDirPath);
            log.info("{} : compressSrcFilePath = {}, srcFile.getRoothPathStr = {}", AppConst.PREFIX_LOG, compressSrcFilePath, srcFile.getRootPathStr());
            Path destDir = new Path(destRootPathStr);
//                    FilenameUtils.getFullPathNoEndSeparator(compressSrcFilePath.toString().replaceAll(srcFile.getRootPathStr(), "")));
//                    FilenameUtils.getFullPathNoEndSeparator(compressSrcFilePath.toString().replaceFirst(StringUtil.convertWildcardToRegex(srcFile.getRootPathStr()), "")));
            log.info("{} : Source file path: {}, Uncompress directory : {}, Destination directory: {}", AppConst.PREFIX_LOG, compressSrcFilePath, uncompressDirPath, destDir);

            //...Move file one by one
            Collection<File> files = FileUtils.listFiles(new File(uncompressDirPath), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
            log.info("{} : Total {} files in compress file({})", AppConst.PREFIX_LOG, files.size(), uncompressDirPath);
            for(File file : files){
                if(ctrlExtensions.contains(FilenameUtils.getExtension(file.getName()))) {
                    destDir = new Path(destCtrlPath);
                }
                else {
                    destDir = new Path(destRootPathStr);
                }
                LocalDateTime subStartTime = LocalDateTime.now();
                String absoluteFilePath = file.getAbsolutePath();
//                String relativeFilePath = absoluteFilePath.replaceAll(uncompressDirPath, "");
                log.debug("{} absolute file path = {}, uncompress dir path = {}", AppConst.PREFIX_LOG, absoluteFilePath, uncompressDirPath);
                ReconcileInfoDTO reconcileInfo = ReconcileInfoDTO.builder()
                        .createDate(DateUtil.convertToString(LocalDateTime.now(), DateUtil.YYYYMMDDHHmmssSSS))
                        .fileSizeInBytes(file.length())
                        .srcAbsoluteFilePathStr(adapter.resolvePath(adapter.getSourceFileSystem(), compressSrcFilePath))
                        .build();
                reconcileInfos.add(reconcileInfo);
                try {
//                    Path destFilePath = new Path(destDir
//                            + (isCreateTargetZipBaseDir? "/"+FilenameUtils.getBaseName(compressSrcFileName) : "")
//                            + relativeFilePath);
                    Path destFilePath = new Path(destDir
                            + (isCreateTargetZipBaseDir? "/" + FilenameUtils.getBaseName(compressSrcFileName) : "")
                            + "/" + FilenameUtils.getName(file.getAbsolutePath()));
                    Path destFilePathProcessing = new Path(destFilePath.toString() + AppConst.PROCESSING_SUFFIX);
                    if(!overwrite && adapter.exists(adapter.getDestFileSystem(), destFilePath.toString())){
                        throw new DuplicateException(String.format("Target file %s already exists", destFilePath.toString()));
                    }
                    adapter.copyFromLocalFile(false, overwrite, new Path("file://"+absoluteFilePath), destFilePathProcessing);
                    FileUtil.rename(adapter.getDestFileSystem()
                            , destFilePathProcessing
                            , destFilePath
                            , overwrite? Options.Rename.OVERWRITE : Options.Rename.NONE);
                    reconcileInfo.setDestAbsoluteFilePathStr(adapter.resolvePath(adapter.getDestFileSystem(), destFilePath.toString()));
                    reconcileInfo.setStatus(FileStatus.SUCCESS.name());
                }catch(Exception e){
                    isMoveSuccess = false;
                    String errorNo = ErrorUtil.generateErrorNo(appConfig.getAppId());
                    log.error("{} !!!ErrorNo({}) cannot copy compress file '{}' to destination", AppConst.PREFIX_LOG, errorNo, file.getAbsolutePath(), e);
                    reconcileInfo.setStatus(FileStatus.FAILED.name());
                    reconcileInfo.setErrorNo(errorNo);
                    reconcileInfo.setErrMsg(ErrorUtil.getErrorMessage(e));
                }finally{
                    reconcileInfo.setRetry(retry);
                    reconcileInfo.setProcessTimeInMilliseconds(Duration.between(subStartTime, LocalDateTime.now()).toMillis());
                }
            }
//            transferHistory.setDestination(destDir.toString() + "/" + compressSrcFileName);
            transferHistory.setDestination(destDir.toString()
                    + (isCreateTargetZipBaseDir? "/"+FilenameUtils.getBaseName(compressSrcFileName) : "")
                    + "/" + compressSrcFileName);
            transferHistory.setStatus(isMoveSuccess? FileStatus.SUCCESS.name() : FileStatus.FAILED.name());
            if(isMoveSuccess){
                transferHistory.setErrorNo(null);
            }else{
                String errorNo = ErrorUtil.generateErrorNo(appConfig.getAppId()) + "<FILES IN COMPRESS FILE>";
                log.error("{} !!!ErrorNo({}) : Some files in compress file cannot move '{}'", AppConst.PREFIX_LOG, errorNo, transferHistory.getFilePath());
                transferHistory.setErrorNo(errorNo);
            }

        } catch (TaskCancelledException e) {
            String errorNo = ErrorUtil.generateErrorNo(appConfig.getAppId());
            log.error("{} !!!ErrorNo({}) : Task cancelled '{}'", AppConst.PREFIX_LOG, errorNo, transferHistory.getFilePath(), e);
            transferHistory.setErrorNo(errorNo);
            reconcileInfos.add(ReconcileInfoDTO.builder()
                    .createDate(DateUtil.convertToString(LocalDateTime.now(), DateUtil.YYYYMMDDHHmmssSSS))
                    .fileSizeInBytes(srcFile.getSize())
                    .srcAbsoluteFilePathStr(srcFile.getAbsoluteFilePath())
                    .errorNo(errorNo)
                    .errMsg(ErrorUtil.getErrorMessage(e))
                    .retry(retry)
                    .status(FileStatus.FAILED.name())
                    .build());
            throw e;
        }catch(Exception e) {       //...Error out of loop files
            handleFileSizeMisMatchException(adapter.getDestFileSystem(), e);
            String errorNo = ErrorUtil.generateErrorNo(appConfig.getAppId());
            log.error("{} !!!ErrorNo({}) cannot copy or extract compress file '{}' to local", AppConst.PREFIX_LOG, errorNo, srcFile.getRelativeFilePath(), e);
            transferHistory.setErrorNo(errorNo);
            transferHistory.setStatus(FileStatus.FAILED.name());
            reconcileInfos.add(ReconcileInfoDTO.builder()
                    .createDate(DateUtil.convertToString(LocalDateTime.now(), DateUtil.YYYYMMDDHHmmssSSS))
                    .fileSizeInBytes(srcFile.getSize())
                    .srcAbsoluteFilePathStr(srcFile.getAbsoluteFilePath())
                    .errorNo(errorNo)
                    .errMsg(ErrorUtil.getErrorMessage(e))
                    .retry(retry)
                    .status(FileStatus.FAILED.name())
                    .build());
        }finally{
            transferHistory.setProcessTime(Duration.between(startTime, LocalDateTime.now()).toMillis());
            //... Delete temporary compress and uncompress directory
            FileUtils.deleteQuietly(new File(parentDecompressDir));
            if(isMoveSuccess && isDeleteSrc) {           //...Waiting for asking : when some file in compress cannot move
                adapter.delete(adapter.getSourceFileSystem(), srcFile.getRelativeFilePath());
            }
        }
    }

    private void processRegularFile(List<ReconcileInfoDTO> reconcileInfos, TransferHistory transferHistory, String destRootPathStr
            , boolean isDeleteSrc, FileInfoDTO srcFile, Integer retry
            , boolean overwrite, boolean downloadToTmpBeforeUpload, String sftpCopyDir
            , AtomicBoolean cancellationFlag, FileSystemAdapter adapter
            , List<String> ctrlExtensions, String destCtrlPath) throws IOException {
        boolean isSuccess = false;
        LocalDateTime startTime = LocalDateTime.now();
        long srcFileSize = srcFile.getSize();
        ReconcileInfoDTO reconcileInfo = ReconcileInfoDTO.builder()
                .createDate(DateUtil.convertToString(LocalDateTime.now(), DateUtil.YYYYMMDDHHmmssSSS))
                .fileSizeInBytes(srcFile.getSize())
                .srcAbsoluteFilePathStr(srcFile.getAbsoluteFilePath())
                .build();
        String tmpRootPath = destRootPathStr;
        reconcileInfos.add(reconcileInfo);
        try {
            if (cancellationFlag.get() || Thread.currentThread().isInterrupted()) {
                log.info("{} Task cancelled before processing regular file", AppConst.PREFIX_LOG);
                throw new TaskCancelledException("Task was cancelled or interrupted before processing regular file");
            }
            if(ctrlExtensions.contains(FilenameUtils.getExtension(srcFile.getFileName()))) {
                destRootPathStr = destCtrlPath;
            }
            else {
                destRootPathStr = tmpRootPath;
            }
            String destinationFilePath = destRootPathStr + "/" + srcFile.getFileName();
            String destinationFilePathProcessing = destRootPathStr + "/" + srcFile.getFileName() + AppConst.PROCESSING_SUFFIX;
            String srcFilePath = FileSystemUtil.getSchemeAndAuthority(srcFile.getRootPathStr()) + srcFile.getRelativeFilePath();
//            String destinationFilePath = destRootPathStr +
//                    srcFile.getRelativeFilePath().replaceFirst(StringUtil.convertWildcardToRegex(srcFile.getRootPathStr()), "");
//            String destinationFilePathProcessing = destRootPathStr +
//                    srcFile.getRelativeFilePath().replaceFirst(StringUtil.convertWildcardToRegex(srcFile.getRootPathStr()), "") + AppConst.PROCESSING_SUFFIX;
//            String srcFilePath = srcFile.getRelativeFilePath();

//            long srcFileSize = adapter.getFileSize(adapter.getSourceFileSystem(), srcFilePath);
            log.debug(">>> Exists {}: {}", destinationFilePath, adapter.exists(adapter.getDestFileSystem(), destinationFilePath));
//            if(adapter.exists(adapter.getDestFileSystem(), destinationFilePath)){
//                throw new DuplicateException(String.format("Target file %s already exists", destinationFilePath.toString()));
//            }
            if (cancellationFlag.get() || Thread.currentThread().isInterrupted()) {
                log.info("{} Task cancelled before copying regular file", AppConst.PREFIX_LOG);
                throw new TaskCancelledException("Task was cancelled or interrupted before copying regular file");
            }
            if (downloadToTmpBeforeUpload && adapter instanceof SftpFileSystemAdapter) {
                ((SftpFileSystemAdapter) adapter).copyWithTempDownload(srcFilePath, destinationFilePath, true, sftpCopyDir);
            } else {
                adapter.copy(adapter.getDestFileSystem(), srcFilePath, destinationFilePath, isDeleteSrc, true);
            }
            log.info("skip renamed file");
//            long destinationFileSize = adapter.getDestFileSystem().getFileStatus(new Path(destinationFilePathProcessing)).getLen();
//            if(srcFileSize != destinationFileSize){
//                throw new FileSizeMisMatchException(new Path(destinationFilePathProcessing)
//                        , String.format("Source and target file sizes do not match. "
//                        + "Source: %s bytes, Target: %s bytes", srcFileSize+"", destinationFileSize+""));
//            }

//            FileUtil.rename(adapter.getDestFileSystem()
//                    , new Path(destinationFilePathProcessing)
//                    , new Path(destinationFilePath)
//                    , overwrite? Options.Rename.OVERWRITE : Options.Rename.NONE);
            reconcileInfo.setDestAbsoluteFilePathStr(adapter.getDestFileSystem().resolvePath(new Path(destinationFilePath)).toString());
            transferHistory.setDestination(destinationFilePath.toString());
            transferHistory.setErrorNo(null);
            isSuccess = true;
        } catch (TaskCancelledException e) {
            String errorNo = ErrorUtil.generateErrorNo(appConfig.getAppId());
            log.error("{} !!!ErrorNo({}) : Task cancelled '{}'", AppConst.PREFIX_LOG, errorNo, transferHistory.getFilePath(), e);
            transferHistory.setErrorNo(errorNo);
            transferHistory.setErrorMsg(e.getMessage());
            reconcileInfo.setErrorNo(errorNo);
            reconcileInfo.setErrMsg(e.getMessage() + "(" + ErrorUtil.getCauseClassInfo(e.getStackTrace()) + ")");
            throw e;
        } catch (Throwable t) {
            // this is the missing branch that explains FAILED without errorNo
            String errorNo = ErrorUtil.generateErrorNo(appConfig.getAppId());
            log.error("{} !!!ErrorNo({}) : FATAL throwable while processing '{}', type={}",
                    AppConst.PREFIX_LOG, errorNo, transferHistory.getFilePath(), t.getClass().getName(), t);

            transferHistory.setErrorNo(errorNo);
            transferHistory.setErrorMsg(t.getMessage());
            reconcileInfo.setErrorNo(errorNo);
            reconcileInfo.setErrMsg(t.getMessage());
        } finally {
            transferHistory.setProcessTime(Duration.between(startTime, LocalDateTime.now()).toMillis());
            transferHistory.setStatus(isSuccess ? FileStatus.SUCCESS.name() :FileStatus.FAILED.name());
            reconcileInfo.setStatus(isSuccess ? FileStatus.SUCCESS.name() :FileStatus.FAILED.name());
            reconcileInfo.setRetry(retry);
            if(isSuccess && isDeleteSrc) {
                adapter.delete(adapter.getSourceFileSystem(), srcFile.getRelativeFilePath());
            }
        }
    }

    private void markTransferHistoriesAsFailed(List<TransferHistory> transferHistories, String errorNo) {
        for (TransferHistory transferHistory : transferHistories) {
            if (!FileStatus.SUCCESS.name().equals(transferHistory.getStatus())) {
                transferHistory.setStatus(FileStatus.FAILED.name());
                if(StringUtils.isEmpty(transferHistory.getErrorNo())) {
                    transferHistory.setErrorNo(errorNo);
                }
            }
        }
    }

    private void saveFinish(List<TransferHistory> transferHistories,
                            String host,Integer port,Boolean isUsedCheckpoint) {
        int batchCount = 0;
        String sourceRootPath = null;
        List<String> updatePath = new ArrayList<>();
        Integer filePartitionDate = null;
        for (TransferHistory transferHistory : transferHistories) {
            sourceRootPath = transferHistory.getSourceRootPath();
            transferHistory.setModifiedBy(appConfig.getAppId());
            transferHistory.setModifiedDate(DateUtil.getCurrentDateWithTime());
            log.info("transfer history file modify time = {}",transferHistory.getFileModifiedTime());
            transferHistoryService.save(transferHistory);
            filePartitionDate = transferHistory.getFilePartitionDate();
            if(isUsedCheckpoint) {
                if (transferHistory.getStatus().equals(FileStatus.SUCCESS.toString())) {
                    updatePath.add(transferHistory.getFilePath());
                    batchCount++;
                }
                if (batchCount > 5000) {
                    batchCount = 0;
                    checkpointMasterRepository.updateCheckpointMasterFileListFinished(
                            sourceRootPath, updatePath, host, port, transferHistory.getFilePartitionDate());
                    updatePath = new ArrayList<>();
                }
            }
        }
        if(isUsedCheckpoint) {
            if (!updatePath.isEmpty())
                checkpointMasterRepository.updateCheckpointMasterFileListFinished(
                        sourceRootPath, updatePath, host, port, filePartitionDate);
        }
    }

    public Map<String, String> stopMoveFiles(List<StopTaskRequestWrapper> stopTaskRequestWrappers) {
        Map<String, String> taskStatusSummary = new HashMap<>();
        if (stopTaskRequestWrappers == null) {
            log.info("{}[Stop Transfer] No tasks provided to stop.", AppConst.PREFIX_LOG);
            return taskStatusSummary;
        }
        for (StopTaskRequestWrapper stopTaskRequestWrapper : stopTaskRequestWrappers) {
            String keyPrefix = stopTaskRequestWrapper.getClientId() + "_" + stopTaskRequestWrapper.getTaskId();
            List<String> matchedKeys = runningTasks.keySet().stream()
                    .filter(k -> k.startsWith(keyPrefix))
                    .collect(Collectors.toList());

            if (matchedKeys.isEmpty()) {
                log.info("{}[Stop Transfer] No running task found for key prefix {}", AppConst.PREFIX_LOG, keyPrefix);
                continue;
            }

            for (String matchedKey : matchedKeys) {
                synchronized (runningTasks) {
                    AtomicBoolean cancellationFlag = cancellationFlags.get(matchedKey);
                    if (cancellationFlag != null) {
                        cancellationFlag.set(true);
                        log.info("{}[Stop Transfer] Cancellation flag set for task {}", AppConst.PREFIX_LOG, matchedKey);
                    }
                    runningTasks.computeIfPresent(matchedKey, (key, future) -> {
                        future.cancel(true); // Send interrupt signal to thread
                        try {
                            future.get(TASK_CANCELLATION_TIMEOUT_SECONDS, TimeUnit.SECONDS); // Wait for cancellation
                            taskStatusSummary.put(matchedKey, "COMPLETED");
                            log.info("{}[Stop Transfer] Task {} completed", AppConst.PREFIX_LOG, matchedKey);
                        } catch (CancellationException e) {
                            taskStatusSummary.put(matchedKey, "CANCELLED");
                            log.info("{}[Stop Transfer] Task {} was cancelled", AppConst.PREFIX_LOG, matchedKey);
                        } catch (TimeoutException e) {
                            log.error("{}[Stop Transfer] Timeout! Task {} is still running after 5 seconds.", AppConst.PREFIX_LOG, matchedKey);
                            taskStatusSummary.put(matchedKey, "TIMEOUT");
                        } catch (InterruptedException e) {
                            log.info("{}[Stop Transfer] Task {} was interrupted, interrupt status: {}", AppConst.PREFIX_LOG, matchedKey, Thread.currentThread().isInterrupted());
                            taskStatusSummary.put(matchedKey, "INTERRUPTED");
                            Thread.currentThread().interrupt(); // Restore interrupt status
                        } catch (ExecutionException e) {
                            log.info("{}[Stop Transfer] Task {} failed: {}", AppConst.PREFIX_LOG, matchedKey, e.getCause().getMessage());
                            taskStatusSummary.put(matchedKey, "FAILED");
                        }
                        return null;
                    });
                    cancellationFlags.remove(matchedKey);
                }
            }
        }
        log.info("{}[Stop Transfer] Summary: {}", AppConst.PREFIX_LOG, taskStatusSummary);
        return taskStatusSummary;
    }

    @Scheduled(fixedDelay = CLEANUP_INTERVAL_MS) // Every 5 minutes
    public void cleanupCompletedTasks() {
        synchronized (runningTasks) {
            Iterator<Map.Entry<String, CompletableFuture<Void>>> iterator = runningTasks.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, CompletableFuture<Void>> entry = iterator.next();
                CompletableFuture<Void> future = entry.getValue();

                if (future.isDone()) {
                    String taskKey = entry.getKey();
                    iterator.remove();
                    cancellationFlags.remove(taskKey);
                    log.trace("{} Cleaned up completed task: {}", AppConst.PREFIX_LOG, taskKey);
                }
            }
        }
    }
}
