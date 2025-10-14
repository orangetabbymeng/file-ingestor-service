package com.sulaksono.fileingestorservice.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Centralised REST‐error handling.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /* ---------------------------------------------------------------
       1. File too large
       ------------------------------------------------------------- */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorBody> handleMaxSize() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorBody("File too large – limit is 10 MB"));
    }

    /* ---------------------------------------------------------------
       2. Missing multipart part
       ------------------------------------------------------------- */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorBody> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorBody("Required part '" + ex.getRequestPartName() + "' is missing"));
    }

    /* ---------------------------------------------------------------
       3. Static resource not found (favicon, root, etc.) → 404
       ------------------------------------------------------------- */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource(NoResourceFoundException ex) {
        log.debug("Static resource not found: {}", ex.getResourcePath());
        return ResponseEntity.notFound().build();
    }

    /* ---------------------------------------------------------------
       4. Fallback – unexpected controller exceptions
       ------------------------------------------------------------- */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> handleGeneric(Exception ex) {
        log.error("Unhandled controller exception", ex);
        return ResponseEntity.internalServerError()
                .body(new ErrorBody("Internal error"));
    }

    public record ErrorBody(String message) { }
}