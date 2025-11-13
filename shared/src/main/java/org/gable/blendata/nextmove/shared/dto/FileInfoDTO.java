package org.gable.blendata.nextmove.shared.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfoDTO {
    private String rootPathStr;
    private String relativeFilePath;
    private String absoluteFilePath;
    private String fileName;
    private String extension;
    private Long size;
    private Timestamp modifyTime;

}
