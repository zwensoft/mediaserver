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

import java.util.concurrent.Callable;

import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.rtsp.RtspHeaders;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.handler.codec.rtsp.RtspResponseStatuses;
import io.netty.handler.codec.rtsp.RtspVersions;

/**
 * 
 * @author amit bhayani
 *
 */
public class OptionsAction implements Callable<FullHttpResponse> {

	private RtspController rtspController = null;
	private HttpRequest request = null;
	public final static String OPTIONS = RtspMethods.DESCRIBE.name() + ", " + RtspMethods.SETUP.name() + ", "
			+ RtspMethods.TEARDOWN.name() + ", " + RtspMethods.PLAY.name()+ ", " + RtspMethods.OPTIONS.name();

	public OptionsAction(RtspController rtspController, HttpRequest request) {
		this.rtspController = rtspController;
		this.request = request;
	}

	public FullHttpResponse call() throws Exception {
		DefaultFullHttpResponse response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.OK);
		response.headers().add(HttpHeaders.Names.SERVER, RtspController.SERVER);
		response.headers().add(RtspHeaders.Names.CSEQ, this.request.headers().get(RtspHeaders.Names.CSEQ));
		response.headers().add(RtspHeaders.Names.PUBLIC, OPTIONS);
		return response;
	}
}
