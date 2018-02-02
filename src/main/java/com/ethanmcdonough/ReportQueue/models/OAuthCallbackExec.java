package com.ethanmcdonough.ReportQueue.models;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
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

public class OAuthCallbackExec implements Runnable {

	private static final Logger log = Logger.getLogger("log");
	private static Pattern kaURLPattern = Pattern.compile("^https:\\/\\/(?:www\\.)?khanacademy\\.org.*$");

	private AsyncContext context;

	public OAuthCallbackExec(AsyncContext context) {
		this.context = context;
	}

	private void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
		try {
			HttpSession session = request.getSession();

			String pathInfo = request.getPathInfo();
			String userToken = (pathInfo == null ? null : pathInfo.substring(1));

			String tokenName = CSRF.getTokenName(), tokenPublic = request.getParameter("oauth_token"),
					tokenSecret = request.getParameter("oauth_token_secret"),
					verifier = request.getParameter("oauth_verifier");

			if (userToken == null || session.getAttribute(tokenName) == null || verifier == null || tokenPublic == null
					|| tokenSecret == null || !userToken.equals(session.getAttribute(tokenName).toString())) {
				response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
				return;
			}

			JsonReader reader;
			InputStream configIS = new FileInputStream(
					new File(request.getServletContext().getRealPath("/WEB-INF/") + "config.json"));
			reader = Json.createReader(configIS);
			JsonObject configObj = (JsonObject) reader.readObject();
			reader.close();
			configIS.close();

			KAOAuth10aService kaservice = (KAOAuth10aService) new KAServiceBuilder(configObj.getString("KAAPIPublic"))
					.apiSecret(configObj.getString("KAAPISecret")).build(KhanApi.instance());

			OAuth1RequestToken requestToken = new OAuth1RequestToken(tokenPublic, tokenSecret);
			OAuth1AccessToken accessToken;
			try {
				accessToken = kaservice.getAccessToken(requestToken, verifier);
			} catch (Exception e) {
				log.log(Level.WARNING, "Could not get access token", e);
				response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				return;
			}

			HashMap<String, String> params = new HashMap<String, String>();
			if (session.getAttribute("auth-callback-type") != null)
				params.put("type", session.getAttribute("auth-callback-type") + "");
			if (session.getAttribute("auth-callback-id") != null)
				params.put("id", session.getAttribute("auth-callback-id") + "");
			if (session.getAttribute("auth-callback-callback") != null
					&& kaURLPattern.matcher(session.getAttribute("auth-callback-callback").toString()).matches())
				params.put("callback", session.getAttribute("auth-callback-callback") + "");

			session.removeAttribute("auth-callback-type");
			session.removeAttribute("auth-callback-id");
			session.removeAttribute("auth-callback-callback");

			session.setAttribute("access-token-public", accessToken.getToken());
			session.setAttribute("access-token-secret", accessToken.getTokenSecret());

			response.sendRedirect(String.format("%s/submit%s", request.getContextPath(),
					params.size() > 0 ? ("?" + QueryUtils.buildQuery(params)) : ""));
		} catch (Exception e) {
			log.log(Level.WARNING, "Internal server error", e);
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
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
