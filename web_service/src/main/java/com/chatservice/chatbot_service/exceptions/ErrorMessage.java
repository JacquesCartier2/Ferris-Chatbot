package com.chatservice.chatbot_service.exceptions;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ErrorMessage {
    private Integer status; //http status code
    private String message; //short user-viewable message
    private String detail; //detailed message meant for staff

    public ErrorMessage(Integer status, String message){
        this.status = status;
        this.message = message;
    }

}
