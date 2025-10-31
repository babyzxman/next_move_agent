package org.gable.blendata.nextmove.service.dto;

import lombok.*;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ReconcileInfoDTO {
    private String appId;
    private String taskId;
    private Long transferHistoryId;
    private String createDate;
    private String srcAbsoluteFilePathStr;
    private String destAbsoluteFilePathStr;
    private String destFilePathStr;
    private Long fileSizeInBytes;
    private Long processTimeInMilliseconds;
    private Integer retry;
    private String status;
    private String errorNo;
    private String errMsg;

}
