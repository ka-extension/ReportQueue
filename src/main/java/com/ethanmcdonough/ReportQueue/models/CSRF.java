package com.ethanmcdonough.ReportQueue.models;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.security.SecureRandom;
import java.util.logging.Logger;
import org.apache.commons.codec.binary.Hex;

public class CSRF {
	private static final String tokenName = "ftok";
	private static final SecureRandom generator = new SecureRandom();
	private static final int tokenLength = 64;
	protected static final Logger log = Logger.getLogger("log");

	public static String getTokenName() {
		return tokenName;
	}

	public static void run(HttpServletRequest request, HttpServletResponse response) {
		HttpSession session = request.getSession();
		if (session.getAttribute(tokenName) == null) {
			byte[] randomBytes = new byte[tokenLength];
			generator.nextBytes(randomBytes);
			String token = new String(Hex.encodeHex(randomBytes));
			session.setAttribute(tokenName, token);
		}
	}
}
