package org.gable.blendata.nextmove.client.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.hadoop.fs.Path;
import org.gable.blendata.nextmove.client.dto.TransferHistoryView;
import org.gable.blendata.nextmove.client.dto.TaskResponse;
import org.gable.blendata.nextmove.client.mapper.TransferHistoryMapper;
import org.gable.blendata.nextmove.client.repo.TransferHistoryRepository;
import org.gable.blendata.nextmove.shared.constant.FileStatus;
import org.gable.blendata.nextmove.shared.constant.TaskConst;
import org.gable.blendata.nextmove.shared.constant.TaskConst.SourceType;
import org.gable.blendata.nextmove.shared.entity.TransferHistory;
import org.gable.blendata.nextmove.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransferHistoryService {

    private final TransferHistoryRepository transferHistoryRepository;
    private final TransferHistoryMapper transferHistoryMapper;

    public void deleteByCreatedDateLessThan(Date beforeDate) {
        transferHistoryRepository.deleteByCreatedDateLessThan(beforeDate);
    }

    public List<TransferHistoryView> getSuccessOrProcessingFiles(SourceType sourceType, String rootPath, String host){
        if(SourceType.HADOOP.equals(sourceType)){
            return transferHistoryRepository.findHadoopFilePathBySourceRootPathAndStatus(rootPath, Arrays.asList(FileStatus.SUCCESS.name(), FileStatus.PROCESSING.name()));
        } else if(SourceType.SFTP.equals(sourceType)){
            return transferHistoryRepository.findSftpFilePathBySourceRootPathAndStatus(rootPath, host, Arrays.asList(FileStatus.SUCCESS.name(), FileStatus.PROCESSING.name()));
        }
        return new ArrayList<>();
    }


    public List<TransferHistoryView> findTransferHistoryViewByTaskId(String taskId) {
        return transferHistoryRepository.findTransferHistoryViewByTaskId(taskId);
    }

    public List<TransferHistoryView> getProcessingFiles(SourceType sourceType, String rootPath, String host){
        if(SourceType.HADOOP.equals(sourceType)){
            return transferHistoryRepository.findHadoopFilePathBySourceRootPathAndStatus(rootPath, Arrays.asList(FileStatus.PROCESSING.name()));
        } else if(SourceType.SFTP.equals(sourceType)){
            return transferHistoryRepository.findSftpFilePathBySourceRootPathAndStatus(rootPath, host, Arrays.asList(FileStatus.PROCESSING.name()));
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
