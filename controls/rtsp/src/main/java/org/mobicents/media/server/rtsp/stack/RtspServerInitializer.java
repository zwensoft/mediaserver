package org.mobicents.media.server.rtsp.stack;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.rtsp.RtspRequestDecoder;
import io.netty.handler.codec.rtsp.RtspResponseEncoder;

import org.mobicents.media.server.rtsp.RtspProvider;

public class RtspServerInitializer {

  private final RtspProvider provider;

  public RtspServerInitializer(RtspProvider provider) {
    this.provider = provider;
  }

  public ChannelInitializer<SocketChannel> get() throws Exception {
    return new ChannelInitializer<SocketChannel>() {
      @Override
      public void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("decoder", new RtspRequestDecoder());
        pipeline.addLast("encoder", new RtspResponseEncoder());
        pipeline.addLast("aggregator", new HttpObjectAggregator(1048576));
        pipeline.addLast("handler", new RtspRequestHandler(provider));
      }
    };
  }
}
