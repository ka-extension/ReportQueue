package com.ethanmcdonough.kascribejavaextension;

import com.github.scribejava.core.model.OAuth1RequestToken;

public class KhanApi extends KADefaultApi10a {
	private static final String root = "https://www.khanacademy.org/api/";

	protected KhanApi() {

	}

	private static class KhanApiInstanceContainer {
		public static KhanApi instance = new KhanApi();
	}

	public static KhanApi instance() {
		return KhanApiInstanceContainer.instance;
	}

	@Override
	public String getRequestTokenEndpoint() {
		return root + "auth2/request_token";
	}

	@Override
	public String getAccessTokenEndpoint() {
		return root + "auth2/access_token";
	}

	@Override
	public String getAuthorizationUrl(OAuth1RequestToken oAuth1RequestToken) {
		return root + "auth2/authorize?oauth_token=" + oAuth1RequestToken.getToken();
	}
}
