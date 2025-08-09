package com.chatservice.chatbot_service.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice //class can catch all exceptions from the application.
public class AppExceptionHandler {
    @ExceptionHandler(InvalidDataException.class)
    @ResponseStatus(code = HttpStatus.BAD_REQUEST)
    public ErrorMessage handleUnauthorized(InvalidDataException e) {
        return new ErrorMessage(400, e.getMessage());
    }

    @ExceptionHandler(OpenAITimeoutException.class)
    @ResponseStatus(code = HttpStatus.GATEWAY_TIMEOUT)
    public ErrorMessage handleUnauthorized(OpenAITimeoutException e) {
        return new ErrorMessage(504, e.getMessage(), e.getResponseBody());
    }

    @ExceptionHandler(OpenAIGenericException.class)
    @ResponseStatus(code = HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorMessage handleUnauthorized(OpenAIGenericException e) {
        return new ErrorMessage(500, e.getMessage(), e.getResponseBody());
    }

}
