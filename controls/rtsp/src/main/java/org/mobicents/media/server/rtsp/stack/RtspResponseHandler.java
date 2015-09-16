package org.mobicents.media.server.rtsp.stack;

import gov.nist.core.StringTokenizer;
import gov.nist.javax.sdp.SessionDescriptionImpl;
import gov.nist.javax.sdp.fields.SDPField;
import gov.nist.javax.sdp.parser.ParserFactory;
import gov.nist.javax.sdp.parser.SDPParser;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.handler.codec.rtsp.RtspVersions;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sdp.MediaDescription;
import javax.sdp.SdpException;
import javax.sdp.SessionDescription;

import org.apache.commons.lang.StringUtils;
import org.mobicents.media.server.ctrl.rtsp.endpoints.RtspPacketEvent;
import org.mobicents.media.server.impl.rtp.channels.RtpSession;
import org.mobicents.media.server.rtsp.action.RtspInterleavedFrame;
import org.mobicents.media.server.rtsp.rtp.RtpSessionExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RtspResponseHandler extends ChannelInboundHandlerAdapter {
	private static Logger logger = LoggerFactory.getLogger(RtspResponseHandler.class);
	private static enum RTSP {
		UNINIT, OPTIONS, DESCRIBE, SETUP, PLAY, TEARDOWN, PLAYING,
		ERROR
	};

	final private RtspClientStackImpl rtspStack;
	private ChannelHandlerContext ctx;
	private RTSP state = RTSP.UNINIT;
	private Semaphore locks = new Semaphore(0);
	
	private SessionDescription sd;
	private int mediaIndex = 0;
	private AtomicInteger cseq = new AtomicInteger(1);

	public RtspResponseHandler(RtspClientStackImpl client) {
		this.rtspStack = client;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);
		this.ctx = ctx;

	}

	public void connect() throws IOException {
		sendOptions();
		setState(RTSP.OPTIONS);
		
		
		while(state != RTSP.PLAYING && state != RTSP.ERROR) {
			try {
				boolean got;
				got = locks.tryAcquire(10, TimeUnit.SECONDS);
				if (!got) {
					throw new ConnectException("timeout for connect " + ctx.channel().remoteAddress());
				}
			} catch (InterruptedException e) {
				throw new ConnectException("Interrupted during connect " + ctx.channel().remoteAddress());
			}
			
		}
		
		if (state != RTSP.PLAYING) {
			throw new ConnectException("fail connect " + ctx.channel().remoteAddress());
		}
	}
	
	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		super.channelInactive(ctx);
		this.ctx = null;
		
		setState(RTSP.ERROR);
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		super.userEventTriggered(ctx, evt);
		
		if (evt instanceof IdleStateEvent) {
			sendGetParameters();
		}
	}
	
	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		
		if (msg instanceof DefaultFullHttpResponse) {
			messageReceive(ctx, (DefaultFullHttpResponse)msg);
		} else if (msg instanceof RtspInterleavedFrame){
			RtspInterleavedFrame rtp = (RtspInterleavedFrame)msg;
			if (rtp.getChannel() % 2 == 0) { // rtp
				rtspStack.dispatch(new RtspPacketEvent(rtp.getPkt()));
			}
			logger.trace("ignore msg: {}", msg);;
		}

		/** send next */
		super.channelRead(ctx, msg);
	}

	private void messageReceive(ChannelHandlerContext ctx, DefaultFullHttpResponse response) {
		/** send next cmd **/
		switch (state) {
		case PLAY:
			String rtpInfo = response.headers().get("RTP-Info");

			setState(RTSP.PLAYING);
			break;
		case SETUP:
			// session
			String sessionId = response.headers().get("Session");
			if (null != sessionId) {
				Matcher matcher = Pattern.compile("([^;]+)").matcher(sessionId);
				if (matcher.find()) {
					rtspStack.setSession(matcher.group(1));
				} else {
					rtspStack.setSession(sessionId);
				}
			}
			
			// transport
			InetSocketAddress remoteHost = (InetSocketAddress)ctx.channel().remoteAddress();
			String transport = response.headers().get("Transport");
			RtpSessionExt rtp = rtspStack.getLastRtpSession();
			Matcher matcher = Pattern.compile("([^\\s=;]+)=(([^-;]+)(-([^;]+))?)")
					.matcher(transport);
			while (matcher.find()) {
				String key = matcher.group(1).toLowerCase();
				if ("server_port".equals(key)) {
					rtp.connectRtp(new InetSocketAddress(remoteHost.getHostString(), Integer.valueOf(matcher.group(3))));
					rtp.connectRtcp(new InetSocketAddress(remoteHost.getHostString(), Integer.valueOf(matcher.group(5))));
				} else if ("ssrc".equals(key)) {
					rtp.setSsrc(Long.parseLong(matcher.group(2).trim(), 16));
				} else if ("interleaved".equals(key)) {
					rtp.setRtpChunkIndex(Integer.valueOf(matcher.group(3)));
					rtp.setRtcpChunkIndex(Integer.valueOf(matcher.group(5)));
				} else {
					logger.warn("ignored [{}={}]", key, matcher.group(2));
				}
			}
			
			// next action
			boolean finish = setup(sd, mediaIndex ++);
			if (finish) {
				sendPlay();
				setState(RTSP.PLAY);
			}
			
			break;
		case DESCRIBE:
			if (response.getStatus().code() == HttpResponseStatus.UNAUTHORIZED.code()) {
				sendDescribe(buildAuthorizationString(response));
				break;
			}
			
			String desciptor = response.content().toString(Charset.forName("UTF8"));
			SessionDescriptionImpl sd = new SessionDescriptionImpl();
			StringTokenizer tokenizer = new StringTokenizer(desciptor);
			while (tokenizer.hasMoreChars()) {
				String line = tokenizer.nextToken();

				try {
					SDPParser paser = ParserFactory.createParser(line);
					if (null != paser) {
						SDPField obj = paser.parse();
						sd.addField(obj);
					}
				} catch (ParseException e) {
					logger.warn("fail parse [{}]", line, e);
				}
			}
			this.sd = sd;
			
			mediaIndex = 0;
			setup(sd, mediaIndex ++);
			
			// 心跳
			rtspStack.getChannel().pipeline().addFirst("ping", new IdleStateHandler(30, 15, 13,TimeUnit.SECONDS));
			
			//
			rtspStack.setSessionDescription(sd);
			break;
		case OPTIONS:
			if (response.getStatus().code() == HttpResponseStatus.UNAUTHORIZED.code()) {
				sendDescribe(buildAuthorizationString(response));
			} else {
				sendDescribe();
			}
			
			setState(RTSP.DESCRIBE);
			break;
		case PLAYING:
			break;
		default:
			logger.warn("I dont't Known What to do with {}", response);
			break;
		}
	}

	public boolean setup(SessionDescription sd, int mediaIndex) {
		boolean finish = false;
		try {
			Vector<MediaDescription> mds = sd.getMediaDescriptions(true);
			
			if (mds.size() <= mediaIndex) {
				finish = true;
			} else {
				MediaDescription md = mds.get(mediaIndex);
				
				RtpSessionExt rtp = rtspStack.createRtpSession(md);
				rtp.bind(false, true);

				
				// sendTCPSetup(md.getAttribute("control"), rtp.getRtpInterleaved(), rtp.getRtcpInterleaved());
				sendUDPSetup(md.getAttribute("control"), rtp.getRtpPort(), rtp.getRtcpPort());
				
				
				setState(RTSP.SETUP);
			}
			
		} catch (SdpException e) {
			throw new IllegalArgumentException(e);
		} catch (IllegalStateException e) {
			logger.error(e.getMessage(), e);
		} catch (IOException e) {
			logger.error(e.getMessage(), e);
		}
		
		return finish;
		
	}
	
	public void tearDown() {
		if (null != ctx) {
			sendTeardown();
			setState(RTSP.TEARDOWN);
		}
	}

	private void setState(RTSP newState) {
		if (newState.ordinal() < state.ordinal()) {
			throw new IllegalStateException("can't change  state from " + this.state + " to " + newState);
		}

		this.state = newState;
		locks.release();
	}

	

	public void sendOptions()  {
		DefaultFullHttpRequest req = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.OPTIONS, rtspStack.getUrl());
		addCSeq(req);
		rtspStack.sendRequest(req, rtspStack.getHost(), rtspStack.getPort());
	}
	
	private String buildAuthorizationString(HttpResponse request) {
		List<String> auths = request.headers().getAll("WWW-Authenticate");
		for(String auth : auths) {
			if (StringUtils.startsWith(auth, "Basic")) {
				String user = rtspStack.getUser();
				String pass = rtspStack.getPasswd();
				byte[] bytes = org.apache.commons.codec.binary.Base64.encodeBase64(new String(user + ":"
						+ (pass != null ? pass : "")).getBytes());
				String authValue = "Basic " + new String(bytes);
				return authValue;
				
			}
			else if (StringUtils.startsWith(auth, "Digest")) {
				
			}
		}
		
		throw new UnsupportedOperationException("fail use WWW-Authenticate:" + auths.toString());
	}
	
	public void sendDescribe(String auth)  {
		DefaultFullHttpRequest req = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.DESCRIBE, rtspStack.getUrl());
		addCSeq(req);
		req.headers().add("Accept", "application/sdp");
		if (null != auth) {
			req.headers().add("Authorization", auth);
		}
		rtspStack.sendRequest(req, rtspStack.getHost(), rtspStack.getPort());
	}
	
	public void sendDescribe()  {
		DefaultFullHttpRequest req = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.DESCRIBE, rtspStack.getUrl());
		addCSeq(req);
		req.headers().add("Accept", "application/sdp");

		rtspStack.sendRequest(req, rtspStack.getHost(), rtspStack.getPort());
	}
	
	public void sendGetParameters()  {
		DefaultFullHttpRequest req = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.GET_PARAMETER, rtspStack.getUrl());
		addCSeq(req);

		rtspStack.sendRequest(req, rtspStack.getHost(), rtspStack.getPort());
	}

	public void sendUDPSetup(String track, int rtp, int rtcp)  {
		String trackUrl = track;
		if (track.startsWith("rtsp://")) {
			trackUrl = track;
		} else {
			throw new IllegalArgumentException("unsupported track url:" + track);
		}
		
		DefaultFullHttpRequest req = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.SETUP, trackUrl);
		addCSeq(req);
		req.headers().add("Transport", "RTP/AVP;unicast;client_port="+rtp + "-" + rtcp);

		rtspStack.sendRequest(req, rtspStack.getHost(), rtspStack.getPort());
	}
	
	/**
	 * Transport: RTP/AVP/TCP;interleaved=${rtp}-${rtcp}
	 * 
	 * @param track
	 * @param rtp
	 * @param rtcp
	 */
	public void sendTCPSetup(String track, int rtp, int rtcp)  {
		String trackUrl = track;
		if (track.startsWith("rtsp://")) {
			trackUrl = track;
		} else {
			throw new IllegalArgumentException("unsupported track url:" + track);
		}
		
		DefaultFullHttpRequest req = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.SETUP, trackUrl);
		addCSeq(req);
		req.headers().add("Transport", "RTP/AVP/TCP;interleaved="+rtp + "-" + rtcp);

		rtspStack.sendRequest(req, rtspStack.getHost(), rtspStack.getPort());
	}

	public void sendPlay()  {
		DefaultFullHttpRequest req = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.PLAY, rtspStack.getUrl());
		addCSeq(req);
		req.headers().add("Session", rtspStack.getSession());
		req.headers().add("Range", "npt=0.000");

		rtspStack.sendRequest(req, rtspStack.getHost(), rtspStack.getPort());
	}

	public void sendTeardown()  {
		DefaultFullHttpRequest req = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.TEARDOWN, rtspStack.getUrl());
		addCSeq(req);
		
		String session = rtspStack.getSession();
		if (null != session) {
			req.headers().add("Session", session);
		}

		rtspStack.sendRequest(req, rtspStack.getHost(), rtspStack.getPort());
	}
	
	private void addCSeq(DefaultFullHttpRequest req) {
		req.headers().add("CSeq", cseq.toString());
		cseq.incrementAndGet();
	}
}