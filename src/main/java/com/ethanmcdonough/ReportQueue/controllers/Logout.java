package com.ethanmcdonough.ReportQueue.controllers;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.ethanmcdonough.ReportQueue.models.CSRF;

@WebServlet("/logout")
public class Logout extends HttpServlet {
	private static final long serialVersionUID = 1L;

	public Logout() {
		super();
	}

	@Override
	protected void doDelete(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		HttpSession session = request.getSession();
		String providedToken = request.getHeader("x-" + CSRF.getTokenName());
		Object actualToken = session.getAttribute(CSRF.getTokenName());
		if (providedToken != null && actualToken != null && actualToken.toString().equals(providedToken)) {
			session.invalidate();
			response.setStatus(204);
			return;
		}
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
	}

}
