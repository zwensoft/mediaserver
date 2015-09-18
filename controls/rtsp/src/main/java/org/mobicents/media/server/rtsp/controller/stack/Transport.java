package org.mobicents.media.server.rtsp.controller.stack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Transport {
	private static Logger logger = LoggerFactory.getLogger(Transport.class);
	
	private String protocol;
	private boolean unicast;
	private long ssrc;
	private int localRtpPort;
	private int localRtcpPort;
	
	private int remoteRtpPort;
	private int remoteRtcpPort;
	
	private int rtpChunkIndex = 0;
	private int rtcpChunkIndex = 0;
	/**
	 * @param transport RTP/AVP;unicast;client_port=4588-4589
	 */
	public Transport(String transport) {
		// key value
		Matcher matcher = Pattern.compile("([^\\s=;]+)=(([^-;]+)(-([^;]+))?)")
				.matcher(transport);
		while (matcher.find()) {
			String key = matcher.group(1).toLowerCase();
			if ("server_port".equals(key)) {
				remoteRtpPort =  Integer.valueOf(matcher.group(3));
				remoteRtcpPort =  Integer.valueOf(matcher.group(5));
			} else if ("client_port".equals(key)) {
				localRtpPort =  Integer.valueOf(matcher.group(3));
				localRtcpPort =  Integer.valueOf(matcher.group(5));
			} else if ("ssrc".equals(key)) {
				ssrc = Long.parseLong(matcher.group(2).trim(), 16);
			} else if ("interleaved".equals(key)) {
				rtpChunkIndex = Integer.valueOf(matcher.group(3));
				rtcpChunkIndex = Integer.valueOf(matcher.group(5));
			} else {
				logger.warn("ignored [{}={}]", key, matcher.group(2));
			}
		}
		
	}
	
	
	public int getRtcpChunkIndex() {
		return rtcpChunkIndex;
	}
	
	public int getRemoteRtcpPort() {
		return remoteRtcpPort;
	}


	public String getProtocol() {
		return protocol;
	}


	public boolean isUnicast() {
		return unicast;
	}


	public long getSsrc() {
		return ssrc;
	}


	public int getLocalRtpPort() {
		return localRtpPort;
	}


	public int getLocalRtcpPort() {
		return localRtcpPort;
	}


	public int getRemoteRtpPort() {
		return remoteRtpPort;
	}


	public int getRtpChunkIndex() {
		return rtpChunkIndex;
	}
	
	
	
}
