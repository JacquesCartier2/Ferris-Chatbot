package com.chatservice.chatbot_service.chatbot;

import com.chatservice.chatbot_service.exceptions.OpenAIGenericException;
import com.chatservice.chatbot_service.exceptions.OpenAITimeoutException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class OpenaiConnector implements ModelConnector, ChatbotConnector {
	private final String key; //access key used for authorization
	private final String responseModel; //name of the GPT model to use for responses (responses are one-off prompts)
	private final String responseEndpoint; //URL of the web endpoint where you can access the response model
	private final String threadEndpoint; //URL of the web endpoint where you can access threads (conversations with AI)
	private final String assistantId; //id of the openai assistant that will be ran on all created threads.
	private final long runMaxSeconds = 15L; //amount of time to wait for a run to complete before throwing a timeout exception.
	private final long runIntervalSeconds = 1L; //amount of time to between run status checks.

	public OpenaiConnector() {
		this.key = System.getenv("openai.key");
		this.responseModel = System.getenv("openai.responseModel");
		this.responseEndpoint = System.getenv("openai.responseEndpoint");
		this.threadEndpoint = System.getenv("openai.threadEndpoint");
		this.assistantId = System.getenv("openai.assistantId");
	}
	
	public String Prompt(String prompt) {
		VerifyResponseConfig();
		HttpResponse<String> response = null;
		try {
			HttpClient client = HttpClient.newHttpClient();

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(responseEndpoint))
					.header("Authorization", "Bearer " + key)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString("{\"model\": \"" + responseModel + "\", \"messages\": [{\"role\": \"user\", \"content\": \"" + prompt + "\"}]}"))
					.build();

			response = client.send(request, HttpResponse.BodyHandlers.ofString());

			if(response.statusCode() >= 200 && response.statusCode() < 300){
				return ExtractValueFromJSONResponse("content", response.body());
			}else{
				throw new Exception("Failed to prompt OpenAI.");
			}
	       } catch (Exception e) {
			if(response == null){
				throw new OpenAIGenericException(e.getMessage());
			}else{
				throw new OpenAIGenericException(e.getMessage(), response.body());
			}

		}
	}

	//threads represent a saved set of messages between a user and an assistant. You must create a thread to start a conversation with a chatbot.
	@Override
	public String CreateThread() {
		VerifyThreadConfig();
		HttpResponse<String> response = null;
		try {
			HttpClient client = HttpClient.newHttpClient();

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(threadEndpoint))
					.header("Authorization", "Bearer " + key)
					.header("Content-Type", "application/json")
					.header("OpenAI-Beta", "assistants=v2")
					.POST(HttpRequest.BodyPublishers.ofString(""))
					.build();

			response = client.send(request, HttpResponse.BodyHandlers.ofString());
			System.out.println(response.body());

			if(response.statusCode() >= 200 && response.statusCode() < 300){
				return ExtractValueFromJSONResponse("id",response.body());
			}else{
				throw new Exception("Failed to create OpenAI thread.");
			}
		} catch (Exception e) {
			if(response == null){
				throw new OpenAIGenericException(e.getMessage());
			}else{
				throw new OpenAIGenericException(e.getMessage(), response.body());
			}
		}
	}

	//assistants are AI tools that may have conversations on threads, and runs are a connection between an assistant and a thread.
	private String RunAssistantOnThread(String assistId, String threadId){
		VerifyThreadConfig();
		HttpResponse<String> response = null;
		try {
			HttpClient client = HttpClient.newHttpClient();

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(threadEndpoint + "/" + threadId + "/runs"))
					.header("Authorization", "Bearer " + key)
					.header("Content-Type", "application/json")
					.header("OpenAI-Beta", "assistants=v2")
					.POST(HttpRequest.BodyPublishers.ofString("{\"assistant_id\": \"" + assistId + "\"}"))
					.build();

			response = client.send(request, HttpResponse.BodyHandlers.ofString());

			if(response.statusCode() >= 200 && response.statusCode() < 300){
				return response.body();
			}else{
				throw new Exception("Failed to run assistant on thread.");
			}
		} catch (Exception e) {
			if(response == null){
				throw new OpenAIGenericException(e.getMessage());
			}else{
				throw new OpenAIGenericException(e.getMessage(), response.body());
			}
		}
	}

	//once you have created a thread, you may send messages to it. Sending a message to openai will respond with the message object.
	//we will also create a "run" ordering an assistant to add a new message to the thread, and wait for it to finish.
	//then, we will retrieve all messages from the thread, extract the latest one, and return its value.
	//in short, this function sends a message to the chatbot and returns the response.
	@Override
	public String PromptThread(String message, String threadId) {
		VerifyThreadConfig();

		MessageThread(message, threadId);

		//have the assistant add a message to the thread, store the id of the assistant's execution on the thread.
		String runId = ExtractValueFromJSONResponse("id", RunAssistantOnThread(assistantId, threadId));;

		//returns true when the run is finished, false if not finished within maximum time.
		boolean finishedInTime = ConfirmRunCompletion(threadId, runId, 1L, 10f);

		if(finishedInTime){
			return GetThreadResponse(threadId);
		}else{
			throw new OpenAITimeoutException("Assistant run timed out.", "Time: " + runMaxSeconds + ", Interval: " + runIntervalSeconds);
		}
	}

	private void MessageThread(String message, String threadId){
		VerifyThreadConfig();
		HttpResponse<String> response = null;
		try {
			HttpClient client = HttpClient.newHttpClient();

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(threadEndpoint + "/" + threadId + "/messages"))
					.header("Authorization", "Bearer " + key)
					.header("Content-Type", "application/json")
					.header("OpenAI-Beta", "assistants=v2")
					.POST(HttpRequest.BodyPublishers.ofString("{\"role\": \"user\", \"content\": \"" + message + "\"}"))
					.build();

			response = client.send(request, HttpResponse.BodyHandlers.ofString());

			if(response.statusCode() >= 200 && response.statusCode() < 300){

			}else{
				throw new Exception("Failed to message OpenAI thread.");
			}
		} catch (Exception e) {
			if(response == null){
				throw new OpenAIGenericException(e.getMessage());
			}else{
				throw new OpenAIGenericException(e.getMessage(), response.body());
			}
		}
	}

	//this will retrieve the last message of a thread. If an assistant is running on it, the last message should be the assistant response.
	@Override
	public String GetThreadResponse(String threadId) {
		VerifyThreadConfig();
		HttpResponse<String> response = null;
		try {
			HttpClient client = HttpClient.newHttpClient();

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(threadEndpoint + "/" + threadId + "/messages"))
					.header("Authorization", "Bearer " + key)
					.header("Content-Type", "application/json")
					.header("OpenAI-Beta", "assistants=v2")
					.build();

			response = client.send(request, HttpResponse.BodyHandlers.ofString());

			if(response.statusCode() >= 200 && response.statusCode() < 300){
				/*response contains the entire thread of messages in the body, so we need to extract the topmost
				(most recent) one and return the value*/
				String JSONObject = ExtractJSONObjectFromList(1,1,response.body());
				if(JSONObject != null){
					String value = ExtractValueFromJSONResponse("value", JSONObject);
					return CleanStringFormat(value);
				}else{
					throw new Exception("Failed to extract message from thread.");
				}
			}else{
				throw new Exception("Failed to get thread response");
			}
		} catch (Exception e) {
			if(response == null){
				throw new OpenAIGenericException(e.getMessage());
			}else{
				throw new OpenAIGenericException(e.getMessage(), response.body());
			}
		}
	}

	public String GetRunStatus(String threadId, String runId) {
		VerifyThreadConfig();
		HttpResponse<String> response = null;
		try {
			HttpClient client = HttpClient.newHttpClient();

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(threadEndpoint + "/" + threadId + "/runs/" + runId))
					.header("Authorization", "Bearer " + key)
					.header("OpenAI-Beta", "assistants=v2")
					.build();

			response = client.send(request, HttpResponse.BodyHandlers.ofString());

			if(response.statusCode() >= 200 && response.statusCode() < 300){
				return ExtractValueFromJSONResponse("status", response.body());
			}else{
				throw new Exception("Failed to get run.");
			}
		} catch (Exception e) {
			if(response == null){
				throw new OpenAIGenericException(e.getMessage());
			}else{
				throw new OpenAIGenericException(e.getMessage(), response.body());
			}
		}
	}

	//throws an error if any of the env variables required for using response models are not configured.
	private void VerifyResponseConfig(){
		if(key == null || key.isEmpty()){
			throw new OpenAIGenericException("OpenAI key not configured on server.", "An OpenAI access key must be configured in the server's environment variables to use this endpoint.");
		}
		if(responseModel == null || responseModel.isEmpty()){
			throw new OpenAIGenericException("OpenAI response model not configured on server.", "An OpenAI response model must be configured in the server's environment variables to use this endpoint.");
		}
		if(responseEndpoint == null || responseEndpoint.isEmpty()){
			throw new OpenAIGenericException("OpenAI response endpoint not configured on server.", "The OpenAI response API endpoint must be configured in the server's environment variables to use this endpoint.");
		}
	}

	//throws an error if any of the env variables required for using threads are not configured.
	private void VerifyThreadConfig(){
		if(key == null || key.isEmpty()){
			throw new OpenAIGenericException("OpenAI key not configured on server.", "An OpenAI access key must be configured in the server's environment variables to use this endpoint.");
		}
		if(threadEndpoint == null || threadEndpoint.isEmpty()){
			throw new OpenAIGenericException("OpenAI thread endpoint not configured on server.", "The OpenAI API thread endpoint must be configured in the server's environment variables to use this endpoint.");
		}
	}
	
	//take a JSON response and return the value of a certain parameter.
	private String ExtractValueFromJSONResponse(String property, String response) {
       int start = response.indexOf(property) + property.length() + 4;
	   Integer end = null;

	   //the backslash is an escape operator and any quotation preceded by a backslash is not the end.
	   boolean escape = false;
	   for(int i = start; i < response.length(); i++){
		   char currentChar = response.charAt(i);
		   if(currentChar == '"' && !escape){
			   end = i;
			   break;
		   }else{
			   escape = (currentChar == '\\');
		   }
	   }

	   if(end != null){
		   return response.substring(start, end);
	   }else {
		   return "Could not parse " + property + ".";
	   }
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

   //will check multiple times to see if a run is complete. Returns true if complete within max time, false otherwise.
   private boolean ConfirmRunCompletion(String threadId, String runId, long interval, float maxTime){
		long timeWaited = 0L;

		for(; timeWaited <= maxTime; timeWaited += interval){
            try {
				if(GetRunStatus(threadId, runId).equals("completed")){
					return true;
				}
                TimeUnit.SECONDS.sleep(interval);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

		return false;
   }

   //prepare json data from openai to be sent as a regular string.
   private String CleanStringFormat(String input){
	   HashMap<String,String> replacements = new HashMap<String,String>();
	   replacements.put("\\n", "\n");
	   replacements.put("\\\"", "\"");

	   String output = "" + input; //leave input unmodified.
	   for(String textToReplace : replacements.keySet()){
		   output = output.replace(textToReplace, replacements.get(textToReplace));
	   }
	   return output;
   }
}
