package org.mobicents.media.server.ctrl.rtsp;

import org.mobicents.media.server.impl.rtp.RtpPacket;

public class RtspInterleavedFrame {
	private int channel;
	private int length;
	private RtpPacket rtp;
	
	public RtspInterleavedFrame(int channel, int length, RtpPacket pkt) {
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
