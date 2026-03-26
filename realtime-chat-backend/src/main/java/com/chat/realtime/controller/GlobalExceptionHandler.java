package com.chat.realtime.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex) {
        String message = ex.getMessage();
        HttpStatus status;

        if (message != null && message.toLowerCase().contains("already exists")) {
            status = HttpStatus.CONFLICT; // 409
        } else if (message != null && (message.toLowerCase().contains("not found")
                || message.toLowerCase().contains("invalid password"))) {
            status = HttpStatus.UNAUTHORIZED; // 401
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR; // 500
        }

        return ResponseEntity.status(status).body(Map.of("error", message != null ? message : "Unknown error"));
    }
}
