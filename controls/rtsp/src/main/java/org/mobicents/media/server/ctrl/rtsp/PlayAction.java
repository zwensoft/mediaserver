/*
 * JBoss, Home of Professional Open Source
 * Copyright XXXX, Red Hat Middleware LLC, and individual contributors as indicated
 * by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a full listing
 * of individual contributors.
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU General Public License, v. 2.0.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License,
 * v. 2.0 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 */
package org.mobicents.media.server.ctrl.rtsp;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.log4j.Logger;
import org.mobicents.media.server.ctrl.rtsp.session.RtspSession;
import org.mobicents.media.server.ctrl.rtsp.session.SessionState;
import org.mobicents.media.server.spi.Endpoint;
import org.mobicents.media.server.spi.player.Player;

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.rtsp.RtspHeaders;
import io.netty.handler.codec.rtsp.RtspResponseStatuses;
import io.netty.handler.codec.rtsp.RtspVersions;

/**
 * 
 * @author amit bhayani
 * 
 */
public class PlayAction implements Callable<FullHttpResponse> {

	private static Logger logger = Logger.getLogger(PlayAction.class);
	private RtspController rtspController = null;
	private HttpRequest request = null;

	public PlayAction(RtspController rtspController, HttpRequest request) {
		this.rtspController = rtspController;
		this.request = request;
	}

	public FullHttpResponse call() throws Exception {
		FullHttpResponse response = null;
		String sessionId = this.request.headers().get(RtspHeaders.Names.SESSION);
		String absolutePath = this.request.getUri();
		URI uri = new URI(absolutePath);

		String path = uri.getPath();
		String filePath = null;
		String trackID = null;

		int pos = path.indexOf("/trackID");
        if (pos > 0) {
            filePath = path.substring(0, pos);
            trackID = path.substring(pos + 1);
        } else {
            filePath = path;
        }

		File f = new File(filePath);
		if (f.isDirectory() || !f.exists()) {
			response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.NOT_FOUND);
			response.headers().add(HttpHeaders.Names.SERVER, RtspController.SERVER);
			response.headers().add(RtspHeaders.Names.CSEQ, this.request.headers().get(RtspHeaders.Names.CSEQ));
			return response;
		}

		String sessionID = this.request.headers().get(RtspHeaders.Names.SESSION);
		if (sessionID == null) {
			response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.BAD_REQUEST);
			response.headers().add(HttpHeaders.Names.SERVER, RtspController.SERVER);
			response.headers().add(RtspHeaders.Names.CSEQ, this.request.headers().get(RtspHeaders.Names.CSEQ));
			return response;
		}
		// determine session
		RtspSession session = rtspController.getSession(this.request.headers().get(RtspHeaders.Names.SESSION), false);
		if (session == null) {
			response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.SESSION_NOT_FOUND);
			response.headers().add(HttpHeaders.Names.SERVER, RtspController.SERVER);
			response.headers().add(RtspHeaders.Names.CSEQ, this.request.headers().get(RtspHeaders.Names.CSEQ));
			return response;
		}

		Endpoint endpoint = (Endpoint) session.getAttribute("endpoint");
		Player player = null;//(Player) endpoint.getComponent("player");
		String rtpInfo = "";
		double npt = 0;
		if (trackID != null) {
//			player.getMediaSource(trackID).start();
			//TODO Add rtp-info field
		} else {

			List<String> trackIds = (List<String>) session.getAttribute("trackIds");
			
			boolean first = true;
			for (String trackId : trackIds) {
				int rtpTime = 268435456 + (int) (Math.random() * (Integer.MAX_VALUE - 268435456));
//				player.setRtpTime(trackId, rtpTime);
				if (first) {
					rtpInfo += "url=" + absolutePath + "/" + trackId + ";seq=1;rtptime=" + rtpTime;
					first = false;					
//					npt = player.getNPT(trackId);
				} else {
					rtpInfo += ",url=" + absolutePath + "/" + trackId + ";seq=1;rtptime=" + rtpTime;
				}
			}
			
			//System.out.println("RTP-INfo = "+ rtpInfo+ " NPT = "+ npt);
//			player.start();
		}

		response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.OK);
		response.headers().add(HttpHeaders.Names.SERVER, RtspController.SERVER);
		response.headers().add(RtspHeaders.Names.CSEQ, this.request.headers().get(RtspHeaders.Names.CSEQ));
		response.headers().add(RtspHeaders.Names.SESSION, session.getId());
		response.headers().add(RtspHeaders.Names.RTP_INFO, rtpInfo);
		response.headers().add("Range", "npt=0.00000-"+npt);

		session.setState(SessionState.PLAYING);
		return response;
	}
}
