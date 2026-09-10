package com.shreyas.url_shortner.url.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", (status instanceof HttpStatus httpStatus) ? httpStatus.getReasonPhrase() : null);
        body.put("message", ex.getReason());
        body.put("timestamp", Instant.now().toString());

        return new ResponseEntity<>(body, HttpStatusCode.valueOf(status.value()));
    }
}