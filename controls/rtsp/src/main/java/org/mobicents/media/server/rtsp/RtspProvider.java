package org.mobicents.media.server.rtsp;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.rtsp.RtspHeaders;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.handler.codec.rtsp.RtspResponseStatuses;
import io.netty.handler.codec.rtsp.RtspVersions;
import io.netty.util.concurrent.Future;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Callable;

import org.mobicents.media.core.ResourcesPool;
import org.mobicents.media.server.ctrl.rtsp.session.DefaultSessionAccessor;
import org.mobicents.media.server.ctrl.rtsp.session.RtspSession;
import org.mobicents.media.server.ctrl.rtsp.session.RtspSessionAccessor;
import org.mobicents.media.server.io.network.UdpManager;
import org.mobicents.media.server.rtsp.action.DescribeAction;
import org.mobicents.media.server.rtsp.action.OptionsAction;
import org.mobicents.media.server.rtsp.action.PlayAction;
import org.mobicents.media.server.rtsp.action.SetupAction;
import org.mobicents.media.server.rtsp.action.TeardownAction;
import org.mobicents.media.server.rtsp.controller.Controller;
import org.mobicents.media.server.rtsp.controller.RtspManager;
import org.mobicents.media.server.rtsp.stack.RtspServerInitializer;
import org.mobicents.media.server.scheduler.Scheduler;
import org.mobicents.media.server.spi.listener.Listeners;
import org.mobicents.media.server.spi.listener.TooManyListenersException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RtspProvider implements RtspListener {
	public final static String SERVER = "Mobicents RTSP Media Server";
	private final static Logger logger = LoggerFactory
			.getLogger(RtspProvider.class);

	private static final int BIZTHREADSIZE = 4;
	private static final String DATE_PATTERN = "EEE, d MMM yyyy HH:mm:ss z";
	public static final String REQUIRE_VALUE_NGOD_R2 = "com.comcast.ngod.r2";
	public static final String REQUIRE_VALUE_NGOD_C1 = "com.comcast.ngod.c1";

	private static final int BIZGROUPSIZE;
	private static final EventLoopGroup bossGroup;
	private static final EventLoopGroup workerGroup;
	public static final RtspSessionAccessor sessionAccessor;

	static {
		BIZGROUPSIZE = Runtime.getRuntime().availableProcessors() * 2;

		bossGroup = new NioEventLoopGroup(BIZGROUPSIZE);
		workerGroup = new NioEventLoopGroup(BIZTHREADSIZE);
		sessionAccessor = new DefaultSessionAccessor();
	}

	// event listeners
	private Listeners<RtspListener> listeners = new Listeners<RtspListener>();

	// Underlying network interface\
	private UdpManager udpManager;

	// MGCP port number
	private int port;

	// Job scheduler
	private Scheduler scheduler;
	
	private ResourcesPool resourcesPool;

	// 
	private RtspManager rtspManager;
	
	public RtspProvider(UdpManager udpInterface, int port, Scheduler scheduler, ResourcesPool pool) {
		this.udpManager = udpInterface;
		this.port = port;
		this.scheduler = scheduler;
		this.resourcesPool = pool;
	}

	public void activate() {
		String host = udpManager.getLocalBindAddress();
		int port = this.port;

		logger.info("Opening channel");
		ServerBootstrap server = new ServerBootstrap();
		try {
			server.group(bossGroup, workerGroup);
			server.channel(NioServerSocketChannel.class);
			server.childHandler(new RtspServerInitializer(this).get());
			Future<?> f = server.bind(host, port);
			f.sync();


			if (f.isSuccess()) {
				logger.info("Binding channel to {}:{}", host, port);
			} else {
				logger.error("Failed Open channel {}:{}", host, port);
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	public void shutdown() {
		bossGroup.shutdownGracefully();
		
		workerGroup.shutdownGracefully();
	}

	public void addListener(Controller controller)
			throws TooManyListenersException {
		listeners.add(controller);
	}

	@Override
	public void process(RtspEvent event) {

	}

	public void process(FullHttpRequest request, ChannelHandlerContext ctx) {
		logger.info("\r\n<<<< \r\n{}", request);
		Callable<FullHttpResponse> action = null;
		FullHttpResponse response = null;
		try {

			if (request.getMethod().equals(RtspMethods.OPTIONS)) {
				action = new OptionsAction(this, request);
				response = action.call();
			} else if (request.getMethod().equals(RtspMethods.DESCRIBE)) {
				action = new DescribeAction(this, request, udpManager.getBindAddress());
				response = action.call();
			} else if (request.getMethod().equals(RtspMethods.SETUP)) {
				InetSocketAddress inetSocketAddress = (InetSocketAddress) ctx
						.channel().remoteAddress();
				String remoteIp = inetSocketAddress.getAddress()
						.getHostAddress();
				action = new SetupAction(this, request, remoteIp);
				response = action.call();
			} else if (request.getMethod().equals(RtspMethods.PLAY)) {
				action = new PlayAction(this, request);
				response = action.call();
			} else if (request.getMethod().equals(RtspMethods.TEARDOWN)) {
				action = new TeardownAction(this, request);
				response = action.call();
			} else if (request.getMethod().equals(HttpMethod.GET)) {

				String date = new SimpleDateFormat(DATE_PATTERN)
						.format(new Date());

				response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_0,
						HttpResponseStatus.OK);
				response.headers().add(HttpHeaders.Names.SERVER,
						RtspProvider.SERVER);
				response.headers().add(HttpHeaders.Names.CONNECTION,
						HttpHeaders.Values.CLOSE);
				response.headers().add(HttpHeaders.Names.DATE, date);
				response.headers().add(HttpHeaders.Names.CACHE_CONTROL,
						HttpHeaders.Values.NO_STORE);
				response.headers().add(HttpHeaders.Names.PRAGMA,
						HttpHeaders.Values.NO_CACHE);
				response.headers().add(HttpHeaders.Names.CONTENT_TYPE,
						"application/x-rtsp-tunnelled");

			} else if (request.getMethod().equals(HttpMethod.POST)) {
				// http://developer.apple.com/quicktime/icefloe/dispatch028.html
				// The POST request is never replied to by the server.
				logger.info("POST Response = " + response);
				// TODO : Map this request to GET

				return;

				// response = new DefaultHttpResponse(HttpVersion.HTTP_1_0,
				// HttpResponseStatus.NOT_IMPLEMENTED);
			} else {
				response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0,
						RtspResponseStatuses.METHOD_NOT_ALLOWED);
				response.headers().add(HttpHeaders.Names.SERVER,
						RtspProvider.SERVER);
				response.headers().add(RtspHeaders.Names.CSEQ,
						request.headers().get(RtspHeaders.Names.CSEQ));
				response.headers().add(RtspHeaders.Names.ALLOW,
						OptionsAction.OPTIONS);
			}

		} catch (Exception e) {
			logger.error("Unexpected error during processing,Caused by ", e);

			response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0,
					RtspResponseStatuses.INTERNAL_SERVER_ERROR);
			response.headers().add(HttpHeaders.Names.SERVER,
					RtspProvider.SERVER);
			response.headers().add(RtspHeaders.Names.CSEQ,
					request.headers().get(RtspHeaders.Names.CSEQ));
		}

		logger.info("Sending Response \r\n{}", response);
		ByteBuf sdp = response.content();
		String log = sdp.toString(0, sdp.readableBytes(),
				Charset.forName("UTF-8"));
		logger.info("\r\n>>>>>>> \r\n{} \r\n\r\n{}", response, log);

		ctx.writeAndFlush(response);
	}
	
	public Scheduler getScheduler() {
		return scheduler;
	}
	
	public UdpManager getUdpManager() {
		return udpManager;
	}
	
	public ResourcesPool getResourcesPool() {
		return resourcesPool;
	}
	
	public void setRtspManager(RtspManager rtspManager) {
		this.rtspManager = rtspManager;
	}
	
	public RtspManager getRtspManager() {
		return rtspManager;
	}

	public RtspSession getSession(String string, boolean b) {
		// TODO Auto-generated method stub
		return null;
	}
}
