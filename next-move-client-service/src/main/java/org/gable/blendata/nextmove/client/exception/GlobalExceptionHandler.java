package org.gable.blendata.nextmove.client.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.gable.blendata.nextmove.client.config.AppConfig;
import org.gable.blendata.nextmove.client.dto.ApiResponse;
import org.gable.blendata.nextmove.shared.constant.AppConst;
import org.gable.blendata.nextmove.shared.exception.BusinessException;
import org.gable.blendata.nextmove.shared.exception.GeneralException;
import org.gable.blendata.nextmove.shared.util.ErrorUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.servlet.http.HttpServletRequest;

@ControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final AppConfig appConfig;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error occurred: {} at {}", ex.getMessage(), request.getRequestURI(), ex);
        ApiResponse<Object> response = ApiResponse.error(String.format("An unexpected error occurred : %s(%s)"
                ,ex.getMessage(), ErrorUtil.getCauseClassInfo(ex.getStackTrace()))
                , HttpStatus.INTERNAL_SERVER_ERROR.value());
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode());
        ApiResponse<Object> response = ApiResponse.error(ex.getMessage(), ex.getStatusCode());
        return new ResponseEntity<>(response, status);
    }

    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(
            GeneralException e, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode());
        ApiResponse<Object> response = ApiResponse.error(e.getMessage(), e.getStatusCode());
        return new ResponseEntity<>(response, status);
    }
}
