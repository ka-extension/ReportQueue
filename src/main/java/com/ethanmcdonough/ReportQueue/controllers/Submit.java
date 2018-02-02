package com.ethanmcdonough.ReportQueue.controllers;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.ethanmcdonough.ReportQueue.models.CThreadFactory;
import com.ethanmcdonough.ReportQueue.models.DefaultAsyncListener;
import com.ethanmcdonough.ReportQueue.models.SubmitExec;

@WebServlet(name = "submit", value = { "/submit" }, asyncSupported = true)
public class Submit extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private static final long TIMEOUT = 70000L;
	private static final int threadPoolSize = 20;

	private ExecutorService executor;

	public Submit() {
		super();
	}

	@Override
	public void init() {
		executor = Executors.newFixedThreadPool(threadPoolSize, new CThreadFactory());
	}

	@Override
	public void destroy() {
		executor.shutdown();
	}

	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		AsyncContext ctx = request.startAsync();
		ctx.setTimeout(TIMEOUT);
		ctx.addListener(new DefaultAsyncListener(ctx));
		executor.execute(new SubmitExec(ctx));
	}
}
