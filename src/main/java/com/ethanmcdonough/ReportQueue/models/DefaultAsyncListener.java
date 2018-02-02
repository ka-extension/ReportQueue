package com.ethanmcdonough.ReportQueue.models;

import java.io.IOException;

import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.AsyncContext;

public class DefaultAsyncListener implements AsyncListener {
	private AsyncContext context;

	public DefaultAsyncListener(AsyncContext context) {
		this.context = context;
	}

	@Override
	public void onComplete(AsyncEvent event) throws IOException {
	}

	@Override
	public void onTimeout(AsyncEvent event) throws IOException {
		((HttpServletResponse) context.getResponse()).sendError(HttpServletResponse.SC_REQUEST_TIMEOUT);
	}

	@Override
	public void onError(AsyncEvent event) throws IOException {
		((HttpServletResponse) context.getResponse()).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
	}

	@Override
	public void onStartAsync(AsyncEvent event) throws IOException {
	}

}
