package org.mobicents.media.server.ctrl.rtsp;

import org.mobicents.media.server.io.network.UdpManager;
import org.mobicents.media.server.io.network.channel.Channel;
import org.mobicents.media.server.rtsp.RtspProvider;
import org.mobicents.media.server.scheduler.Scheduler;

import junit.framework.TestCase;

public class TestRtspController extends TestCase {
	private Object lock = new Object();
	private RtspProvider controller;
	
	@Override
	protected void setUp() throws Exception {
		Scheduler scheduler = new Scheduler();
		UdpManager udpManager = new UdpManager(scheduler);
		
		Channel channel = null;
		udpManager.open(channel);
		
		controller = new RtspProvider(udpManager, 554, scheduler);
	}
	
	public void testServer() throws Exception {
		controller.activate();
		
		synchronized (lock) {
			lock.wait();
		}
	}
}
