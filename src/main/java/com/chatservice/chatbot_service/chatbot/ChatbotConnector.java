package com.chatservice.chatbot_service.chatbot;

//chatbot connector is used for holding a conversation with an AI where the AI stores previous responses in a thread.
public interface ChatbotConnector {
    //create a chatbot thread that stores the entire conversation and return the thread id.
    public String CreateThread();

    //send a message to a specific thread and return a confirmation message.
    public String MessageThread(String message, String threadId);

    //return the last AI response from a thread.
    public String GetThreadResponse(String threadId);
}
