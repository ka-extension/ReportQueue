package com.ethanmcdonough.ReportQueue.controllers;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ethanmcdonough.ReportQueue.models.Environment;

@WebFilter(value = { "/*" }, asyncSupported = true)
public class HTTPSFilter implements Filter {
	public HTTPSFilter() {

	}

	public void destroy() {

	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		HttpServletRequest req = (HttpServletRequest) request;
		HttpServletResponse res = (HttpServletResponse) response;

		String prot = req.getHeader("x-forwarded-proto");

		if (Environment.current == Environment.HEROKU && prot != null && prot.equals("http")) {
			res.sendRedirect(
					req.getRequestURL().append(req.getQueryString() == null ? "" : ("?" + req.getQueryString()))
							.toString().replaceFirst("http", "https"));
		} else {
			chain.doFilter(request, response);
		}
	}

	public void init(FilterConfig fConfig) throws ServletException {

	}

}
