package org.mobicents.media.server.ctrl.rtsp;

import org.mobicents.media.server.io.network.UdpManager;
import org.mobicents.media.server.io.network.channel.Channel;
import org.mobicents.media.server.scheduler.Scheduler;

import junit.framework.TestCase;

public class TestRtspController extends TestCase {
	private Object lock = new Object();
	private RtspController controller;
	
	@Override
	protected void setUp() throws Exception {
		Scheduler scheduler = new Scheduler();
		UdpManager udpManager = new UdpManager(scheduler);
		
		Channel channel = null;
		udpManager.open(channel);
		
		controller = new RtspController(scheduler, udpManager);
		controller.setIp("172.16.160.143");
		controller.setPort(554);
	}
	
	public void testServer() throws Exception {
		controller.start();
		
		synchronized (lock) {
			lock.wait();
		}
	}
}
