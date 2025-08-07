package com.chatservice.chatbot_service.exceptions;

public class OpenAITimeoutException extends OpenAIException {
    public OpenAITimeoutException(String message) {
        super(message);
    }

    public OpenAITimeoutException(String message, String responseBody) {
      super(message);
      this.responseBody = responseBody;
    }

}
