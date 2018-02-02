package com.ethanmcdonough.kascribejavaextension;

import com.github.scribejava.core.builder.api.DefaultApi10a;
import com.github.scribejava.core.model.OAuthConfig;

public abstract class KADefaultApi10a extends DefaultApi10a {
	@Override
	public KAOAuth10aService createService(OAuthConfig config) {
		return new KAOAuth10aService(this, config);
	}
}
