package com.ethanmcdonough.ReportQueue.models;

import java.util.concurrent.ThreadFactory;

public class CThreadFactory implements ThreadFactory {
	@Override
	public Thread newThread(Runnable r) {
		return new Thread(r, "Async processor");
	}
}
