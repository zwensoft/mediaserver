package org.mobicents.media.server.ctrl.rtsp.stack;

import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.handler.codec.rtsp.RtspVersions;

public class RtspRequestDecoder extends RtspObjectDecoder {

	@Override
	protected boolean isDecodingRequest() {
		return true;
	}

	@Override
	protected HttpMessage createMessage(String[] initialLine) throws Exception {
		return new DefaultHttpRequest(RtspVersions.valueOf(initialLine[2]), RtspMethods.valueOf(initialLine[0]), initialLine[1], validateHeaders);
	}

	@Override
	protected HttpMessage createInvalidMessage() {
		return new DefaultHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.TEARDOWN, "");
	}

}
