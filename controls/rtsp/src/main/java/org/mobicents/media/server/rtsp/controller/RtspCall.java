package org.mobicents.media.server.rtsp.controller;

import org.mobicents.media.server.concurrent.ConcurrentMap;
import org.mobicents.media.server.rtsp.RtspProvider;
import org.mobicents.media.server.spi.MediaType;

/**
 * A RTSP Call
 * 
 * 每个客户端连接， 都是一个请求.
 * @author chenxh
 */
public class RtspCall {
	private long id;
	private RtspEndpoint endpoint;
	private ConcurrentMap<RtpConnectionExt> connections = new ConcurrentMap<RtpConnectionExt>();

	protected RtspCall(RtspEndpoint endpoint, long id) {
		this.id = id;
		this.endpoint = endpoint;
	}

	public long getId() {
		return id;
	}

	public String getSourceUrl() {
		return endpoint.getUrl();
	}
	
	public RtspEndpoint getEndpoint() {
		return endpoint;
	}
	
	public RtpConnectionExt getRtpConnection(MediaType type) {
		return null;
	}

	/**
	 * Excludes connection activity from this call.
	 * 
	 * @param activity
	 *            the activity to be excluded.
	 */
	public void exclude(RtpConnectionExt activity) {
		connections.remove(activity.id);

		// if no more connections terminate the entire call
		if (connections.isEmpty()) {
			endpoint.terminate(this);
		}
	}
}
