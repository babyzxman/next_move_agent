package org.gable.blendata.nextmove.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class CreateTaskResponse{
    private String taskId;
    private int numberOfFiles;
}
