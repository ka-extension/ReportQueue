package com.ethanmcdonough.ReportQueue;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;

public class RequestLimiter {
	private HashMap<String, LinkedList<Date>> ipMap = new HashMap<String, LinkedList<Date>>();
	private int limit;
	private long timeFrame;
	public RequestLimiter(int limit, long timeFrame) {
		this.limit = limit;
		this.timeFrame = timeFrame;
	}
	private void trunc(LinkedList<Date> dates) {
		if(dates.size() > limit + 1)
			dates.removeLast();
	}
	public boolean checkIfDenyService(String ip) {
		final Date now = new Date(); 
		return ipMap.get(ip) != null && 
				ipMap.get(ip).stream().filter(e -> 
					now.getTime() - e.getTime() <= timeFrame).count() > limit;
	}
	public void visit(String ip) {
		LinkedList<Date> dates = ipMap.get(ip) != null ? ipMap.get(ip) : new LinkedList<Date>();
		dates.push(new Date());
		if(ipMap.get(ip) == null)
			ipMap.put(ip, dates);
		trunc(dates);
	}
}