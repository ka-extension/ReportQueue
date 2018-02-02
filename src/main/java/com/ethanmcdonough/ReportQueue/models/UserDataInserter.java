package com.ethanmcdonough.ReportQueue.models;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Pattern;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import com.github.scribejava.core.oauth.OAuth10aService;

public class UserDataInserter extends DataInserter {
	private final String profileDataEndpoint = "http://www.khanacademy.org/api/internal/user/profile?kaid=";

	Pattern kaidRegex = Pattern.compile("^kaid_\\d{20,40}$");

	public UserDataInserter(String appName, String spreadsheetId, OAuth10aService kaservice, File googleClientSecret,
			File googleCredentials) {
		super(appName, spreadsheetId, kaservice, googleClientSecret, googleCredentials);
	}

	@Override
	protected boolean validateId(String id) {
		return kaidRegex.matcher(id).find();
	}

	@Override
	protected boolean isModifiedId(String id, String modifiedId) {
		return id != null && modifiedId != null && modifiedId.split("/").length == 5
				&& id.equals(modifiedId.split("/")[4]);
	}

	@Override
	protected String getInsertId(String id) throws Exception {
		URL url = new URL(profileDataEndpoint + id);
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("GET");
		con.connect();
		InputStream returnedContent = con.getInputStream();
		JsonReader reader = Json.createReader(returnedContent);
		JsonObject userData = reader.readObject();
		reader.close();
		returnedContent.close();
		return String.format("https://www.khanacademy.org/profile/%s", userData.getString("kaid"));
	}

	@Override
	protected String getType() {
		return "user";
	}

}
