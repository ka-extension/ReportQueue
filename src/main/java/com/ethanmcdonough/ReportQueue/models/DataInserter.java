package com.ethanmcdonough.ReportQueue.models;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import javax.servlet.http.HttpServletResponse;

import com.github.scribejava.core.model.OAuth1AccessToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Verb;
import com.github.scribejava.core.oauth.OAuth10aService;
import com.github.scribejava.core.model.Response;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;
import com.google.api.services.sheets.v4.Sheets;

public abstract class DataInserter {
	private File googleCredentials, googleClientSecret;
	private OAuth10aService kaservice;
	private static final List<String> googleScopes = Arrays.asList(SheetsScopes.SPREADSHEETS, SheetsScopes.DRIVE);
	private static final JsonFactory googleJsonFactory = JacksonFactory.getDefaultInstance();
	private static HttpTransport googleHttpTransport;
	private String appName, spreadsheetId;
	private static final SimpleDateFormat datef = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss zzz");

	private static final int userReasonCharCount = 500;

	private static final long userReportTimeMillis = minToMillis(60);
	private static final int userReportTimeCount = 5;

	protected static final Logger log = Logger.getLogger("log");

	static {
		datef.setTimeZone(TimeZone.getTimeZone("GMT"));
		try {
			googleHttpTransport = GoogleNetHttpTransport.newTrustedTransport();
		} catch (Exception e) {
			log.log(Level.SEVERE, "Could not initialize googleHttpTransport", e);
		}
	}

	private static long minToMillis(int minutes) {
		return minutes * 60000L;
	}

	public DataInserter(String appName, String spreadsheetId, OAuth10aService kaservice, File googleClientSecret,
			File googleCredentials) {
		this.appName = appName;
		this.spreadsheetId = spreadsheetId;
		this.googleClientSecret = googleClientSecret;
		this.googleCredentials = googleCredentials;
		this.kaservice = kaservice;
	}

	public DataInserterResponse serve(OAuth1AccessToken accessToken, String id, String reason, String ip) {
		if (!validateId(id)) {
			return new DataInserterResponse(HttpServletResponse.SC_BAD_REQUEST, "Invalid id", false);
		}

		if (reason.length() > userReasonCharCount) {
			return new DataInserterResponse(HttpServletResponse.SC_BAD_REQUEST,
					"Your reason cannot exceed " + userReasonCharCount + " characters in length", false);
		}

		String insertId;
		try {
			insertId = getInsertId(id);
		} catch (Exception e) {
			return new DataInserterResponse(HttpServletResponse.SC_BAD_REQUEST, "Invalid id", false);
		}

		String kaid;
		try {
			kaid = getSenderKaid(accessToken);
		} catch (Exception e) {
			log.log(Level.WARNING, "Could not get user KAID from access token", e);
			return new DataInserterResponse(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", false);
		}

		Sheets sheetService;
		try {
			sheetService = getSheetsService();
		} catch (IOException e) {
			log.log(Level.WARNING, "Could not get Google sheets service", e);
			return new DataInserterResponse(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error",
					false);
		}

		List<List<Object>> currentEntries;
		try {
			currentEntries = getCurrentEntries(sheetService);
		} catch (IOException e) {
			log.log(Level.WARNING, "Could not get existing entries", e);
			return new DataInserterResponse(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error",
					false);
		}

		Date dateInserted = new Date();

		if (testIfAlreadySubmitted(currentEntries, kaid, id)) {
			return new DataInserterResponse(HttpServletResponse.SC_BAD_REQUEST,
					String.format("You already reported that %s", getType()), false);
		}

		if (testIfQuotaExceeded(currentEntries, kaid, dateInserted)) {
			return new DataInserterResponse(HttpServletResponse.SC_BAD_REQUEST,
					String.format("You cannot report more than %s times per hour.", userReportTimeCount), false);
		}

		try {
			insertItems(sheetService, insertId, kaid, reason, ip, dateInserted);
		} catch (IOException e) {
			log.log(Level.WARNING, "Could not insert data", e);
			return new DataInserterResponse(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error",
					false);
		}

		return new DataInserterResponse(HttpServletResponse.SC_OK, "Thanks.  A Guardian will review your report.",
				true);
	}

	private String getSenderKaid(OAuth1AccessToken accessToken)
			throws InterruptedException, ExecutionException, IOException {
		OAuthRequest req = new OAuthRequest(Verb.GET, "https://www.khanacademy.org/api/v1/user");
		kaservice.signRequest(accessToken, req);
		Response res = kaservice.execute(req);
		InputStream resIS = res.getStream();
		JsonReader reader = Json.createReader(resIS);
		JsonObject responseJSON = reader.readObject();
		reader.close();
		resIS.close();
		return responseJSON.getString("kaid");
	}

	private Credential getCredentials() throws IOException {
		InputStream clientSecret = null;
		clientSecret = new FileInputStream(googleClientSecret);

		File credentials = googleCredentials;
		FileDataStoreFactory googleDataStoreFactory = new FileDataStoreFactory(credentials);
		GoogleClientSecrets googleClientSecrets = GoogleClientSecrets.load(googleJsonFactory,
				new InputStreamReader(clientSecret));

		clientSecret.close();

		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(googleHttpTransport,
				googleJsonFactory, googleClientSecrets, googleScopes).setDataStoreFactory(googleDataStoreFactory)
						.setAccessType("offline").build();

		return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
	}

	private Sheets getSheetsService() throws IOException {
		return new Sheets.Builder(googleHttpTransport, googleJsonFactory, getCredentials()).setApplicationName(appName)
				.build();
	}

	private List<List<Object>> getCurrentEntries(Sheets service) throws IOException {
		ValueRange currentData = service.spreadsheets().values().get(spreadsheetId, "A:E").execute();
		List<List<Object>> cdata = new ArrayList<List<Object>>(currentData.getValues());
		if (cdata.get(0) != null)
			cdata.remove(0);
		return cdata;
	}

	private void insertItems(Sheets service, Object id, Object reportingKaid, Object reason, Object ip, Date date)
			throws IOException {
		ValueRange insertionBody = new ValueRange()
				.setValues(Arrays.asList(Arrays.asList(id, reportingKaid, reason, ip, (Object) datef.format(date))));

		Sheets.Spreadsheets.Values.Append insertRequest = service.spreadsheets().values().append(spreadsheetId, "A:E",
				insertionBody);
		insertRequest.setValueInputOption("USER_ENTERED");
		insertRequest.setInsertDataOption("OVERWRITE");

		insertRequest.execute();
	}

	private boolean testIfQuotaExceeded(List<List<Object>> data, String kaid, Date date) {
		final String finKaid = kaid;
		final Date insertDate = date;
		return data.stream().filter(e -> {
			try {
				Date reportedDate = datef.parse((String) e.get(4));
				return e.get(1) != null && e.get(1).equals(finKaid) && e.get(4) != null && reportedDate != null
						&& insertDate.getTime() - reportedDate.getTime() < userReportTimeMillis;
			} catch (Exception e1) {
				log.log(Level.INFO, "Could not parse " + ((String) e.get(4)), e);
				return false;
			}
		}).count() >= userReportTimeCount;
	}

	private boolean testIfAlreadySubmitted(List<List<Object>> data, String kaid, String id) {
		final String finKaid = kaid, finId = id;
		return data.stream().filter(
				e -> isModifiedId(finId, (String) e.get(0)) && e.get(1) != null && ((String) e.get(1)).equals(finKaid))
				.count() > 0;
	}

	abstract protected boolean validateId(String id);

	abstract protected boolean isModifiedId(String id, String modifiedId);

	abstract protected String getType();

	abstract protected String getInsertId(String id) throws Exception;
}
