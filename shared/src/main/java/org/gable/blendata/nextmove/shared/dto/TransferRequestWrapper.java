package org.gable.blendata.nextmove.shared.dto;

import lombok.*;
import org.gable.blendata.nextmove.shared.constant.TaskConst;
import org.gable.blendata.nextmove.shared.constant.TaskConst.SourceType;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class TransferRequestWrapper {
    private String taskId;
    private String clientId;
    private List<String> filePathStrs;
    private String sourceRootPathStr;
    private String destinationRootPathStr;
    private String destinationCtrlRootPathStr;
    @Builder.Default
    private String moveType = TaskConst.MoveType.COPY.name();
    private Long fileSize;
    @Builder.Default
    private Long checkFileDelaySeconds = 30l;
    @Builder.Default
    private Integer retry = 0;
    private boolean createTargetZipBaseDir;
    private boolean notExtract;
    private boolean overwrite;
    private boolean checkFileSize;
    private Map<String, Object> sourceProperties;
    private SourceType sourceType;
    private String host;    //...SFTP
    private List<String> ctrlExtensions;

}
