package org.mobicents.media.server.ctrl.rtsp.endpoints;

import org.mobicents.media.server.impl.rtp.RtpPacket;
import org.mobicents.media.server.spi.listener.Event;
import org.mobicents.media.server.spi.memory.Frame;

public class RtspPacketEvent implements Event<RtpPacket> {
	private RtpPacket frame;

	public RtspPacketEvent(RtpPacket frame) {
		this.frame = frame;
	}

	@Override
	public RtpPacket getSource() {
		return frame;
	}

}
