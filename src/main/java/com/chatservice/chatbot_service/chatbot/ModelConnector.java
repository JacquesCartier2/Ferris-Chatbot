package com.chatservice.chatbot_service.chatbot;

//model connector is used for prompting models that do not store previous messages. Every prompt is a one-off exchange.
public interface ModelConnector {
	//send the entered prompt to a chatbot and return the response. 
	public String Prompt(String prompt);
}
