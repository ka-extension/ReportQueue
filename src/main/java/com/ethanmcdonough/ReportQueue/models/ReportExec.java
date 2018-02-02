package com.ethanmcdonough.ReportQueue.models;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.ethanmcdonough.kascribejavaextension.KhanApi;
import com.github.scribejava.core.builder.ServiceBuilder;
import com.github.scribejava.core.model.OAuth1AccessToken;
import com.github.scribejava.core.oauth.OAuth10aService;

public class ReportExec implements Runnable {
	private AsyncContext context;
	private static Logger log = Logger.getLogger("log");
	private static final JsonBuilderFactory jsonFactory = Json.createBuilderFactory(new HashMap<String, String>());
	private static final String appName = "Alternate Guardian Flag Queue";

	private static final int submitTimeLimitMin = 2;
	private static final long submitTimeLimit = minToMillis(submitTimeLimitMin);
	private static final int submitLimit = 4;

	private static final RequestLimiter limiter = new RequestLimiter(submitLimit, submitTimeLimit);

	public ReportExec(AsyncContext context) {
		this.context = context;
	}

	private static long minToMillis(int minutes) {
		return minutes * 60000L;
	}

	private String getIp(HttpServletRequest request) {
		return request.getHeader("x-forwarded-for") != null ? request.getHeader("x-forwarded-for")
				: (request.getHeader("X_FORWARDED_FOR") != null ? request.getHeader("X_FORWARDED_FOR")
						: request.getRemoteAddr());
	}

	private void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		try {
			HttpSession session = request.getSession();

			String method = request.getMethod();
			String ip = getIp(request);

			String path = request.getPathInfo();

			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");

			if (path == null) {
				notFound(request, response);
				return;
			}

			switch (path) {
			case "/program":
			case "/user":
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
							new File(request.getServletContext().getRealPath("/WEB-INF/") + "config.json"));
					JsonReader reader = Json.createReader(configIS);
					config = reader.readObject();
					reader.close();
					configIS.close();
				} catch (Exception e) {
					log.log(Level.SEVERE, "Unable to read config!", e);
					internalServerError(request, response);
					return;
				}

				OAuth10aService kaservice = new ServiceBuilder(config.getString("KAAPIPublic"))
						.apiSecret(config.getString("KAAPISecret")).build(KhanApi.instance());

				File googleCredentials = new File(request.getServletContext().getRealPath("/WEB-INF/") + "credentials"),
						clientSecret = new File(
								request.getServletContext().getRealPath("/WEB-INF/") + "client_secret.json");

				Object tokenPublicObj = session.getAttribute("access-token-public");
				Object tokenSecretObj = session.getAttribute("access-token-secret");

				String givenToken = request.getHeader("x-" + CSRF.getTokenName());
				Object actualToken = session.getAttribute(com.ethanmcdonough.ReportQueue.models.CSRF.getTokenName());

				if (givenToken == null || actualToken == null || !(actualToken + "").equals(givenToken)
						|| tokenPublicObj == null || tokenSecretObj == null) {
					unauthorized(request, response);
					return;
				}

				String tokenPublic = tokenPublicObj + "";
				String tokenSecret = tokenSecretObj + "";

				InputStream bodyIs = request.getInputStream();
				JsonObject body = null;
				String reason = null, id = null;
				try {
					JsonReader bodyReader = Json.createReader(bodyIs);
					body = bodyReader.readObject();
					bodyReader.close();

					bodyIs.close();

					id = body.getString("id");
					reason = body.getString("reason").trim();
				} catch (Exception e) {
					badRequest(request, response);
					return;
				}
				if (body == null || id == null || reason == null) {
					badRequest(request, response);
					return;
				}

				DataInserter inserter = path.equals("/program")
						? new ProgramDataInserter(appName, config.getString("programSpreadsheetId"), kaservice,
								clientSecret, googleCredentials)
						: new UserDataInserter(appName, config.getString("userSpreadsheetId"), kaservice, clientSecret,
								googleCredentials);

				DataInserterResponse result = inserter.serve(new OAuth1AccessToken(tokenPublic, tokenSecret), id,
						reason, ip);

				PrintWriter writer = response.getWriter();

				response.setStatus(result.getResponseCode());
				writer.println(result.getBodyJSONString());
				break;
			default:
				notFound(request, response);
				return;
			}
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

	private void badRequest(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		respondError("Bad request", HttpServletResponse.SC_BAD_REQUEST, request, response);
	}

	private void methodNotAllowed(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		respondError("Method not allowed", HttpServletResponse.SC_METHOD_NOT_ALLOWED, request, response);
	}

	private void notFound(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		respondError("Not found", HttpServletResponse.SC_NOT_FOUND, request, response);
	}

	private void ratelimit(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		respondError("You're sending too many requests.  Please wait a few minutes before trying again.", 429, request,
				response);
	}

	@Override
	public void run() {
		HttpServletRequest request = (HttpServletRequest) context.getRequest();
		HttpServletResponse response = (HttpServletResponse) context.getResponse();

		try {
			service(request, response);
		} catch (Exception e) {
			log.log(Level.WARNING, "Internal server error", e);
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}

		context.complete();
	}
}
