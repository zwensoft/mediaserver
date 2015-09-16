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
package org.mobicents.media.server.rtsp.action;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.rtsp.RtspHeaders;
import io.netty.handler.codec.rtsp.RtspResponseStatuses;
import io.netty.handler.codec.rtsp.RtspVersions;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import javax.sdp.Media;
import javax.sdp.MediaDescription;
import javax.sdp.SessionDescription;

import org.apache.commons.lang.math.RandomUtils;
import org.apache.log4j.Logger;
import org.mobicents.media.server.io.sdp.fields.ConnectionField;
import org.mobicents.media.server.rtsp.RtspProvider;

/**
 * 
 * @author amit bhayani
 * 
 */
public class DescribeAction implements Callable<FullHttpResponse> {

    private static Logger logger = Logger.getLogger(DescribeAction.class);
    final private RtspProvider rtspProvider;
    final private HttpRequest request;
    final private String serverIp;
    final private ConnectionField connection;
    
    private static final String DATE_PATTERN = "EEE, d MMM yyyy HH:mm:ss z";

    public DescribeAction(RtspProvider rtspProvider, HttpRequest request, String serverIp) {
        this.rtspProvider = rtspProvider;
        this.request = request;
        this.serverIp = serverIp;
        
        if (null == serverIp) {
            throw new IllegalArgumentException("unknown server ip");
        } 
        else if (Pattern.matches("^([\\d]+.){4}$", serverIp + ".")) {
            connection = new ConnectionField("IN", "IP4", serverIp);    
        } else {
            connection = new ConnectionField("IN", "IP6", serverIp);
        }
    }

    public FullHttpResponse call() throws Exception {
        FullHttpResponse response = null;

        URI objUri = new URI(this.request.getUri());
        String srcUri = objUri.getPath();
        SessionDescription sd = rtspProvider.describe(srcUri);
        
        if (null == sd) {
            logger.warn("No srcUrl passed in request " + srcUri);
            response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.NOT_FOUND);
            response.headers().set(HttpHeaders.Names.SERVER, RtspProvider.SERVER);
            response.headers().set(RtspHeaders.Names.CSEQ, this.request.headers().get(RtspHeaders.Names.CSEQ));
            return response;
        }
        
        List<MediaDescription> mds = sd.getMediaDescriptions(false);
        if (null == mds || mds.isEmpty()) {
        	logger.warn("No Media passed in request " + srcUri);
            response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.BAD_REQUEST);
            response.headers().set(HttpHeaders.Names.SERVER, RtspProvider.SERVER);
            response.headers().set(RtspHeaders.Names.CSEQ, this.request.headers().get(RtspHeaders.Names.CSEQ));
            return response;
        }
        
        //we hard-code here
        //        sdp = "v=0\n" +
        //        "o=- 3776780 3776780 IN IP4 127.0.0.1\n" +
        //        "s=Mobicents Media Server\n" +
        //        "c=IN IP4 127.0.0.1\n" +
        //        "t=0 0\n" +
        //        "m=audio 0 RTP/AVP 0 2 3 97 8\n" +
        //        "b=AS:20\n"+
        //        "a=rtpmap:0 pcmu/8000\n" +        
        //        "a=rtpmap:2 g729/8000\n" +
        //        "a=rtpmap:3 gsm/8000\n" +
        //        "a=rtpmap:97 speex/8000\n" +
        //        "a=rtpmap:8 pcma/8000\n" +
        //        "a=control:audio\n";
                
        //        sdp = "v=0\n" +
        //        "o=MobicentsMediaServer 6732605 6732605 IN IP4 127.0.0.1\n"+
        //        "s=session\n"+
        //        "c=IN IP4 127.0.0.1\n"+
        //        "t=0 0\n"+
        //        "m=audio 0 RTP/AVP 97\n"+
        //        "b=AS:20\n"+
        //        "a=rtpmap:97 mpeg4-generic/8000/2\n"+
        //        "a=control:trackID=4\n"+
        //        "a=fmtp:97 profile-level-id=15;mode=AAC-hbr;sizelength=13;indexlength=3;indexdeltalength=3;config=1590\n"+
        //        "a=mpeg4-esid:101\n"+
        //        "m=video 0 RTP/AVP 96\n"+
        //        "b=AS:76\n"+
        //        "a=rtpmap:96 MP4V-ES/90000\n"+
        //        "a=control:trackID=3\n"+
        //        "a=cliprect:0,0,242,192\n"+
        //        "a=framesize:96 192-242\n"+
        //        "a=fmtp:96 profile-level-id=1;config=000001B0F3000001B50EE040C0CF0000010000000120008440FA283020F2A21F\n"+
        //        "a=mpeg4-esid:201\n";
        StringBuilder sdp = new StringBuilder();
        try {
            // v=0\n
            sdp.append("v=").append("0")
                .append("\n");
            
            // o=- 3776780 3776780 IN IP4 127.0.0.1\n
            long randomSessionId = RandomUtils.nextLong();
            sdp.append("o=")
                .append("- ")
                .append(randomSessionId).append(" ")
                .append(randomSessionId).append(" ")
                .append(connection.getNetworkType()).append(" ")
                .append(connection.getAddressType()).append(" ")
                .append(connection.getAddress()).append(" ")
                .append("\n")
                ;
            
            
            // s=Mobicents Media Server\n
            sdp.append("s=").append("Mobiecnts Rtsp Media Server").append("\n");
            
            // c=IN IP4 127.0.0.1\n
            sdp.append("c=")
                .append(connection.getNetworkType()).append(" ")
                .append(connection.getAddressType()).append(" ")
                .append(connection.getAddress()).append(" ")
                .append("\n");
            
            // "t=0 0\n"
            sdp.append("t=")
                .append("0 0")
                .append("\n");
            
            
            int mediaIndex = -1;
            for (MediaDescription md : mds) {
            	mediaIndex ++;
            	Media media = md.getMedia();
            	String rtpmap = md.getAttribute("rtpmap");
            	String fmtp = md.getAttribute("fmtp");
            	String control = request.getUri() + "/trackID=" + mediaIndex;
            	
            	// m=audio 0 RTP/AVP 97\n
            	sdp.append("m=").append(media.getMediaType()).append(" ")
            		.append("0 ")
            		.append("RTP/AVP");
            	List<Object> formats = media.getMediaFormats(true);
            	for (Object format : formats) {
            		sdp.append(" ").append(format);
				}
            	sdp.append("\n");
            	
            	// a=rtpmap:97 mpeg4-generic/8000/2\n
            	sdp.append("a=").append(rtpmap)
            		.append("\n");

            	// a=control:trackID=4\n
            	sdp.append("a=").append("control:")
            		.append(control)
            		.append("\n");

            	// a=rtpmap:96 MP4V-ES/90000\n
            	sdp.append("a=").append("rtpmap:")
        		.append(rtpmap)
        		.append("\n");
            	
            	// a=fmtp:97 profile-level-id=15;mode=AAC-hbr;sizelength=13;indexlength=3;indexdeltalength=3;config=1590\n
            	sdp.append("a=").append("fmtp:")
	        		.append(fmtp)
	        		.append("\n");
			}
            
        } catch (RuntimeException e) {
            logger.warn("There is no free endpoint: " + srcUri);
            response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.SERVICE_UNAVAILABLE);
            response.headers().add(HttpHeaders.Names.SERVER, RtspProvider.SERVER);
            response.headers().add(RtspHeaders.Names.CSEQ, this.request.headers().get(RtspHeaders.Names.CSEQ));
            return response;
        }


        byte[] bytes = sdp.toString().getBytes("UTF-8");
		response = new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.OK, Unpooled.copiedBuffer(bytes));
        response.headers().add(HttpHeaders.Names.SERVER, RtspProvider.SERVER);
        response.headers().add(RtspHeaders.Names.CSEQ, this.request.headers().get(RtspHeaders.Names.CSEQ));
        response.headers().add(HttpHeaders.Names.CONTENT_TYPE, "application/sdp");
        response.headers().add(HttpHeaders.Names.CONTENT_LENGTH, String.valueOf(bytes.length));

        return response;
    }
}
