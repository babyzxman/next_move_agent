package org.gable.blendata.nextmove.service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gable.blendata.nextmove.service.config.AppConfig;
import org.gable.blendata.nextmove.service.dto.ReconcileInfoDTO;
import org.gable.blendata.nextmove.shared.constant.AppConst;
import org.gable.blendata.nextmove.shared.util.CsvUtil;
import org.gable.blendata.nextmove.shared.util.DateUtil;
import org.gable.blendata.nextmove.shared.util.ErrorUtil;
import org.gable.blendata.nextmove.shared.util.StringUtil;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Date;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReconcileLogService {

    private final AppConfig appConfig;

    protected void writeLogFile(List<ReconcileInfoDTO> reconcileInfos, String rootPathStr, String taskId) {
        try {
            String[] headers = new String[]{
                    "app id"
                    , "task id"
                    , "create date"
                    , "source file path"
                    , "destination file path"
                    , "file size"
                    , "status"
                    , "error no"
                    , "error message"
                    , "retry"
            };
            List<String[]> data = reconcileInfos.stream()
                    .map(reconcileInfo -> new String[]{appConfig.getAppId()
                            , taskId
                            , StringUtil.replaceValueWhenNull(reconcileInfo.getCreateDate(), "")
                            , StringUtil.replaceValueWhenNull(reconcileInfo.getSrcAbsoluteFilePathStr(), "")
                            , StringUtil.replaceValueWhenNull(reconcileInfo.getDestAbsoluteFilePathStr(), "")
                            , StringUtil.replaceValueWhenNull(reconcileInfo.getFileSizeInBytes(), "")
                            , StringUtil.replaceValueWhenNull(reconcileInfo.getStatus(), "")
                            , StringUtil.replaceValueWhenNull(reconcileInfo.getErrorNo(), "")
                            , StringUtil.replaceValueWhenNull(reconcileInfo.getErrMsg(), "")
                            , StringUtil.replaceValueWhenNull(reconcileInfo.getRetry(), "")
                    })
                    .collect(Collectors.toList());

            java.nio.file.Path rootDir = Paths.get(appConfig.getReconcileLogDir() + "/" + DateUtil.convertToString(Date.from(Instant.now()), DateUtil.YYYYMMDD));
            if (Files.notExists(rootDir)) {
                Files.createDirectories(rootDir);
            }
            //...count, create date time, srcpath, dest path, file size, process time, status, err no, err desc
            String filePath = rootDir + "/"
                    + appConfig.getAppId() + "_"
                    + DateUtil.convertToString(Date.from(Instant.now()), DateUtil.YYYYMMDDHHmmssSSS)
                    + ".csv";

            CsvUtil.writeToCsv(filePath, headers, data);
        } catch (Exception e) {
            String errorNo = ErrorUtil.generateErrorNo(appConfig.getAppId());
            log.error("{} !!!ErrorNo({}) : Cannot write reconcile log for root path '{}'", AppConst.PREFIX_LOG, errorNo, rootPathStr, e);
        }
    }
}
