package org.mobicents.media.server.rtsp.controller.stack;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.handler.codec.rtsp.RtspRequestEncoder;
import io.netty.handler.codec.rtsp.RtspVersions;
import io.netty.util.ReferenceCountUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.mobicents.media.server.io.network.UdpManager;
import org.mobicents.media.server.rtsp.controller.RtpSessionExt;
import org.mobicents.media.server.rtsp.stack.RtspResponseDecoder;
import org.mobicents.media.server.scheduler.Scheduler;

public class RtspClientStack {
	private static Logger logger = Logger.getLogger(RtspClientStack.class);

	private final UdpManager udpManager;
	private final Scheduler scheduler;
	private final String user;
	private final String passwd;
	private final String host;
	private final int port;
	private final String url;
	
	// netty client
 	private ResponseFuture task = null;
 	private Channel channel;
 	private EventLoopGroup workerGroup = new NioEventLoopGroup();

 	// stack 
 	private String session;
	private AtomicInteger cseq = new AtomicInteger(1);
	private boolean acceptBasicAuth = false;
	private boolean acceptDigstAuth = false;
	
	public RtspClientStack(UdpManager udpManager, Scheduler scheduler, String url) {
		this.udpManager = udpManager;
		this.scheduler = scheduler;

		Pattern pattern = Pattern.compile("^rtsp://(([^:]+):([^@]*)@)?([^:/]+)(:([0-9]+))?(.*)$");
		Matcher m = pattern.matcher(url);
		if (!m.matches()) {
			throw new IllegalArgumentException("illegal rtsp url [" + url + "]");
		}

		user = m.group(2);
		passwd = m.group(3);
		host = m.group(4);
		String uri = m.group(7);
		if (null == uri) {
			uri = "";
		}

		int defaultPort = 554;
		try {
			defaultPort = Integer.parseInt(m.group(5));
		} catch (Exception e) {
		}
		port = defaultPort;

		this.url = "rtsp://" + host + ":" + port + uri;
	}
	
	public void connect() throws IOException {
		Bootstrap b = new Bootstrap();
		b.group(workerGroup);
		b.channel(NioSocketChannel.class);
		b.option(ChannelOption.SO_KEEPALIVE, true);
		b.handler(new ChannelInitializer<SocketChannel>() {
			@Override
			public void initChannel(SocketChannel ch) throws Exception {
				ch.pipeline().addLast(new RtspResponseDecoder());
				ch.pipeline().addLast(new RtspRequestEncoder());
				ch.pipeline().addLast("handler", new RtspResponseHandler());
			}
		});
		
		SocketAddress address = new InetSocketAddress(host, port);
		ChannelFuture future = b.connect(address);
		try {
			future.sync();
		} catch (InterruptedException ex) {
			throw new IOException("Fail connect:" + address);
		}

		if (!future.isSuccess()) {
			throw new IOException("Fail connect:" + address);
		}
		
		channel = future.channel();
	}

	public void disconnect() {
		if (null != channel) {
			try {
				ResponseFuture f = tearDown();
				f.get();
			} catch (Exception e) {
			}

			channel.close();
			channel = null;
		}
	}

	
	/**
	 * send a request and get response
	 * @param request
	 * @return
	 */
	public ResponseFuture send(FullHttpRequest request) {
		if (null != task) {
			throw new IllegalArgumentException("Existed Unfinish Task");
		}
		if (null == channel) {
			throw new IllegalStateException("Channel Not Connected");
		}
		
		task = new ResponseFuture(request);
		channel.writeAndFlush(request);
		return task;
	}
	
	public ResponseFuture options() {
		DefaultFullHttpRequest req = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.OPTIONS, url);
		addCSeq(req);
		
		return send(req);
	}
	
	public ResponseFuture describe(boolean auth) {
		DefaultFullHttpRequest req = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.DESCRIBE, url);
		addCSeq(req);
		req.headers().add("Accept", "application/sdp");

		if (auth && acceptBasicAuth) {
			String authValue = buildAuthorizationString();
			req.headers().add("Authorization", authValue);
		}
		
		return send(req);
	}
	
	public ResponseFuture setupByUDP(String controlUri, RtpSessionExt rtpSession) {
		String trackUrl;
		if (controlUri.startsWith("/")) {
			trackUrl = "rtsp://" + host + ":" + port + controlUri;
		} else if (controlUri.startsWith("rtsp://")){
			trackUrl = controlUri;
		} else if (url.endsWith("/") || controlUri.startsWith("/")){
			trackUrl = url + controlUri;
		} else {
			trackUrl = url + "/" + controlUri;
		}
		
		DefaultFullHttpRequest req = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.SETUP, trackUrl);
		addCSeq(req);
		req.headers().add("Transport", "RTP/AVP;unicast;client_port=" + rtpSession.getRtpPort() + "-" + rtpSession.getRtcpPort());

		return send(req);
	}
	
	public ResponseFuture setupByTCP(String controlUri, RtpSessionExt rtpSession) {
		String trackUrl;
		if (controlUri.startsWith("/")) {
			trackUrl = "rtsp://" + host + ":" + port + controlUri;
		} else if (controlUri.startsWith("rtsp://")){
			trackUrl = controlUri;
		} else if (url.endsWith("/") || controlUri.startsWith("/")){
			trackUrl = url + controlUri;
		} else {
			trackUrl = url + "/" + controlUri;
		}
		
		DefaultFullHttpRequest req = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.SETUP, trackUrl);
		addCSeq(req);
		req.headers().add("Transport", "RTP/AVP/TCP;interleaved="+ rtpSession.getRtpChunk() + "-" + rtpSession.getRtcpChunk());

		return send(req);
	}


	public ResponseFuture play() {
		DefaultFullHttpRequest req = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.PLAY, url);
		addCSeq(req);
		req.headers().add("Session", session);
		req.headers().add("Range", "npt=0.000");

		return send(req);
	}

	public ResponseFuture tearDown() {
		DefaultFullHttpRequest req = new DefaultFullHttpRequest(RtspVersions.RTSP_1_0, RtspMethods.TEARDOWN, url);
		addCSeq(req);
		
		if (null != session) {
			req.headers().add("Session", session);
		}

		return send(req);
	}
	private String buildAuthorizationString() {
		String user = this.user;
		String pass = this.passwd;
		byte[] bytes = org.apache.commons.codec.binary.Base64.encodeBase64(new String(user + ":"
				+ (pass != null ? pass : "")).getBytes());
		String authValue = "Basic " + new String(bytes);
		return authValue;
	}

	private void addCSeq(DefaultFullHttpRequest req) {
		req.headers().add("CSeq", cseq.toString());
		cseq.incrementAndGet();
	}
	
	private class RtspResponseHandler extends ChannelInboundHandlerAdapter {
		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			if (null != task) {
				task.cancel(true);
				task = null;
			}

			workerGroup.shutdownGracefully();
			super.channelInactive(ctx);
		}
		
		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
				throws Exception {
			if (null != task) {
				task.setException(cause);
				task = null;
			}

			super.exceptionCaught(ctx, cause);
		}
		
		
		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg)
				throws Exception {
			if (msg instanceof FullHttpResponse) {
				if (null != task) {
					FullHttpResponse response = (FullHttpResponse)msg;
					
					// session
					String session = response.headers().get("Session");
					if (null != session) {
						RtspClientStack.this.session = session;
					}
					
					// auth
					List<String> auths = response.headers().getAll("WWW-Authenticate");
					for(String auth : auths) {
						if (StringUtils.startsWith(auth, "Basic")) {
							acceptBasicAuth = true;
						}
						else if (StringUtils.startsWith(auth, "Digest")) {
							acceptDigstAuth = true;
						}
					}
					
					task.read((FullHttpResponse)msg);
					task = null;
				} else {
					ReferenceCountUtil.release(msg) ;
				}
			} else if (msg instanceof RtspFrame) {
				
			} else {
				super.channelRead(ctx, msg);
			}
		}
	}
	
	public String getHost() {
		return host;
	}
	
	public int getPort() {
		return port;
	}
}
