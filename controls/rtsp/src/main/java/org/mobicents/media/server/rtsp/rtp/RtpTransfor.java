package org.mobicents.media.server.rtsp.rtp;

import org.mobicents.media.server.scheduler.Scheduler;
import org.mobicents.media.server.scheduler.Task;

public class RtpTransfor {
	private final Scheduler scheduler;
	private RtpSessionExt rtpSession;
	
	public RtpTransfor(Scheduler scheduler, RtpSessionExt rtpSession) {
		this.scheduler = scheduler;
		this.rtpSession = rtpSession;
	}
	
	
	
	public void start() {
	}
	
	
	public class PoolTask extends Task {

		@Override
		public int getQueueNumber() {
			return 0;
		}

		@Override
		public long perform() {
			return 0;
		}
		
	}
}
