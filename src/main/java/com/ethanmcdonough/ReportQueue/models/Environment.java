package com.ethanmcdonough.ReportQueue.models;

public enum Environment {
	DEVELOPMENT, HEROKU;

	public static final Environment current = HEROKU;
}
