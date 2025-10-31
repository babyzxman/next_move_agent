package org.gable.blendata.nextmove.client.dto;

import lombok.Data;
import org.gable.blendata.nextmove.shared.constant.FileStatus;

import java.util.Set;

@Data
public class TaskResponse {
    private String taskId;
    private Set<String> sourceFiles;
    private int numberOfFiles;
    @Data
    public static class SourceFile{
        private String fileName;
        private FileStatus status;
    }
}
