package com.app.streaming.advice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import com.app.streaming.model.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {
    // @ExceptionHandler(IllegalArgumentException.class)
    // public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
    //     IllegalArgumentException ex, WebRequest request
    // ) {
    //     ErrorResponse errorDetails = new ErrorResponse(
    //         HttpStatus.CONFLICT.value(),
    //         HttpStatus.CONFLICT.getReasonPhrase(),
    //         ex.getMessage()
    //     );

    //     return new ResponseEntity<>(errorDetails, HttpStatus.CONFLICT);
    // }
}
