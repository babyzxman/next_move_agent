package org.gable.blendata.nextmove.shared.exception;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message, Throwable e){
        super(message, e);
    }

    public NotFoundException(String message){
        super(message);
    }
}
