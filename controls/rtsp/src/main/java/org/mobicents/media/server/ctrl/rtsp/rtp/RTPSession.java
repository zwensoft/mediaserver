package org.mobicents.media.server.ctrl.rtsp.rtp;

import javax.sdp.MediaDescription;

import org.mobicents.media.server.impl.rtp.RtpTransmitter;

public class RTPSession {
	private int rtpPort;
	private int rtcpPort;
	private int rtpInterleaved;
	private int rtcpInterleaved;

	private boolean unicast = true;
	private String serverHost;
	private int serverRtpPort;
	private int serverRtcpPort;
	private long ssrc;
	private String mode;
	
	private MediaDescription md;

	public RTPSession(RtpTransmitter transmitter, int rtpPort, int rtcpPort) {
		super();
		this.rtpPort = rtpPort;
		this.rtcpPort = rtcpPort;
		
	}

	public void play() {
		
	}
	
	public void tearDown() {
		
	}
	
	public int getRtpInterleaved() {
		return rtpInterleaved;
	}

	public void setRtpInterleaved(int rtpInterleaved) {
		this.rtpInterleaved = rtpInterleaved;
	}

	public int getRtcpInterleaved() {
		return rtcpInterleaved;
	}

	public void setRtcpInterleaved(int rtcpInterleaved) {
		this.rtcpInterleaved = rtcpInterleaved;
	}

	public MediaDescription getMd() {
		return md;
	}

	public void setMd(MediaDescription md) {
		this.md = md;
	}

	public String getServerHost() {
		return serverHost;
	}

	public int getRtcpPort() {
		return rtcpPort;
	}

	public int getRtpPort() {
		return rtpPort;
	}

	public boolean isUnicast() {
		return unicast;
	}

	public void setUnicast(boolean unicast) {
		this.unicast = unicast;
	}

	public int getServerRtpPort() {
		return serverRtpPort;
	}

	public void setServerRtpPort(int serverRtpPort) {
		this.serverRtpPort = serverRtpPort;
	}

	public int getServerRtcpPort() {
		return serverRtcpPort;
	}

	public void setServerRtcpPort(int serverRtcpPort) {
		this.serverRtcpPort = serverRtcpPort;
	}
	
	public void setMediaDescription(MediaDescription md) {
		this.md = md;
	}
	
	public void setServerHost(String serverHost) {
		this.serverHost = serverHost;
	}
	
	public long getSsrc() {
		return ssrc;
	}

	public void setSsrc(long ssrc) {
		this.ssrc = ssrc;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public void setRtpPort(int rtpPort) {
		this.rtpPort = rtpPort;
	}

	public void setRtcpPort(int rtcpPort) {
		this.rtcpPort = rtcpPort;
	}
}
