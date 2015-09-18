/*
 * JBoss, Home of Professional Open Source
 * Copyright XXXX, Red Hat Middleware LLC, and individual contributors as indicated
 * by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a full listing
 * of individual contributors.
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License, v. 2.0.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * v. 2.0 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 */
package org.mobicents.media.server.rtsp.stack;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.rtsp.RtspRequestEncoder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sdp.MediaDescription;
import javax.sdp.SdpParseException;
import javax.sdp.SessionDescription;

import org.apache.log4j.Logger;
import org.mobicents.media.server.ctrl.rtsp.endpoints.RtspPacketEvent;
import org.mobicents.media.server.io.network.UdpManager;
import org.mobicents.media.server.rtsp.controller.RtpSessionExt;
import org.mobicents.media.server.scheduler.Scheduler;
import org.mobicents.media.server.spi.listener.Listener;
import org.mobicents.media.server.spi.listener.Listeners;
import org.mobicents.media.server.spi.listener.TooManyListenersException;

/**
 * 
 * @author amit.bhayani
 * 
 */
public class RtspClientStackImpl  {

	private static Logger logger = Logger.getLogger(RtspClientStackImpl.class);

	private final UdpManager udpManager;
	private final Scheduler scheduler;
	private final String user;
	private final String passwd;
	private final String host;
	private final int port;
	private final String url;

	private Channel channel = null;
	private Bootstrap bootstrap;
	private EventLoopGroup workerGroup = new NioEventLoopGroup();

	private String session;
	private SessionDescription sessionDescription;
	private List<RtpSessionExt> rtpSessions = new ArrayList<RtpSessionExt>();
	private Listeners<Listener<RtspPacketEvent>> listeners = new Listeners<Listener<RtspPacketEvent>>();

	public RtspClientStackImpl(UdpManager udpManager, Scheduler scheduler, String url) {
		this.udpManager = udpManager;
		this.scheduler = scheduler;

		Pattern pattern = Pattern.compile("^rtsp://(([^:]+):([^@]*)@)?([^:/]+)(:([0-9]+))?(.*)");
		Matcher m = pattern.matcher(url);
		if (!m.find()) {
			throw new IllegalArgumentException("illegal rtsp url [" + url + "]");
		}

		user = m.group(2);
		passwd = m.group(3);
		host = m.group(4);
		String uri = m.group(6);

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
				// 瀹㈡埛绔帴鏀跺埌鐨勬槸httpResponse鍝嶅簲锛屾墍浠ヨ浣跨敤HttpResponseDecoder杩涜瑙ｇ爜
				ch.pipeline().addLast(new RtspResponseDecoder());
				// 瀹㈡埛绔彂閫佺殑鏄痟ttprequest锛屾墍浠ヨ浣跨敤HttpRequestEncoder杩涜缂栫爜
				ch.pipeline().addLast(new RtspRequestEncoder());
				// ch.pipeline().addLast(new HttpObjectAggregator(1024 * 64));
				ch.pipeline().addLast("handler", new RtspResponseHandler(RtspClientStackImpl.this));
			}
		});

		// Start the client.
		ChannelFuture f = b.connect(getHost(), getPort());
		try {
			f.sync();
			channel = f.channel();

			InetSocketAddress bindAddress = new InetSocketAddress(this.host, this.port);

			logger.info("Mobicents RTSP Client started and bound to " + bindAddress.toString());

			RtspResponseHandler handler = (RtspResponseHandler)f.channel().pipeline().get("handler");
			handler.connect();
		} catch (InterruptedException e) {
			throw new IOException(f.cause());
		}
	}

	public RtpSessionExt createRtpSession(MediaDescription md) {
		RtpSessionExt rtp;
		try {
			rtp = new RtpSessionExt(rtpSessions.size(), md.getMedia().getMediaType(), scheduler, udpManager);
			rtpSessions.add(rtp);
			return rtp;
		} catch (SdpParseException e) {
			throw new IllegalArgumentException("fail get media type", e);
		}
	}

	public RtpSessionExt getLastRtpSession() {
		return rtpSessions.isEmpty() ? null : rtpSessions.get(rtpSessions.size() - 1);
	}

	public void disconnect() {
		if (null != channel) {
			RtspResponseHandler handler = (RtspResponseHandler) channel.pipeline().get("handler");
			handler.sendTeardown();

			ChannelFuture cf = channel.closeFuture();
			cf.addListener(new ClientChannelFutureListener());
			channel.close();
			cf.awaitUninterruptibly();

			channel = null;
		}
	}

	private class ClientChannelFutureListener implements ChannelFutureListener {

		public void operationComplete(ChannelFuture arg0) throws Exception {
			logger.info("Mobicents RTSP Client Stop complete");

			workerGroup.shutdownGracefully();
		}

	}

	public void sendRequest(HttpRequest rtspRequest, String remoteHost, int remotePort) {

		ChannelFuture future = null;
		if (channel == null || (channel != null && !channel.isOpen())) {
			// Start the connection attempt.
			future = bootstrap.connect(new InetSocketAddress(remoteHost, remotePort));

			// Wait until the connection attempt succeeds or fails.
			channel = future.awaitUninterruptibly().channel();
			if (!future.isSuccess()) {
				future.cause().printStackTrace();
				// bootstrap.releaseExternalResources();
				return;
			}
		}

		channel.writeAndFlush(rtspRequest);
	}

	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public String getPasswd() {
		return passwd;
	}

	public String getUser() {
		return user;
	}

	public Channel getChannel() {
		return channel;
	}

	public void setSession(String sessionId) {
		this.session = sessionId;
	}

	public String getSession() {
		return session;
	}

	public String getUrl() {
		return url;
	}

	public void addListener(Listener<RtspPacketEvent> listener) throws TooManyListenersException {
		listeners.add(listener);
	}

	public void dispatch(RtspPacketEvent event) {
		listeners.dispatch(event);
	}

	public void setSessionDescription(SessionDescription descriptor) {
		this.sessionDescription = descriptor;
	}

	public SessionDescription getSessionDescription() {
		return sessionDescription;
	}

}
