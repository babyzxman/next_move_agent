package org.gable.blendata.nextmove.client.dto;

import lombok.*;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.gable.blendata.nextmove.shared.constant.TaskConst.MoveType;
import org.gable.blendata.nextmove.shared.constant.TaskConst.TaskType;
import org.gable.blendata.nextmove.shared.constant.TaskConst.SourceType;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
@ToString
public class TaskDTO implements Serializable {
    private String id;
    private static final long serialVersionUID = -20240917153501L;
    private SourceType sourceType;
    private RootPath rootPath;
    private TaskType type;
    private MoveType moveType;
    private Integer filesPerRound;
    private String afterDate;
    private Integer beforeCurrentDateInHours;
    private Integer beforeCurrentDateInDays;
    private Long checkFileDelaySeconds;
    private String cronExpression;
    private List<String> ctrlExtensions;
    private Integer retry;
    private List<String> srcExtensions;
    private List<String> wildcardPatterns;
    private boolean checkFileSize;
    private boolean overwrite;
    private Compression compression;
    private String sourceFile;
    private Map<String, Object> sourceProperties;
    private List<String> ctrlFilePatterns;
    private Integer filePartitionDate;
    private Boolean usedCheckpoint = false;
    private boolean downloadToTmpBeforeUpload;
    private Timestamp modifiedTime;

    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    @ToString
    public static class RootPath implements Serializable{
        private static final long serialVersionUID = -20240917153502L;
        private String source;
        private String destination;
        private String ctrlPath;
        private String ctrlDestPath;
    }
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    @ToString
    public static class Compression implements Serializable{
        private static final long serialVersionUID = -20250222194202L;
        private boolean notExtract;
        private boolean createTargetDirectory;
    }

}
