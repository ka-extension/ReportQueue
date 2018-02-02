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

public class ProgramDataInserter extends DataInserter {
	private static String programDataEndpoint = "https://www.khanacademy.org/api/labs/scratchpads/";

	Pattern programIdRegex = Pattern.compile("^\\d{10,30}$");

	public ProgramDataInserter(String appName, String spreadsheetId, OAuth10aService kaservice, File googleClientSecret,
			File googleCredentials) {
		super(appName, spreadsheetId, kaservice, googleClientSecret, googleCredentials);
	}

	@Override
	protected boolean validateId(String id) {
		return programIdRegex.matcher(id).find();
	}

	@Override
	protected boolean isModifiedId(String id, String modifiedId) {
		return id != null && modifiedId != null && modifiedId.split("/").length == 6
				&& id.equals(modifiedId.split("/")[5]);
	}

	@Override
	protected String getInsertId(String id) throws Exception {
		URL KAProgramAPIEndpoint = new URL(programDataEndpoint + id);
		HttpURLConnection progCon = (HttpURLConnection) KAProgramAPIEndpoint.openConnection();
		progCon.setRequestMethod("GET");
		progCon.connect();
		InputStream returnedProgContent = progCon.getInputStream();
		JsonReader reader = Json.createReader(returnedProgContent);
		JsonObject programData = reader.readObject();
		reader.close();
		returnedProgContent.close();
		return programData.getString("url");
	}

	@Override
	protected String getType() {
		return "program";
	}

}
