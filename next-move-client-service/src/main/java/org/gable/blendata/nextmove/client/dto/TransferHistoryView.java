package org.gable.blendata.nextmove.client.dto;

import java.sql.Timestamp;
import java.time.LocalDateTime;

public interface TransferHistoryView {

    String getTaskId();

    String getFilePath();

    String getDestination();

    String getStatus();

    Long getProcessTime();

    Long getFileSize();

    String getErrorNo();

    Timestamp getFileModifiedTime();

    String getErrorMsg();
}
