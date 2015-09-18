package org.mobicents.media.server.rtsp.controller.stack;

import org.mobicents.media.server.impl.rtp.RtpPacket;

public class RtspFrame {
	private int channel;
	private int length;
	private RtpPacket rtp;
	
	public RtspFrame(int channel, int length, RtpPacket pkt) {
		this.channel = channel;
		this.length = length;
		this.rtp = pkt;
	}
	
	public int getChannel() {
		return channel;
	}
	
	public RtpPacket getPkt() {
		return rtp;
	}
	
	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("interleaved-frame").append(" channel=").append(channel).append(", length=").append(length);
		return buf.toString();
	}
}
