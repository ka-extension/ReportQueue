package com.ethanmcdonough.ReportQueue.models;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;

public class QueryUtils {
	public static String buildQuery(HashMap<String, String> pairs) throws UnsupportedEncodingException {
		int i = 0;
		String encoded = "";
		for (String key : pairs.keySet()) {
			encoded += (i++ != 0 ? "&" : "") + URLEncoder.encode(key, "UTF-8") + "="
					+ URLEncoder.encode(pairs.get(key), "UTF-8");
		}
		return encoded;
	}
}
