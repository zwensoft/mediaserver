package org.mobicents.media.server.rtsp.controller;

import org.mobicents.media.server.impl.rtp.RtpPacket;
import org.mobicents.media.server.impl.rtp.channels.RtpSession;
import org.mobicents.media.server.impl.rtp.sdp.RTPFormat;
import org.mobicents.media.server.impl.rtp.sdp.RTPFormats;
import org.mobicents.media.server.io.network.UdpManager;
import org.mobicents.media.server.rtsp.dsp.SimpleProcessor;
import org.mobicents.media.server.scheduler.Scheduler;
import org.mobicents.media.server.scheduler.Task;
import org.mobicents.media.server.spi.ConnectionMode;
import org.mobicents.media.server.spi.format.LinearFormats;
import org.slf4j.LoggerFactory;

/**
 * tcp to udp or udp to tcp
 * 
 * @author chenxh
 */
public class RtpSessionExt extends RtpSession {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RtpSessionExt.class);
    
	private Scheduler scheduler;
	
	private int rtpChunk;
	private int rtcpChunk;
	
	private RelayTask relay;

	public RtpSessionExt(int channelId, String mediaType, Scheduler scheduler,
			UdpManager udpManager) {
		super(channelId, mediaType, LinearFormats.AUDIO, scheduler,
				new SimpleProcessor(), udpManager);

		this.rtpChunk = channelId * 2 + 0;
		this.rtcpChunk = channelId * 2 + 1;
		this.scheduler = scheduler;
		this.relay = new RelayTask();		
		
	}
	@Override
	public void setFormats(RTPFormats formats) {
		super.setFormats(formats);
	}

	public void setRtcpChunk(int rtcpChunk) {
		this.rtcpChunk = rtcpChunk;
	}
	
	public void setRtpChunk(int rtpChunk) {
		this.rtpChunk = rtpChunk;
	}
	
	public void start() {
		setConnectionMode(ConnectionMode.SEND_RECV);
		
		scheduler.submit(relay, Scheduler.MIXER_MIX_QUEUE);
	}
	
	@Override
	public void incomingRtp(RtpPacket packet, RTPFormat format) {
		logger.info("{}", packet);
		super.incomingRtp(packet, format);
	}
	
	@Override
	public void outgoingRtp(RtpPacket packet) {
		super.outgoingRtp(packet);
	}
	
	
	public int getRtcpChunk() {
		return rtcpChunk;
	}

	public int getRtpChunk() {
		return rtpChunk;
	}
	
	private class RelayTask extends Task {

		@Override
		public int getQueueNumber() {
            return Scheduler.RECEIVER_QUEUE;
		}

		@Override
		public long perform() {
			
			org.mobicents.media.server.spi.memory.Frame[] frames = getMediaComponent().getInbandComponent().retrieveData();
			if (null != frames) {
				System.out.println(frames.length);
			}
			
			scheduler.submit(this, Scheduler.MIXER_MIX_QUEUE);
			return 0;
		}
		
	} 
}
