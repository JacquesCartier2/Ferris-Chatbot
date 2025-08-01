package com.chatservice.chatbot_service.chatbot;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class OpenaiConnector implements ModelConnector, ChatbotConnector {
	private final String key; //access key used for authorization
	private final String responseModel; //name of the GPT model to use for responses (responses are one-off prompts)
	private final String responseEndpoint; //URL of the web endpoint where you can access the response model
	private final String threadEndpoint; //URL of the web endpoint where you can access threads (conversations with AI)
	private final String assistantId; //id of the openai assistant that will be ran on all created threads.
	
	public OpenaiConnector() {
		this.key = System.getenv("openai.key");
		this.responseModel = System.getenv("openai.responseModel");
		this.responseEndpoint = System.getenv("openai.responseEndpoint");
		this.threadEndpoint = System.getenv("openai.threadEndpoint");
		this.assistantId = System.getenv("openai.assistantId");
	}
	
	public String Prompt(String prompt) {
		Error responseError = VerifyThreadConfig();
		if(responseError != null){
			return responseError.getMessage();
		}

		try {
			HttpClient client = HttpClient.newHttpClient();

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(responseEndpoint))
					.header("Authorization", "Bearer " + key)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString("{\"model\": \"" + responseModel + "\", \"messages\": [{\"role\": \"user\", \"content\": \"" + prompt + "\"}]}"))
					.build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			if(response.statusCode() >= 200 && response.statusCode() < 300){
				return extractValueFromJSONResponse("content", response.body());
			}else{
				return "Failed to prompt openai, error code " + response.statusCode() + ": \n" + response.body();
			}
	       } catch (Exception e) {
	           throw new RuntimeException(e);
	       }
	}

	//threads represent a saved set of messages between a user and an assistant. You must create a thread to start a conversation with a chatbot.
	@Override
	public String CreateThread() {
		Error threadError = VerifyThreadConfig();
		if(threadError != null){
			return threadError.getMessage();
		}
		try {
			HttpClient client = HttpClient.newHttpClient();

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(threadEndpoint))
					.header("Authorization", "Bearer " + key)
					.header("Content-Type", "application/json")
					.header("OpenAI-Beta", "assistants=v2")
					.POST(HttpRequest.BodyPublishers.ofString(""))
					.build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			if(response.statusCode() >= 200 && response.statusCode() < 300){
				return extractValueFromJSONResponse("id",response.body());

			}else{
				return "Failed to create thread, error code " + response.statusCode() + ": \n" + response.body();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	//assistants are AI tools that may have conversations on threads, and runs are a connection between an assistant and a thread.
	private boolean RunAssistantOnThread(String assistId, String threadId){
		Error threadError = VerifyThreadConfig();
		if(threadError != null){
			return false;
		}
		try {
			HttpClient client = HttpClient.newHttpClient();

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(threadEndpoint + "/" + threadId + "/runs"))
					.header("Authorization", "Bearer " + key)
					.header("Content-Type", "application/json")
					.header("OpenAI-Beta", "assistants=v2")
					.POST(HttpRequest.BodyPublishers.ofString("{\"assistant_id\": \"" + assistId + "\"}"))
					.build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			if(response.statusCode() >= 200 && response.statusCode() < 300){
				return true;
			}else{
				System.out.println("Failed to run assistant on thread, error code " + response.statusCode() + ": \n" + response.body());
				return false;
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	//once you have created a thread, you may send messages to it. This will respond with a confirmation message.
	@Override
	public String MessageThread(String message, String threadId) {
		Error threadError = VerifyThreadConfig();
		if(threadError != null){
			return threadError.getMessage();
		}
		try {
			HttpClient client = HttpClient.newHttpClient();

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(threadEndpoint + "/" + threadId + "/messages"))
					.header("Authorization", "Bearer " + key)
					.header("Content-Type", "application/json")
					.header("OpenAI-Beta", "assistants=v2")
					.POST(HttpRequest.BodyPublishers.ofString("{\"role\": \"user\", \"content\": \"" + message + "\"}"))
					.build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			if(response.statusCode() >= 200 && response.statusCode() < 300){
				boolean runResult = RunAssistantOnThread(assistantId, threadId); //add the assistant to the thread.
				if(runResult){
					return response.body();
				}else{
					return "Could not run assistant on thread.";
				}
			}else{
				return "Failed to message thread, error code " + response.statusCode() + ": \n" + response.body();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	//this will retrieve the last message of a thread. If an assistant is running on it, the last message should be the assistant response.
	@Override
	public String GetThreadResponse(String threadId) {
		Error threadError = VerifyThreadConfig();
		if(threadError != null){
			return threadError.getMessage();
		}
		try {
			HttpClient client = HttpClient.newHttpClient();

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(threadEndpoint + "/" + threadId + "/messages"))
					.header("Authorization", "Bearer " + key)
					.header("Content-Type", "application/json")
					.header("OpenAI-Beta", "assistants=v2")
					.build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			if(response.statusCode() >= 200 && response.statusCode() < 300){
				return response.body();
			}else{
				return "Failed to get thread response, error code " + response.statusCode() + ": \n" + response.body();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	//returns null if env variable are configured for using responses, returns an error if not.
	private Error VerifyResponseConfig(){
		if(key == null){
			return new Error("OpenAI key not configured on server.");
		}
		if(responseModel == null){
			return new Error("Response model not configured on server.");
		}
		if(responseEndpoint == null){
			return new Error("Response endpoint not configured on server.");
		}
		return null;
	}

	//returns null if env variable are configured for using threads, returns an error if not.
	private Error VerifyThreadConfig(){
		if(key == null){
			return new Error("OpenAI key not configured on server.");
		}
		if(threadEndpoint == null){
			return new Error("Thread endpoint not configured on server.");
		}
		return null;
	}
	
	//take a JSON response and return the value of a certain parameter.
	private String extractValueFromJSONResponse(String parameter, String response) {
       int start = response.indexOf(parameter) + parameter.length() + 4;

       int end = response.indexOf("\"", start);

       return response.substring(start, end);
   }
}
