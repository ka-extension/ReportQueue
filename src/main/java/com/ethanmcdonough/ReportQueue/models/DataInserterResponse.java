package com.ethanmcdonough.ReportQueue.models;

import java.util.HashMap;
import javax.json.Json;
import javax.json.JsonBuilderFactory;

public class DataInserterResponse {
	private int code;
	private String message;
	private boolean success;
	private static JsonBuilderFactory factory = Json.createBuilderFactory(new HashMap<String, String>());

	public DataInserterResponse(int code, String message, boolean success) {
		this.code = code;
		this.message = message;
		this.success = success;
	}

	public int getResponseCode() {
		return code;
	}

	public String getMessage() {
		return message;
	}

	public boolean isSuccessful() {
		return success;
	}

	public String getBodyJSONString() {
		return factory.createObjectBuilder().add("message", message).add("success", success).build().toString();
	}
}
