package org.mobicents.media.server.rtsp;

import org.mobicents.media.server.spi.listener.Listener;

public interface RtspListener extends Listener<RtspEvent> {
	@Override
	public void process(RtspEvent event);
}