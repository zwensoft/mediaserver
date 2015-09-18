package org.mobicents.media.server.rtsp.controller;

import gov.nist.javax.sdp.fields.AttributeField;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.io.IOException;
import java.net.ConnectException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sdp.MediaDescription;
import javax.sdp.SdpException;
import javax.sdp.SessionDescription;

import org.mobicents.media.core.connections.RtpConnection;
import org.mobicents.media.core.endpoints.AbstractRelayEndpoint;
import org.mobicents.media.server.impl.rtp.sdp.RTPFormat;
import org.mobicents.media.server.impl.rtp.sdp.RTPFormats;
import org.mobicents.media.server.io.network.UdpManager;
import org.mobicents.media.server.rtsp.RtspProvider;
import org.mobicents.media.server.rtsp.controller.stack.ResponseFuture;
import org.mobicents.media.server.rtsp.controller.stack.RtspClientStack;
import org.mobicents.media.server.rtsp.controller.stack.Transport;
import org.mobicents.media.server.scheduler.Scheduler;
import org.mobicents.media.server.spi.ConnectionType;
import org.mobicents.media.server.spi.RelayType;
import org.mobicents.media.server.spi.ResourceUnavailableException;
import org.mobicents.media.server.spi.format.EncodingName;
import org.mobicents.media.server.spi.format.Format;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 一个 rtsp 源
 * 
 * 从源获取 rtp 数据，并分发给请求的客户端(calls)
 * 
 * @author chenxh
 */
public class RtspEndpoint extends AbstractRelayEndpoint {
	private static Logger logger = LoggerFactory.getLogger(RtspEndpoint.class);

	/** rtsp source url */
	final private String url;

	final private RtspProvider provider;

	/** client to receive source */
	final private AtomicLong index = new AtomicLong(Integer.MAX_VALUE);
	final private ConcurrentMap<Long, RtspCall> calls = new ConcurrentHashMap<Long, RtspCall>();

	private RtspClientStack stack;
	private SessionDescription sessionDescription;
	private RtpSessionExt[] rtpConnections = new RtpSessionExt[0];

	public RtspEndpoint(String url, RtspProvider provider) {
		super(url, RelayType.MIXER);
		this.url = url;
		this.provider = provider;
		
		setScheduler(provider.getScheduler());
	}
	

	public void start() throws IllegalArgumentException {
		Scheduler scheduler = provider.getScheduler();
		UdpManager udpManager = provider.getUdpManager();
		stack = new RtspClientStack(udpManager, scheduler, url);

		ResponseFuture future;
		try {
			// connect server
			stack.connect();

			// option
			future = stack.options();
			future.get();

			// describe
			future = stack.describe(false);
			if (unauthorized(future.get())) {
				future = stack.describe(true);
			}
			ensureOK(future.get());

			// loop:setup
			sessionDescription = future.getSessionDescription();
			if (null == sessionDescription) {
				throw new IllegalArgumentException("No SDP");
			}
			int mdIndex = 0;
			List<MediaDescription> mds = sessionDescription
					.getMediaDescriptions(true);
			rtpConnections = new RtpSessionExt[mds.size()];
			for (MediaDescription md : mds) {
				String controlUri = md.getAttribute("control");
				String mediaType = md.getMedia().getMediaType();
				
				RTPFormats formats = new RTPFormats();
				List<String> payloadTypes = md.getMedia().getMediaFormats(true);
				List<AttributeField> attrs = md.getAttributes(true);
				for (AttributeField attr : attrs) {
					if ("rtpmap".equals(attr.getName())) {
						String rtpmap = attr.getValue();
						
						Pattern pattern = Pattern.compile("([\\d]+)([\\s]*)([^/]+)/([\\d]+)");
						Matcher matcher = pattern.matcher(rtpmap);
						if (matcher.find() && payloadTypes.contains(matcher.group(1))) {
							int payloadType = Integer.valueOf(matcher.group(1));
							EncodingName name = new EncodingName(matcher.group(3));
							int clockRate = Integer.parseInt(matcher.group(4));
							formats.add(new RTPFormat(payloadType, new Format(name), clockRate));
						}
						
					}
				}
				
				
				RtpSessionExt rtpSession = new RtpSessionExt(mdIndex,
						mediaType, scheduler, udpManager);
				rtpSession.setFormats(formats);
				rtpSession.setMaxJitterSize("video".equals(mediaType)? 25 : 4);
				rtpSession.open();
				rtpSession.bind(false, false);

				future = stack.setupByUDP(controlUri, rtpSession);
				ensureOK(future.get());
				Transport trans = future.getTransport();
				rtpSession
						.connectRtp(stack.getHost(), trans.getRemoteRtpPort());
				rtpSession.connectRtcp(stack.getHost(),
						trans.getRemoteRtcpPort());
				rtpSession.start();

				rtpConnections[mdIndex++] = rtpSession;
			}

			// play
			future = stack.play();
			ensureOK(future.get());
		} catch (IOException e) {
			throw new IllegalArgumentException("Can't open " + url, e);
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		} catch (SdpException e) {
			e.printStackTrace();
		}
	}

	private boolean unauthorized(HttpResponseStatus status)
			throws ConnectException {
		switch (status.code()) {
		case 401: // UNAUTHORIZED
		case 407: // PROXY_AUTHENTICATION_REQUIRED
			return true;
		default:
			return false;
		}
	}

	private void ensureOK(HttpResponseStatus status) throws IOException {
		switch (status.code()) {
		case 200: // OK
			break;
		default:
			throw new ConnectException("Server Error: " + status);
		}
	}

	public void stop() {
		for (int i = 0; i < rtpConnections.length; i++) {
			try {
				if (null != rtpConnections[i]) {
					rtpConnections[i].close();
				}
			} catch (Exception e) {
				logger.info(e.getMessage(), e);
			}
		}

		if (null != stack) {
			stack.disconnect();
		}
	}

	public RtspCall makeCall() {
		RtspCall call = new RtspCall(this, index.incrementAndGet());
		calls.put(call.getId(), call);
		return call;
	}

	public void terminate(RtspCall rtspCall) {
		calls.remove(rtspCall.getId());

		if (calls.isEmpty()) {
			logger.warn("Nobody to call {}", url);
		}
	}

	public String getUrl() {
		return url;
	}

	public int getNumCalls() {
		return calls.size();
	}

	public SessionDescription getSessionDescription() {
		return sessionDescription;
	}
	
	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();

		buf.append("RtspEndpoint[").append(url).append("]");
		buf.append(" calls:").append(calls.size());

		return buf.toString();
	}


	@Override
	protected org.apache.log4j.Logger getLogger() {
		return org.apache.log4j.Logger.getLogger(getClass());
	}

}