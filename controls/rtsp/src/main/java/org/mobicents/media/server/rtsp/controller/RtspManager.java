package org.mobicents.media.server.rtsp.controller;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;

import javax.sdp.SessionDescription;

import org.mobicents.media.server.rtsp.RtspProvider;

/**
 * 负责管理 rtsp 源
 * 
 * @author chenxh
 */
public class RtspManager {
	private RtspProvider provider;

	// string locks
	private ConcurrentMap<String, Semaphore> locks = new ConcurrentHashMap<String, Semaphore>();

	// list of active endpoints
	private ConcurrentMap<String, RtspEndpoint> endpoints = new ConcurrentHashMap<String, RtspEndpoint>();

	public RtspManager(RtspProvider provider) {
		this.provider = provider;
	}
	
	public RtspEndpoint installRstpEndpoint(String url) {
		RtspEndpoint endpoint;

		lock(url);
		try {
			endpoint = endpoints.get(url);

			if (endpoint == null) {
				endpoint = new RtspEndpoint(url, provider);
				endpoint.start();
			}

			endpoints.put(url, endpoint);
		} finally {
			unlock(url);
		}
		
		return endpoint;
	}
	
	public SessionDescription describe(String url) {
		RtspEndpoint endpoint = null;

		endpoint = installRstpEndpoint(url);
		
		return endpoint.getSessionDescription();
	}
	

	public RtspCall makeCall(String url) throws IllegalArgumentException {
		RtspEndpoint endpoint = null;

		endpoint = installRstpEndpoint(url);
		
		return endpoint.makeCall();
	}

	private void lock(String url) {
		Semaphore semaphore;
		synchronized (locks) {
			locks.putIfAbsent(url, new Semaphore(1));
			semaphore = locks.get(url);
		}

		try {
			semaphore.acquire();
		} catch (InterruptedException e) {
			throw new IllegalStateException(e);
		}
	}

	private void unlock(String url) {
		Semaphore semaphore;
		synchronized (locks) {
			semaphore = locks.get(url);
			if (null == semaphore) {
				return;
			}
		}

		semaphore.release();
		if (!semaphore.hasQueuedThreads()) {
			locks.remove(url, semaphore);
		}
	}

	public void terminate(RtspCall rtspCall) {
		RtspEndpoint endpoint = rtspCall.getEndpoint();
		endpoint.terminate(rtspCall);

		if (endpoint.getNumCalls() < 1) {
			endpoints.remove(endpoint.getUrl(), endpoint);
			endpoint.stop();
		}
	}


}
