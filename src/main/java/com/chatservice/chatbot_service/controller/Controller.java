package com.chatservice.chatbot_service.controller;

import com.chatservice.chatbot_service.chatbot.ChatbotConnector;
import com.chatservice.chatbot_service.chatbot.OpenaiConnector;
import com.chatservice.chatbot_service.chatbot.ModelConnector;
import com.chatservice.chatbot_service.constants.AppConstants;
import com.chatservice.chatbot_service.model.Prompt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("chatbot")
@CrossOrigin("*")
public class Controller {
    private ModelConnector model; //models cannot store previous messages and are simpler to implement.
    private ChatbotConnector chatbot; //chatbots can store previous messages to hold a conversation but require extra steps to implement.

    @PostMapping()
    public String ChatbotPrompt(@RequestBody Prompt prompt){
        return model.Prompt(prompt.getPrompt());
    }

    @PostMapping("/thread/{threadId}")
    public String ChatbotMessageThread(@PathVariable String threadId,@RequestBody Prompt prompt){
        return chatbot.PromptThread(prompt.getPrompt(), threadId);
    }

    @GetMapping()
    public String ChatbotHello(){
        return AppConstants.chatbotHello;
    }

    @GetMapping("/thread")
    public String ChatbotCreateThread(){ return chatbot.CreateThread();}

    @GetMapping("/thread/{threadId}")
    public String ChatbotCreateThread(@PathVariable String threadId){ return chatbot.GetThreadResponse(threadId);}

    public Controller(){
        model = new OpenaiConnector();
        chatbot = (OpenaiConnector)model;
    }
}
