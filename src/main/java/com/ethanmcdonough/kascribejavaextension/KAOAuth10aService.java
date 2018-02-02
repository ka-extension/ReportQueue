package com.ethanmcdonough.kascribejavaextension;

import com.github.scribejava.core.builder.api.DefaultApi10a;
import com.github.scribejava.core.model.OAuthConfig;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.oauth.OAuth10aService;

public class KAOAuth10aService extends OAuth10aService {
	private final DefaultApi10a api;

	public KAOAuth10aService(DefaultApi10a api, OAuthConfig config) {
		super(api, config);
		this.api = api;
	}

	@Override
	protected OAuthRequest prepareRequestTokenRequest() {
		final OAuthConfig config = getConfig();
		final OAuthRequest request = new OAuthRequest(api.getRequestTokenVerb(),
				api.getRequestTokenEndpoint() + "?oauth_callback=" + config.getCallback());
		addOAuthParams(request, "");
		appendSignature(request);
		return request;
	}
}
