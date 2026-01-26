package org.gable.blendata.nextmove.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.gable.blendata.nextmove.shared.entity.TransferHistory;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
public class CreateTaskResponse{
    private String taskId;
    private int numberOfFiles;
    private Map<String, TransferHistory> transferHistoryMap;
}
