package org.gable.blendata.nextmove.service.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.gable.blendata.nextmove.shared.constant.AppConst;
import org.gable.blendata.nextmove.shared.exception.FileSizeMisMatchException;

import java.io.IOException;

@Slf4j
public abstract class MoveService {

    protected static final int TASK_CANCELLATION_TIMEOUT_SECONDS = 5;
    protected static final long CLEANUP_INTERVAL_MS = 300000;

    protected void handleFileSizeMisMatchException(FileSystem fs, Exception e){
        if(e instanceof FileSizeMisMatchException){
            Path filePath = ((FileSizeMisMatchException) e).getFilePath();
            try {
                if(fs.exists(filePath)){
                    fs.delete(filePath, true);
                }
            } catch (IOException ex) {
                log.error("{} !!!Error (FileSizeMisMatch) cannot delete file {}", AppConst.PREFIX_LOG, filePath.toString());
            }
        }
    }
}
