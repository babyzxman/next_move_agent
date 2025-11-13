package org.gable.blendata.nextmove.service.service;

import lombok.RequiredArgsConstructor;
import org.apache.hadoop.fs.Path;
import org.gable.blendata.nextmove.service.adapter.FileSystemAdapter;
import org.gable.blendata.nextmove.service.config.AppConfig;
import org.gable.blendata.nextmove.service.repo.TransferHistoryRepository;
import org.gable.blendata.nextmove.shared.constant.FileStatus;
import org.gable.blendata.nextmove.shared.dto.FileInfoDTO;
import org.gable.blendata.nextmove.shared.dto.TransferRequestWrapper;
import org.gable.blendata.nextmove.shared.entity.TransferHistory;
import org.gable.blendata.nextmove.shared.util.DateUtil;
import org.gable.blendata.nextmove.shared.util.FilePathUtil;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransferHistoryService extends GenericService<TransferHistory, Long> {

    private final AppConfig appConfig;
    private final TransferHistoryRepository transferHistoryRepository;

    public Optional<TransferHistory> findByRootPathAndFilePath(String rootPath, String filePathStr){
        List<TransferHistory> transferHistories = transferHistoryRepository.findBySourceRootPathAndFilePath(rootPath, filePathStr);
        if(transferHistories.size() >0){
            return Optional.ofNullable(transferHistories.get(0));
        }
        return Optional.empty();
    }

    public List<TransferHistory> findByIdIn(List<Long> transferHistoryIds){
        return transferHistoryRepository.findByIdIn(transferHistoryIds);
    }

    @Transactional
    public List<TransferHistory> saveProcessing(FileSystemAdapter adapter, TransferRequestWrapper transferRequestWrapper) {
        List<TransferHistory> transferHistories = new ArrayList<>();
        for (String filePathStr : transferRequestWrapper.getFilePathStrs()) {
            String actualSourceRootPath = transferRequestWrapper.getSourceRootPathStr().contains("*")?
                    FilePathUtil.extractMatchingPrefix(transferRequestWrapper.getSourceRootPathStr(), filePathStr)
                    : transferRequestWrapper.getSourceRootPathStr();
            FileInfoDTO srcFile = adapter.getSourceFileInfo(filePathStr, transferRequestWrapper.getSourceRootPathStr());
            TransferHistory transferHistory = save(TransferHistory.builder()
                    .appId(appConfig.getAppId())
                    .taskId(transferRequestWrapper.getTaskId())
                    .serviceId(appConfig.getServiceId())
                    .filePath(filePathStr)
                    .host(transferRequestWrapper.getHost())
                    .sourceType(transferRequestWrapper.getSourceType().name())
                    .sourceRootPath(new Path(transferRequestWrapper.getSourceRootPathStr()).toString())
                    .actualSourceRootPath(actualSourceRootPath)
                    .status(FileStatus.PROCESSING.name())
                    .createdDate(DateUtil.getCurrentDateWithTime())
                    .createdBy(appConfig.getAppId())
                    .fileModifiedTime(srcFile.getModifyTime())
                    .build()
            );
            transferHistories.add(transferHistory);
        }
        return transferHistories;
    }

}
