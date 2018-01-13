package com.ethanmcdonough.ReportQueue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonBuilderFactory;
import javax.json.JsonException;

import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.logging.Level;

import java.net.HttpURLConnection;
import java.net.URL;

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

@WebServlet("/Report/Program")
public class ReportProgram extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static JsonBuilderFactory jsonFactory;

	private static Logger log = Logger.getLogger("log");

	private static HttpTransport googleHttpTransport;
	private static final JsonFactory googleJsonFactory = JacksonFactory.getDefaultInstance();
	private static final List<String> googleScopes = Arrays.asList(SheetsScopes.SPREADSHEETS, SheetsScopes.DRIVE);

	private static Pattern programIdRegex, kaidRegex;

	private static final String appName = "Alternate Guardian Flag Queue";

	private static final SimpleDateFormat datef = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss zzz");

	private static final int submitTimeLimitMin = 2;
	private static final long userReportTimeMillis = minToMillis(60), submitTimeLimit = minToMillis(submitTimeLimitMin);
	private static final int userReportTimeCount = 5, submitLimit = 4, userReasonCharCount = 5000;

	private static RequestLimiter limiter = new RequestLimiter(submitLimit, submitTimeLimit);

	static {
		datef.setTimeZone(TimeZone.getTimeZone("GMT"));
		programIdRegex = Pattern.compile("^\\d{10,30}$");
		kaidRegex = Pattern.compile("^kaid_\\d{20,40}$");
		jsonFactory = Json.createBuilderFactory(new HashMap<String, String>());
		log.setLevel(java.util.logging.Level.FINEST);
		try {
			googleHttpTransport = GoogleNetHttpTransport.newTrustedTransport();
		} catch (Exception e) {
			log.log(Level.SEVERE, "Could not initialize httpTransport", e);
		}
	}

	public ReportProgram() {
		super();
	}

	private static long minToMillis(int minutes) {
		return minutes * 60000L;
	}

	private String getIp(HttpServletRequest request) {
		return request.getHeader("x-forwarded-for") != null ? request.getHeader("x-forwarded-for")
				: (request.getHeader("X_FORWARDED_FOR") != null ? request.getHeader("X_FORWARDED_FOR")
						: request.getRemoteAddr());
	}

	protected void service(HttpServletRequest request, HttpServletResponse response)
			throws IOException, ServletException {
		try {
			String method = request.getMethod();
			String ip = getIp(request);

			response.setHeader("Access-Control-Allow-Origin", "*");
			response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
			response.setHeader("Access-Control-Allow-Headers", "Content-type, accept, X-KA-IntKaid, X-KA-FKey");

			if (method.equalsIgnoreCase("OPTIONS")) {
				response.setStatus(HttpServletResponse.SC_OK);
				return;
			}

			response.setContentType("application/json; charset=utf-8");

			if (!method.equalsIgnoreCase("POST")) {
				methodNotAllowed(request, response);
				return;
			}

			limiter.visit(ip);
			if (limiter.checkIfDenyService(ip)) {
				ratelimit(request, response);
				return;
			}

			JsonObject config = null;
			try {
				InputStream configIS = new FileInputStream(
						new File(getServletContext().getRealPath("/WEB-INF/") + "config.json"));
				JsonReader reader = Json.createReader(configIS);
				config = reader.readObject();
				reader.close();
				configIS.close();
			} catch (Exception e) {
				log.log(Level.SEVERE, "Unable to read config!", e);
				internalServerError(request, response);
				return;
			}

			String internalKaid = request.getHeader("X-KA-IntKaid");
			String fkey = request.getHeader("X-KA-FKey");

			if (internalKaid == null || fkey == null) {
				unauthorized(request, response);
				return;
			}

			InputStream bodyIs = request.getInputStream();
			JsonObject body = null;
			String kaid = null, reason = null, programId = null;
			try {
				JsonReader bodyReader = Json.createReader(bodyIs);
				body = bodyReader.readObject();
				bodyReader.close();

				bodyIs.close();

				programId = body.getJsonNumber("id").toString();
				kaid = body.getString("kaid");
				reason = body.getString("reason").trim();
			} catch (Exception e) {
				badRequest(request, response);
				return;
			}
			if (body == null || programId == null || reason == null || kaid == null) {
				badRequest(request, response);
				return;
			}

			if (reason.length() >= userReasonCharCount) {
				reasonTooLong(request, response);
				return;
			}

			final String finProgId = programId, finKaid = kaid;

			if (!programIdRegex.matcher(programId).find()) {
				invalidProgramId(request, response);
				return;
			}
			if (!kaidRegex.matcher(kaid).find()) {
				invalidKaid(request, response);
				return;
			}

			URL KAProgramAPIEndpoint = new URL("https://www.khanacademy.org/api/labs/scratchpads/" + programId);
			HttpURLConnection progCon = (HttpURLConnection) KAProgramAPIEndpoint.openConnection();
			progCon.setRequestMethod("GET");
			progCon.connect();
			InputStream returnedProgContent = null;
			try {
				returnedProgContent = progCon.getInputStream();
			} finally {
				if (returnedProgContent == null) {
					issuesWithKAAPI(request, response, "could not get https://www.khanacademy.org/api/labs/scratchpads/"
							+ programId + " input stream");
					return;
				}
			}
			if (progCon.getResponseCode() == 404) {
				invalidProgramId(request, response);
				return;
			}
			if (!progCon.getContentType().toLowerCase().contains("application/json")) {
				issuesWithKAAPI(request, response, "KA response type was not JSON");
				return;
			}

			JsonObject progData = null;
			try {
				JsonReader progReader = Json.createReader(returnedProgContent);
				progData = (JsonObject) progReader.readObject();
				progReader.close();
			} catch (JsonException e) {
				issuesWithKAAPI(request, response,
						"could not parse JSON returned from KA API https://www.khanacademy.org/api/labs/scratchpads/"
								+ programId);
				return;
			}

			returnedProgContent.close();

			URL profileUrl = new URL("https://www.khanacademy.org/api/internal/user/profile?kaid=" + kaid);
			HttpURLConnection profileCon = (HttpURLConnection) profileUrl.openConnection();
			profileCon.setRequestMethod("GET");
			profileCon.setRequestProperty("Cookie", "fkey=" + fkey + ";KAID=" + internalKaid + ";");
			profileCon.setRequestProperty("X-KA-FKey", fkey);
			profileCon.connect();
			InputStream profileIS = null;
			try {
				profileIS = profileCon.getInputStream();
			} catch (Exception e) {
				issuesWithKAAPI(request, response, "Issue with fetching profile information");
				log.log(Level.WARNING, "Issue with fetching profile information", e);
				return;
			}
			if (profileIS == null || profileCon.getResponseCode() == 404) {
				issuesWithKAAPI(request, response, "Issue with fetching profile information");
				return;
			}

			JsonObject profile = null;
			try {
				JsonReader profileReader = Json.createReader(profileIS);
				profile = profileReader.readObject();
				profileReader.close();
			} catch (JsonException e) {
				issuesWithKAAPI(request, response,
						"Unable to parse https://www.khanacademy.org/api/internal/user/profile?kaid=" + kaid);
				log.log(Level.WARNING,
						"Unable to parse https://www.khanacademy.org/api/internal/user/profile?kaid=" + kaid, e);
				return;
			} finally {
				profileIS.close();
			}

			if (!profile.getBoolean("isSelf")) {
				forbidden(request, response);
				return;
			}

			InputStream clientSecret = null;
			try {
				clientSecret = new FileInputStream(
						new File(getServletContext().getRealPath("/WEB-INF/") + "client_secret.json"));
			} catch (Exception e) {
				log.log(Level.WARNING, "Unable to fetch client_secret input stream!", e);
				internalServerError(request, response);
				return;
			}

			File credentials = new File(getServletContext().getRealPath("/WEB-INF/") + "credentials");
			FileDataStoreFactory googleDataStoreFactory = new FileDataStoreFactory(credentials);
			GoogleClientSecrets googleClientSecrets = GoogleClientSecrets.load(googleJsonFactory,
					new InputStreamReader(clientSecret));

			clientSecret.close();

			log.info(credentials.getAbsolutePath());

			GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(googleHttpTransport,
					googleJsonFactory, googleClientSecrets, googleScopes).setDataStoreFactory(googleDataStoreFactory)
							.setAccessType("offline").build();

			Credential cred = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");

			Sheets service = new Sheets.Builder(googleHttpTransport, googleJsonFactory, cred)
					.setApplicationName(appName).build();

			ValueRange currentData = service.spreadsheets().values().get(config.getString("spreadsheetId"), "A:E")
					.execute();
			List<List<Object>> cdata = new ArrayList<List<Object>>(currentData.getValues());

			if (cdata.get(0) != null)
				cdata.remove(0);

			if (cdata.stream()
					.filter(e -> e.get(0) != null && ((String) e.get(0)).split("/")[5] != null
							&& ((String) e.get(0)).split("/")[5].equals(finProgId) && e.get(1) != null
							&& ((String) e.get(1)).equals(finKaid))
					.count() > 0) {
				alreadyReported(request, response);
				return;
			}

			final Date insertDate = new Date();

			if (cdata.stream().filter(e -> {
				try {
					Date reportedDate = datef.parse((String) e.get(4));
					return e.get(1) != null && e.get(1).equals(finKaid) && e.get(4) != null && reportedDate != null
							&& insertDate.getTime() - reportedDate.getTime() < userReportTimeMillis;
				} catch (Exception e1) {
					log.log(Level.INFO, "Could not parse " + ((String) e.get(4)), e);
					return false;
				}
			}).count() >= userReportTimeCount) {
				tooManyReports(request, response);
				return;
			}

			ValueRange insertionBody = new ValueRange()
					.setValues(Arrays.asList(Arrays.asList((Object) progData.getString("url"), (Object) kaid,
							(Object) reason, (Object) ip, (Object) datef.format(insertDate))));

			Sheets.Spreadsheets.Values.Append insertRequest = service.spreadsheets().values()
					.append(config.getString("spreadsheetId"), "A:E", insertionBody);
			insertRequest.setValueInputOption("USER_ENTERED");
			insertRequest.setInsertDataOption("OVERWRITE");

			insertRequest.execute();

			PrintWriter writer = response.getWriter();

			response.setStatus(HttpServletResponse.SC_OK);
			writer.println(jsonFactory.createObjectBuilder().add("success", true)
					.add("message", "Thanks for reporting, a Guardian will review this program.").build().toString());
		} catch (Exception e) {
			internalServerError(request, response);
			log.log(Level.WARNING, "Internal server error", e);
			return;
		}
	}

	private void respondError(String msg, int code, HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		PrintWriter writer = response.getWriter();
		response.setStatus(code);
		writer.println(jsonFactory.createObjectBuilder().add("success", false).add("message", msg).build().toString());
	}

	private void internalServerError(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		respondError("Internal server error", HttpServletResponse.SC_INTERNAL_SERVER_ERROR, request, response);
	}

	private void unauthorized(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		respondError("Unauthorized", HttpServletResponse.SC_UNAUTHORIZED, request, response);
	}

	private void forbidden(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		respondError("Forbidden", HttpServletResponse.SC_FORBIDDEN, request, response);
	}

	private void issuesWithKAAPI(HttpServletRequest request, HttpServletResponse response, String note)
			throws ServletException, IOException {
		respondError("We're having some issues with the KA API right now (" + note + ")",
				HttpServletResponse.SC_INTERNAL_SERVER_ERROR, request, response);
	}

	private void badRequest(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		respondError("Bad request", HttpServletResponse.SC_BAD_REQUEST, request, response);
	}

	private void reasonTooLong(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		respondError("The reason may not be more than " + userReasonCharCount + " characters",
				HttpServletResponse.SC_BAD_REQUEST, request, response);
	}

	private void alreadyReported(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		respondError("You already reported that program", HttpServletResponse.SC_BAD_REQUEST, request, response);
	}

	private void tooManyReports(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		respondError("You cannot report more than " + userReportTimeCount + " programs per hour.",
				HttpServletResponse.SC_BAD_REQUEST, request, response);
	}

	private void invalidProgramId(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		respondError("Invalid program ID", HttpServletResponse.SC_BAD_REQUEST, request, response);
	}

	private void invalidKaid(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		respondError("Invalid kaid", HttpServletResponse.SC_BAD_REQUEST, request, response);
	}

	private void methodNotAllowed(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		respondError("Method not allowed", HttpServletResponse.SC_METHOD_NOT_ALLOWED, request, response);
	}

	private void ratelimit(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		respondError("You're sending too many requests.  Please wait a few minutes before trying again.", 429, request,
				response);
	}
}