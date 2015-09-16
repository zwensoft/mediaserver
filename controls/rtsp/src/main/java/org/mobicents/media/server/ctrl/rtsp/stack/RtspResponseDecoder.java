package org.mobicents.media.server.ctrl.rtsp.stack;

import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.rtsp.RtspVersions;

public class RtspResponseDecoder extends RtspObjectDecoder {

	public RtspResponseDecoder() {
	}

	@Override
	protected boolean isDecodingRequest() {
		return false;
	}

	@Override
	protected HttpMessage createMessage(String[] initialLine) throws Exception {
		return new DefaultHttpResponse(RtspVersions.valueOf(initialLine[0]), HttpResponseStatus.valueOf(Integer.valueOf(initialLine[1])));
	}

	@Override
	protected HttpMessage createInvalidMessage() {
		return new DefaultHttpResponse(RtspVersions.RTSP_1_0, HttpResponseStatus.BAD_GATEWAY);
	}

}
