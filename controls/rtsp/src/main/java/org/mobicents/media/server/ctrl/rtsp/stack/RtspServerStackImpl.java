package org.mobicents.media.server.ctrl.rtsp.stack;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RtspServerStackImpl implements RtspStack {
  private static Logger logger = LoggerFactory.getLogger(RtspServerStackImpl.class);
  private final String ip;
  private final int port;
  private RtspListener listener = null;

  private static final int BIZGROUPSIZE = Runtime.getRuntime().availableProcessors() * 2;
  private static final int BIZTHREADSIZE = 4;
  private static final EventLoopGroup bossGroup = new NioEventLoopGroup(BIZGROUPSIZE);
  private static final EventLoopGroup workerGroup = new NioEventLoopGroup(BIZTHREADSIZE);

  public RtspServerStackImpl(String ip, int port) {
    this.ip = ip;
    this.port = port;
  }

  public void start() {
    try {
      ServerBootstrap server = new ServerBootstrap();
      server.group(bossGroup, workerGroup);
      server.channel(NioServerSocketChannel.class);
      server.childHandler(new RtspServerInitializer(this).get());
      server.bind(port).sync();
    } catch (InterruptedException e) {
      logger.error(e.getMessage(), e);
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
    }
  }

  public void stop() {}

  public void sendRequest(HttpRequest rtspRequest, String host, int port) {
    throw new UnsupportedOperationException("Not Supported yet");
  }

  protected void processRtspRequest(HttpRequest rtspRequest, ChannelHandlerContext ctx) {
    synchronized (this.listener) {
      listener.onRtspRequest(rtspRequest, ctx);
    }
  }

  protected void processRtspResponse(HttpResponse rtspResponse) {
    synchronized (this.listener) {
      listener.onRtspResponse(rtspResponse);
    }
  }

public int getPort() {
	return port;
}

public String getHost() {
	return ip;
}

public void setRtspListener(RtspListener listener) {
	this.listener = listener;
}


}
