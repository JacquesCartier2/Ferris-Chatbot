package com.chatservice.chatbot_service.exceptions;

public abstract class OpenAIException extends RuntimeException {
  protected String responseBody;

  public String getResponseBody(){
    return responseBody;
  }

  public void setResponseBody(String responseBody){
    this.responseBody = responseBody;
  }

  public OpenAIException(String message) {
    super(message);
  }

  public OpenAIException(String message, String responseBody) {
    super(message);
    this.responseBody = responseBody;
  }
}
