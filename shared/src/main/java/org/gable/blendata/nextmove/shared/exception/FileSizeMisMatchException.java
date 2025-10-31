package org.gable.blendata.nextmove.shared.exception;

import org.apache.hadoop.fs.Path;

public class FileSizeMisMatchException extends RuntimeException {
    private Path filePath;
    public FileSizeMisMatchException(Path filePath, String message, Throwable e){
        super(message, e);
        this.filePath = filePath;
    }

    public FileSizeMisMatchException(Path filePath, String message){
        super(message);
        this.filePath = filePath;
    }

    public Path getFilePath() {
        return filePath;
    }
}
