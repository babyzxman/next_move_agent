package org.gable.blendata.nextmove.client.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.hadoop.fs.Path;
import org.gable.blendata.nextmove.client.config.FileListConfig;
import org.gable.blendata.nextmove.client.dto.TransferHistoryView;
import org.gable.blendata.nextmove.client.dto.TaskResponse;
import org.gable.blendata.nextmove.client.mapper.TransferHistoryMapper;
import org.gable.blendata.nextmove.client.repo.TransferHistoryRepository;
import org.gable.blendata.nextmove.shared.constant.FileStatus;
import org.gable.blendata.nextmove.shared.constant.TaskConst;
import org.gable.blendata.nextmove.shared.constant.TaskConst.SourceType;
import org.gable.blendata.nextmove.shared.entity.TransferHistory;
import org.gable.blendata.nextmove.shared.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransferHistoryService {

    @Autowired
    private FileListConfig fileListConfig;

    private final TransferHistoryRepository transferHistoryRepository;
    private final TransferHistoryMapper transferHistoryMapper;

    private DateTimeFormatter localDateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    public void deleteByCreatedDateLessThan(Date beforeDate) {
        transferHistoryRepository.deleteByCreatedDateLessThan(beforeDate);
    }

    public List<TransferHistoryView> getSuccessOrProcessingFiles(SourceType sourceType, String rootPath, String host,
                                                                 String destinationRootPath,Integer filePartitionDate){
        Long filePartitionCountDate = fileListConfig.getFileModifyTimePartition();
        if(SourceType.HADOOP.equals(sourceType)){
            return transferHistoryRepository.findHadoopFilePathBySourceRootPathAndStatus(
                    rootPath, Arrays.asList(FileStatus.SUCCESS.name(),
                            FileStatus.PROCESSING.name()),
                    filePartitionDate);
        } else if(SourceType.SFTP.equals(sourceType)){
            return transferHistoryRepository.findSftpFilePathBySourceRootPathAndStatus(
                    rootPath, host, Arrays.asList(FileStatus.SUCCESS.name(),
                            FileStatus.PROCESSING.name()),filePartitionDate);
        }
        return new ArrayList<>();
    }


    public List<TransferHistoryView> findTransferHistoryViewByTaskId(String taskId,Integer page, Integer pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        return transferHistoryRepository.findTransferHistoryViewByTaskId(taskId,pageable).getContent();
    }

    public Integer findTransferHistoryViewPageSizeByTaskId(String taskId, Integer pageSize) {
        Pageable pageable = PageRequest.of(0, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        return transferHistoryRepository.findTransferHistoryViewByTaskId(taskId,pageable).getTotalPages();
    }

    public Boolean findTransferHistoryStatus(String taskId) {
        return transferHistoryRepository.existsProcessingByTaskId(taskId);
    }

    public List<TransferHistoryView> getProcessingFiles(SourceType sourceType,
                                                        String rootPath,
                                                        String host,String destPath,Integer filePartitionDate){
        if(SourceType.HADOOP.equals(sourceType)){
            return transferHistoryRepository.findHadoopFilePathBySourceRootPathAndStatus(
                    rootPath, Collections.singletonList(FileStatus.PROCESSING.name()),filePartitionDate);
        } else if(SourceType.SFTP.equals(sourceType)){
            return transferHistoryRepository.
                    findSftpFilePathBySourceRootPathAndStatus(
                            rootPath, host, Collections.singletonList(
                                    FileStatus.PROCESSING.name()),filePartitionDate);
        }
        return new ArrayList<>();
    }

    public TaskResponse getTaskInfoByTaskId(String taskId){
        List<TransferHistory> transferHistories = transferHistoryRepository.findByTaskId(taskId);
        if(CollectionUtils.isEmpty(transferHistories)){
            throw new NotFoundException("Task ID not found: " + taskId);
        }
        return transferHistoryMapper.mapToTaskResponseWithSourceFiles(transferHistories);

    }
}
