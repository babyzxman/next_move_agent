package org.gable.blendata.nextmove.shared.dto;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class StopTaskRequestWrapper {
    private String taskId;
    private String clientId;
}
