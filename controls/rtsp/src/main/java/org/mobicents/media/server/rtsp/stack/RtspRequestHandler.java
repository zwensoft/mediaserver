package org.mobicents.media.server.rtsp.stack;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;

import org.mobicents.media.server.rtsp.RtspProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RtspRequestHandler extends ChannelInboundHandlerAdapter {
  private static Logger logger = LoggerFactory.getLogger(RtspRequestHandler.class);
  private final RtspProvider listener;

  protected RtspRequestHandler(RtspProvider listener) {
    this.listener = listener;
  }

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof FullHttpRequest) {
    	FullHttpRequest request = (FullHttpRequest) msg;
    	listener.process(request, ctx);
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    logger.error(cause.getMessage(), cause);
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    logger.debug("channelActive {}", ctx.channel().remoteAddress());
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    logger.debug("channelInactive {}", ctx.channel().remoteAddress());
  }
}
