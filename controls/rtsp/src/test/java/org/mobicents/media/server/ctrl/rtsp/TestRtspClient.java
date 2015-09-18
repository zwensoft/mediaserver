package org.mobicents.media.server.ctrl.rtsp;

import java.io.IOException;

import junit.framework.TestCase;

import org.mobicents.media.core.ResourcesPool;
import org.mobicents.media.server.impl.rtp.ChannelsManager;
import org.mobicents.media.server.io.network.PortManager;
import org.mobicents.media.server.io.network.UdpManager;
import org.mobicents.media.server.rtsp.RtspProvider;
import org.mobicents.media.server.rtsp.controller.RtspEndpoint;
import org.mobicents.media.server.rtsp.dsp.SimpleDspFactory;
import org.mobicents.media.server.scheduler.DefaultClock;
import org.mobicents.media.server.scheduler.Scheduler;
import org.mobicents.media.server.spi.dsp.DspFactory;

public class TestRtspClient extends TestCase {
	private RtspEndpoint endpoint;
	
	@Override
	protected void setUp() throws Exception {
		//  private RtspSession client = new RtspSession("172.16.176.165", 554, "/caozhen", "admin", "12345");
		PortManager portManager = new PortManager();

		Scheduler scheduler = new Scheduler();
		scheduler.setClock(new DefaultClock());
		
		UdpManager udpManager = new UdpManager(scheduler);
		udpManager.setLowestPort(23001);
		udpManager.setHighestPort(43001);
		udpManager.setBindAddress("172.16.160.143");
		udpManager.setLocalNetwork("172.16.160.143");
		udpManager.setLocalBindAddress("172.16.160.143");
		udpManager.start();
		scheduler.start();
		
		DspFactory dspFactory = new SimpleDspFactory();
		
		ChannelsManager channelsManager = new ChannelsManager(scheduler, udpManager, dspFactory );
		ResourcesPool pool = new ResourcesPool(scheduler, channelsManager, dspFactory);
		
		String url = "rtsp://admin:12345678@172.16.176.165/caozhen";
		RtspProvider provider = new RtspProvider(udpManager, 554, scheduler, pool);
		endpoint = new RtspEndpoint(url, provider);
	}
	
	public void testConnect() throws IOException, InterruptedException {
		endpoint.start();
		
		Thread.sleep(1000 * 1000);
	}
	
	@Override
	protected void tearDown() throws Exception {
		endpoint.stop();
	}
	
}
