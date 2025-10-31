package org.gable.blendata.nextmove.shared.exception;

public class DuplicateException extends RuntimeException {
    public DuplicateException(String message, Throwable e){
        super(message, e);
    }

    public DuplicateException(String message){
        super(message);
    }
}
