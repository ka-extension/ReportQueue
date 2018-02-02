package com.ethanmcdonough.ReportQueue.controllers;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ethanmcdonough.ReportQueue.models.CSRF;

import javax.servlet.annotation.WebFilter;

@WebFilter(value = { "/*" }, asyncSupported = true)
public class CSRFFilter implements Filter {
	public CSRFFilter() {

	}

	public void destroy() {
	}

	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		CSRF.run((HttpServletRequest) request, (HttpServletResponse) response);

		chain.doFilter(request, response);
	}

	public void init(FilterConfig fConfig) throws ServletException {
	}

}
