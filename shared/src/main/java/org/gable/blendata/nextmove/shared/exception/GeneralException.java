package org.gable.blendata.nextmove.shared.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
public class GeneralException extends RuntimeException {
    private final int statusCode;

    public GeneralException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

}
