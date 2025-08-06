package com.chatservice.chatbot_service.exceptions;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ErrorMessage {
    private Integer status;
    private String message;
}
