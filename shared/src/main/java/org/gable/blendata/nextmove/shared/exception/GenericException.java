package org.gable.blendata.nextmove.shared.exception;

public class GenericException extends RuntimeException{
    public GenericException(String message, Throwable e){
        super(message, e);
    }

    public GenericException(String message){
        super(message);
    }
}
