package org.gable.blendata.nextmove.client.dto;

public interface TransferHistoryView {

    String getTaskId();

    String getFilePath();

    String getDestination();

    String getStatus();

    Long getProcessTime();

    Long getFileSize();

    String getErrorNo();
}
