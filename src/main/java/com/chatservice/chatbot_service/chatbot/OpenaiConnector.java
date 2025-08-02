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
				return ExtractValueFromJSONResponse("content", response.body());
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
				return ExtractValueFromJSONResponse("id",response.body());

			}else{
				return "Failed to create thread, error code " + response.statusCode() + ": \n" + response.body();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	//assistants are AI tools that may have conversations on threads, and runs are a connection between an assistant and a thread.
	private String RunAssistantOnThread(String assistId, String threadId){
		Error threadError = VerifyThreadConfig();
		if(threadError != null){
			return threadError.getMessage();
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
				return response.body();
			}else{
				return "Failed to run assistant on thread, error code " + response.statusCode() + ": \n" + response.body();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	//once you have created a thread, you may send messages to it. This will respond with a confirmation message.
	@Override
	public String MessageThread(String message, String threadId) {
		Error threadError = VerifyResponseConfig();
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
				return RunAssistantOnThread(assistantId, threadId); //add the assistant to the thread.
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
				/*response contains the entire thread of messages in the body, so we need to extract the topmost
				(most recent) one and return the value*/
				String JSONObject = ExtractJSONObjectFromList(1,1,response.body());
				if(JSONObject != null){
					return ExtractValueFromJSONResponse("value", JSONObject);
				}else{
					return "Failed to extract message from thread.";
				}
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
	private String ExtractValueFromJSONResponse(String parameter, String response) {
       int start = response.indexOf(parameter) + parameter.length() + 4;

       int end = response.indexOf("\"", start);

       return response.substring(start, end);
   }

   //returns a JSON object from a list, with both the list and returned object represented as strings.
   //the list goes from top line to bottom, the itemNumber determines which item is extracted, with number 1 meaning the topmost item.
   //itemDepth is used for lists contained in other objects. The itemDepth determines how many outer braces are ignored when selecting the object.
   private String ExtractJSONObjectFromList(int itemNumber, int itemDepth, String json){
		if(itemNumber < 1){
			System.out.println("Extract JSON object failure: item number may not be lower than 1.");
			return null;
		}
	    if(itemDepth < 0){
		   System.out.println("Extract JSON object failure: item depth may not be lower than 0.");
		   return null;
	    }

		int ignoredOpeningBraces = 0;
		int unclosedBraces = 0;
		int objectsFound = 0;
		Integer objectStart = null;
		Integer objectEnd = null;

		for(int i = 0; i < json.length(); i++){
			char currentChar = json.charAt(i);

			if(currentChar == '{'){
				//if we have ignored less braces than the item depth, ignore this opening brace.
				if(ignoredOpeningBraces < itemDepth){
					ignoredOpeningBraces++;
				}
				//if the brace within the depth we want, check to see if it is the object we want.
				else{
					unclosedBraces++;

					//having one brace unclosed indicates the start of an object at the depth we want.
					if(unclosedBraces == 1){
						objectsFound++;
						if(objectsFound == itemNumber){
							objectStart = i;
						}
					}
				}
			}
			else if(currentChar == '}'){
				/*if we encounter a closing bracket before finding an opening bracket at the depth we want, then no
				object exists in the itemNumber position at the itemDepth. */
				if(unclosedBraces < 1){
					System.out.println("No JSON object found at the desired position.");
					return null;
				}

				unclosedBraces--;

				if(objectStart != null && unclosedBraces == 0){
					objectEnd = i;
					break;
				}

			}
		}

		if(objectStart == null || objectEnd == null){
			System.out.println("No JSON object found at the desired position.");
			return null;
		}
		return json.substring(objectStart, objectEnd + 1);
   }
}
