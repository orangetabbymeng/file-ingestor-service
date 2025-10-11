package com.sulaksono.fileingestorservice.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Centralised REST‐error handling.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /* --------------------------------------------------------------------- */
    /*  1. File too large                                                    */
    /* --------------------------------------------------------------------- */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorBody> handleMaxSize(MaxUploadSizeExceededException ex) {
        log.warn("Rejected upload – size exceeds configured limit", ex);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorBody("File too large – limit is 10 MB"));
    }

    /* --------------------------------------------------------------------- */
    /*  2. Multipart part missing                                            */
    /* --------------------------------------------------------------------- */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorBody> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorBody("Required part '" + ex.getRequestPartName() + "' is missing"));
    }

    /* --------------------------------------------------------------------- */
    /* 3. Fallback – log and respond                                         */
    /* --------------------------------------------------------------------- */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleGeneric(Exception ex) {
        log.error("Unhandled controller exception", ex);
        return ResponseEntity.internalServerError()
                .body(new ErrorBody("Internal error"));
    }

    /* record DTO */
    public record ErrorBody(String message) { }
}