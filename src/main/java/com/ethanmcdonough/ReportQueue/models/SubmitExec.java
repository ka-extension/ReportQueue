package com.ethanmcdonough.ReportQueue.models;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.ethanmcdonough.kascribejavaextension.KAOAuth10aService;
import com.ethanmcdonough.kascribejavaextension.KAServiceBuilder;
import com.ethanmcdonough.kascribejavaextension.KhanApi;
import com.github.scribejava.core.model.OAuth1AccessToken;
import com.github.scribejava.core.model.OAuth1RequestToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Response;
import com.github.scribejava.core.model.Verb;

public class SubmitExec implements Runnable {
	private static Logger log = Logger.getLogger("log");
	private static Pattern kaURLPattern = Pattern.compile("^https:\\/\\/(?:www\\.)?khanacademy\\.org.*$");
	private AsyncContext context;

	private void redirectCallback(String type, String id, String callback, HttpSession session,
			HttpServletResponse response, KAOAuth10aService kaservice, OAuth1RequestToken requestToken)
			throws IOException {
		if (type != null)
			session.setAttribute("auth-callback-type", type);
		if (id != null)
			session.setAttribute("auth-callback-id", id);
		if (callback != null && kaURLPattern.matcher(callback).matches())
			session.setAttribute("auth-callback-callback", callback);
		String url = kaservice.getAuthorizationUrl(requestToken);
		response.sendRedirect(url);
	}

	private void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
		try {
			String type = request.getParameter("type"), id = request.getParameter("id"),
					callback = request.getParameter("callback");

			JsonReader reader;

			HttpSession session = request.getSession();

			InputStream configIS = new FileInputStream(
					new File(request.getServletContext().getRealPath("/WEB-INF/") + "config.json"));
			reader = Json.createReader(configIS);
			JsonObject configObj = (JsonObject) reader.readObject();
			reader.close();
			configIS.close();

			String oauthCallback = String.format("https://%s%s/callback/%s", request.getServerName(),
					request.getRequestURI(),
					session.getAttribute(CSRF.getTokenName()) != null
							? session.getAttribute(CSRF.getTokenName()).toString()
							: "");

			KAOAuth10aService kaservice = (KAOAuth10aService) new KAServiceBuilder(configObj.getString("KAAPIPublic"))
					.apiSecret(configObj.getString("KAAPISecret")).callback(oauthCallback).build(KhanApi.instance());

			OAuth1RequestToken requestToken = null;
			try {
				requestToken = kaservice.getRequestToken();
			} catch (Exception e) {
				log.log(Level.WARNING, "Error getting request token", e);
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				return;
			}

			String accessTokenPublic = (String) session.getAttribute("access-token-public"),
					accessTokenSecret = (String) session.getAttribute("access-token-secret");

			if (accessTokenPublic == null || accessTokenSecret == null) {
				redirectCallback(type, id, callback, session, response, kaservice, requestToken);
				return;
			}

			OAuth1AccessToken accessToken = new OAuth1AccessToken(accessTokenPublic, accessTokenSecret);

			OAuthRequest req = new OAuthRequest(Verb.GET, "https://www.khanacademy.org/api/v1/user");
			kaservice.signRequest(accessToken, req);

			Response res = null;
			JsonObject userData = null;
			try {
				res = kaservice.execute(req);
				InputStream userDataIS = new ByteArrayInputStream(
						res.getBody().getBytes(StandardCharsets.UTF_8.name()));
				reader = Json.createReader(userDataIS);
				userData = (JsonObject) reader.readObject();
				reader.close();
				userDataIS.close();
			} catch (Exception e) {
				log.log(Level.WARNING, "Error getting user data", e);
			}

			if (res != null && userData != null && res.getCode() == 200) {
				request.setAttribute("kaid", userData.getString("kaid"));
				request.setAttribute("nickname", userData.getJsonObject("student_summary").getString("nickname"));
				request.getRequestDispatcher("/WEB-INF/views/submit.jsp").forward(request, response);
			} else {
				redirectCallback(type, id, callback, session, response, kaservice, requestToken);
				return;
			}
		} catch (Exception e) {
			log.log(Level.WARNING, "Internal server error", e);
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	public SubmitExec(AsyncContext context) {
		this.context = context;
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
