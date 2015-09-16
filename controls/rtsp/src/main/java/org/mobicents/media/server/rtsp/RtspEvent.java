package org.mobicents.media.server.rtsp;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;

import org.mobicents.media.server.spi.listener.Event;

public interface RtspEvent extends Event<FullHttpRequest> {
	@Override
	public FullHttpRequest getSource();

	public Channel getChannel();
}
