package org.mobicents.media.server.ctrl.rtsp;

import gov.nist.core.StringTokenizer;
import gov.nist.javax.sdp.SessionDescriptionImpl;
import gov.nist.javax.sdp.fields.SDPField;
import gov.nist.javax.sdp.parser.ParserFactory;
import gov.nist.javax.sdp.parser.SDPParser;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.rtsp.RtspHeaders;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.handler.codec.rtsp.RtspResponseStatuses;
import io.netty.handler.codec.rtsp.RtspVersions;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.sdp.SessionDescription;

import org.mobicents.media.core.naming.NamingService;
import org.mobicents.media.server.ctrl.rtsp.config.ServerConfig;
import org.mobicents.media.server.ctrl.rtsp.session.BasicSessionStore;
import org.mobicents.media.server.ctrl.rtsp.session.DefaultSessionAccessor;
import org.mobicents.media.server.ctrl.rtsp.session.RtspSession;
import org.mobicents.media.server.ctrl.rtsp.session.RtspSessionAccessor;
import org.mobicents.media.server.ctrl.rtsp.session.RtspSessionKeyFactory;
import org.mobicents.media.server.ctrl.rtsp.session.SimpleRandomKeyFactory;
import org.mobicents.media.server.ctrl.rtsp.stack.RtspListener;
import org.mobicents.media.server.ctrl.rtsp.stack.RtspServerStackImpl;
import org.mobicents.media.server.io.network.UdpManager;
import org.mobicents.media.server.scheduler.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RtspController implements RtspListener {
	private static Logger logger = LoggerFactory.getLogger(RtspController.class);
	public static final String SERVER = "RtspServer";
	public static final String REQUIRE_VALUE_NGOD_R2 = "com.comcast.ngod.r2";
	public static final String REQUIRE_VALUE_NGOD_C1 = "com.comcast.ngod.c1";
	private static final String DATE_PATTERN = "EEE, d MMM yyyy HH:mm:ss z";

	public static final RtspSessionAccessor sessionAccessor = new DefaultSessionAccessor();
	public static final RtspSessionKeyFactory keyFactory = new SimpleRandomKeyFactory();
	
	final private Scheduler scheduler;
	final private UdpManager udpManager;
	final private BasicSessionStore sessionStore = new BasicSessionStore();
	
	private String ip;
	private int port;
	private ServerConfig serverConfig;
	private RtspServerStackImpl server = null;
	private NamingService namingService = new NamingService();
	
	
	public RtspController(Scheduler scheduler, UdpManager manager) {
		this.scheduler = scheduler;
		this.udpManager = manager;
	}

	public void start() throws Exception {
		this.server = new RtspServerStackImpl(ip, port);
		this.server.setRtspListener(this);
		this.server.start();
		logger.debug("Started Rtsp Server. ");
	}

	public void stop() {
		this.server.stop();
	}

	@Override
	public void onRtspRequest(HttpRequest request, ChannelHandlerContext ctx) {
		logger.info("\r\n<<<< \r\n{}", request);
		Callable<FullHttpResponse> action = null;
		FullHttpResponse response = null;
		try {

			if (request.getMethod().equals(RtspMethods.OPTIONS)) {
				action = new OptionsAction(this, request);
				response = action.call();
			} else if (request.getMethod().equals(RtspMethods.DESCRIBE)) {
				action = new DescribeAction(this, request, server.getHost());
				response = action.call();
			} else if (request.getMethod().equals(RtspMethods.SETUP)) {
				InetSocketAddress inetSocketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
				String remoteIp = inetSocketAddress.getAddress().getHostAddress();
				action = new SetupAction(this, request, remoteIp);
				response = action.call();
			} else if (request.getMethod().equals(RtspMethods.PLAY)) {
				action = new PlayAction(this, request);
				response = action.call();
			} else if (request.getMethod().equals(RtspMethods.TEARDOWN)) {
				action = new TeardownAction(this, request);
				response = action.call();
			} else if (request.getMethod().equals(HttpMethod.GET)) {

				String date = new SimpleDateFormat(DATE_PATTERN).format(new Date());

				response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_0, HttpResponseStatus.OK);
				response.headers().add(HttpHeaders.Names.SERVER, RtspController.SERVER);
				response.headers().add(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
				response.headers().add(HttpHeaders.Names.DATE, date);
				response.headers().add(HttpHeaders.Names.CACHE_CONTROL, HttpHeaders.Values.NO_STORE);
				response.headers().add(HttpHeaders.Names.PRAGMA, HttpHeaders.Values.NO_CACHE);
				response.headers().add(HttpHeaders.Names.CONTENT_TYPE, "application/x-rtsp-tunnelled");

			} else if (request.getMethod().equals(HttpMethod.POST)) {
				// http://developer.apple.com/quicktime/icefloe/dispatch028.html
				// The POST request is never replied to by the server.
				logger.info("POST Response = " + response);
				// TODO : Map this request to GET

				return;

				// response = new DefaultHttpResponse(HttpVersion.HTTP_1_0,
				// HttpResponseStatus.NOT_IMPLEMENTED);
			} else {
				response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.METHOD_NOT_ALLOWED);
				response.headers().add(HttpHeaders.Names.SERVER, RtspController.SERVER);
				response.headers().add(RtspHeaders.Names.CSEQ, request.headers().get(RtspHeaders.Names.CSEQ));
				response.headers().add(RtspHeaders.Names.ALLOW, OptionsAction.OPTIONS);
			}

		} catch (Exception e) {
			logger.error("Unexpected error during processing,Caused by ", e);

			response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.INTERNAL_SERVER_ERROR);
			response.headers().add(HttpHeaders.Names.SERVER, RtspController.SERVER);
			response.headers().add(RtspHeaders.Names.CSEQ, request.headers().get(RtspHeaders.Names.CSEQ));
		}

		logger.info("Sending Response \r\n{}", response);
		ByteBuf sdp = response.content();
		String log = sdp.toString(0, sdp.readableBytes(), Charset.forName("UTF-8"));
		logger.info("\r\n>>>>>>> \r\n{} \r\n\r\n{}", response, log);
		
		ctx.writeAndFlush(response);
	}

	@Override
	public void onRtspResponse(HttpResponse response) {

	}

	/*-----------Setter And Getter --------------*/

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public ServerConfig getServerConfig() {
		return serverConfig;
	}

	public void setServerConfig(ServerConfig serverConfig) {
		this.serverConfig = serverConfig;
	}

	public Set<String> getEndpoints() {
		return null;
	}

	public RtspSession getSession(String sessionID, boolean create) {
		return sessionStore.newSession(sessionID, create);
	}

	public SessionDescription describe(String srcUri) {
		StringBuilder buf = new StringBuilder();
        buf.append("v=0\n");
        buf.append("o=MobicentsMediaServer 6732605 6732605 IN IP4 127.0.0.1\n");
        buf.append("s=session\n");
        buf.append("c=IN IP4 127.0.0.1\n");
        buf.append("t=0 0\n");
        buf.append("m=audio 0 RTP/AVP 97\n");
        buf.append("b=AS:20\n");
        buf.append("a=rtpmap:97 mpeg4-generic/8000/2\n");
        buf.append("a=control:trackID=4\n");
        buf.append("a=fmtp:97 profile-level-id=15;mode=AAC-hbr;sizelength=13;indexlength=3;indexdeltalength=3;config=1590\n");
        buf.append("a=mpeg4-esid:101\n");
        buf.append("m=video 0 RTP/AVP 96\n");
        buf.append("b=AS:76\n");
        buf.append("a=rtpmap:96 MP4V-ES/90000\n");
        buf.append("a=control:trackID=3\n");
        buf.append("a=cliprect:0,0,242,192\n");
        buf.append("a=framesize:96 192-242\n");
        buf.append("a=fmtp:96 profile-level-id=1;config=000001B0F3000001B50EE040C0CF0000010000000120008440FA283020F2A21F\n");
        buf.append("a=mpeg4-esid:201\n");
		
        
		// String url = "rtsp://admin:12345678@172.16.176.165/caozhen";
		SessionDescriptionImpl sd = new SessionDescriptionImpl();
		StringTokenizer tokenizer = new StringTokenizer(buf.toString());
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
		
		return sd;
	}
}
