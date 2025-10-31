package org.gable.blendata.nextmove.shared.exception;

import org.apache.hadoop.fs.Path;

public class TaskCancelledException extends RuntimeException {
    private Path filePath;

    public TaskCancelledException(String message){
        super(message);
    }
    public TaskCancelledException(Path filePath, String message, Throwable e){
        super(message, e);
        this.filePath = filePath;
    }

    public TaskCancelledException(Path filePath, String message){
        super(message);
        this.filePath = filePath;
    }

    public Path getFilePath() {
        return filePath;
    }
}
