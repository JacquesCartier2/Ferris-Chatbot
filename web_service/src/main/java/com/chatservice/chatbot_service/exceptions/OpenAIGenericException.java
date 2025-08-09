package com.chatservice.chatbot_service.exceptions;

public class OpenAIGenericException extends OpenAIException {
    public OpenAIGenericException(String message) {
        super(message);
    }

    public OpenAIGenericException(String message, String responseBody) {
        super(message);
        this.responseBody = responseBody;
    }
}
