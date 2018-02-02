package com.ethanmcdonough.kascribejavaextension;

import com.github.scribejava.core.builder.ServiceBuilder;

public class KAServiceBuilder extends ServiceBuilder {
	public KAServiceBuilder(String apiKey) {
		super(apiKey);
	}

	public KAOAuth10aService build(KhanApi api) {
		return api.createService(super.build(api).getConfig());
	}
}
