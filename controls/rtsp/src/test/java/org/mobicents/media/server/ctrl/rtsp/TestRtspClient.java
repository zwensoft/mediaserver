package org.mobicents.media.server.ctrl.rtsp;

import java.io.IOException;

import org.mobicents.media.server.ctrl.rtsp.stack.RtspClientStackImpl;
import org.mobicents.media.server.io.network.PortManager;
import org.mobicents.media.server.io.network.UdpManager;
import org.mobicents.media.server.scheduler.Scheduler;

import junit.framework.TestCase;

public class TestRtspClient extends TestCase {
	private RtspClientStackImpl stack;
	
	@Override
	protected void setUp() throws Exception {
		//  private RtspSession client = new RtspSession("172.16.176.165", 554, "/caozhen", "admin", "12345");
		PortManager portManager = new PortManager();

		Scheduler scheduler = new Scheduler();
		UdpManager udpManager = new UdpManager(scheduler);

		String url = "rtsp://admin:12345678@172.16.176.165/caozhen";
		stack = new RtspClientStackImpl(udpManager, url);
	}
	
	public void testStart() throws IOException, InterruptedException {
		stack.start();
		
		Thread.sleep(30 * 1000);
	}
	
	@Override
	protected void tearDown() throws Exception {
		stack.stop();
	}
	
}
