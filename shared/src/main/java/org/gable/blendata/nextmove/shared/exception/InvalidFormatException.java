package org.gable.blendata.nextmove.shared.exception;

public class InvalidFormatException extends RuntimeException {
    public InvalidFormatException(String message, Throwable e){
        super(message, e);
    }

    public InvalidFormatException(String message){
        super(message);
    }
}
