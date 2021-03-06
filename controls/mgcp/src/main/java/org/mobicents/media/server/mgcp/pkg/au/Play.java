/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc. and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.mobicents.media.server.mgcp.pkg.au;

import java.net.MalformedURLException;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

import org.apache.log4j.Logger;
import org.mobicents.media.ComponentType;
import org.mobicents.media.server.mgcp.controller.signal.Event;
import org.mobicents.media.server.mgcp.controller.signal.NotifyImmediately;
import org.mobicents.media.server.mgcp.controller.signal.Signal;
import org.mobicents.media.server.spi.Connection;
import org.mobicents.media.server.spi.Endpoint;
import org.mobicents.media.server.spi.MediaType;
import org.mobicents.media.server.spi.ResourceUnavailableException;
import org.mobicents.media.server.spi.listener.TooManyListenersException;
import org.mobicents.media.server.spi.player.Player;
import org.mobicents.media.server.spi.player.PlayerEvent;
import org.mobicents.media.server.spi.player.PlayerListener;
import org.mobicents.media.server.utils.Text;

/**
 * Implements play announcement signal.
 * 
 * @author yulian oifa
 */
public class Play extends Signal implements PlayerListener {
	
	private final static String QUERY_MEDIA_SESSION_PATTERN = "query_media_session_";
	private final static int QUERY_MEDIA_SESSION_LEN = QUERY_MEDIA_SESSION_PATTERN.length();
    
    private Event oc = new Event(new Text("oc"));
    private Event of = new Event(new Text("of"));
    
    private Player player;
    private volatile Options options;
    
    private int repeatCount;
    private int segCount;
    
    
    private long delay;
    private String uri;
    
    private Iterator<Text> segments;
    private final static Logger logger = Logger.getLogger(Play.class);
    
    private Semaphore terminateSemaphore=new Semaphore(1);
    
    public Play(String name) {
        super(name);
        oc.add(new NotifyImmediately("N"));
        of.add(new NotifyImmediately("N"));                
    }
    
    @Override
    public void execute() {
    	//get access to player
        player = this.getPlayer();

        //check result
        if (player == null) {
            of.fire(this, new Text("Endpoint has no player"));
            complete();
            return;
        }
        
        //register announcement handler
        try {
            player.addListener(this);
        } catch (TooManyListenersException e) {
            logger.error("OPERATION FAILURE", e);
        } 
        
        //get options of the request
        options = Options.allocate(getTrigger().getParams());        
                
        //set initial delay
        delay = 0;
        
        //get announcement segments
        segments = options.getSegments().iterator();
        repeatCount = options.getRepeatCount();
        segCount=0;
        
        uri = segments.next().toString();
                
        if(uri.startsWith(QUERY_MEDIA_SESSION_PATTERN)) {
        	// Query availability of the media session
			queryConnectionAvailability();
        } else {
        	//start announcement
        	startAnnouncementPhase();        
        }
    }
    
    private void queryConnectionAvailability() {
    	// extract conneciton id from the reserved URI
    	String connectionIdHex = uri.substring(QUERY_MEDIA_SESSION_LEN, uri.length());
    	logger.info(String.format("(%s) Querying connection availability (connectionId=%s)", getEndpoint().getLocalName(), connectionIdHex));
    	
    	try {
    		// Convert connection id from hexadecimal to decimal
    		int connectionId = Integer.parseInt(connectionIdHex, 16);

    		// Retrieve RTP connection by ID
    		Connection rtpConnection = getConnection(String.valueOf(connectionId));
    		if(rtpConnection == null) {
    			throw new NullPointerException(String.format("RTP connection (ID=%d) was not found.", connectionId));
    		}

    		// Detach audio player
    		terminate();
    		
    		// Issue response based on connection availability
    		if(rtpConnection.isAvailable()) {
    			// Send a "100 - OK" to indicate connection is available
        		logger.info(String.format("(%s) The connection is available (connectionId=%s)", getEndpoint().getLocalName(), connectionIdHex));
        		oc.fire(this, new Text("rc=100"));
        	} else {
        		// Send a "300 - Unspecified failure" to indicate connection is not available
        		logger.info(String.format("(%s) The connection is not available (connectionId=%s)", getEndpoint().getLocalName(), connectionIdHex));
        		oc.fire(this, new Text("rc=300"));
        	}
		} catch (NumberFormatException e) {
			// Send a "301 - Bad audio ID" to indicate the connection ID is invalid 
			of.fire(this, new Text("rc=301"));
		} catch (NullPointerException e) {
			// Send a "303 - Bad selector value" to indicate no connection exists with such ID  
			of.fire(this, new Text("rc=303"));
		} finally {
			complete();
		}
    }

    private void startAnnouncementPhase() {
        logger.info(String.format("(%s) Start announcement (segment=%d)", getEndpoint().getLocalName(), segCount));
        
        try {
            player.setURL(uri);
        } catch (MalformedURLException e) {
        	logger.info("Received URL in invalid format , firing of");
            of.fire(this, new Text("rc=301"));
            complete();            
            return;
        } catch (ResourceUnavailableException e) {
        	logger.info("Received URL can not be found , firing of");
            of.fire(this, new Text("rc=312"));
            complete();
            return;
        }
        
        //set max duration if present
        if (options.getDuration() != -1) {
            player.setDuration(options.getDuration());
        }

        //set initial offset
        if (options.getOffset() > 0) {
            player.setMediaTime(options.getOffset());
        }

        //initial delay
        player.setInitialDelay(delay);

        //starting
        player.activate();
    }    
    
    @Override
    public boolean doAccept(Text event) {
        if (!oc.isActive() && oc.matches(event)) {
            return true;
        }

        if (!of.isActive() && of.matches(event)) {
            return true;
        }
        
        return false;
    }

    @Override
    public void cancel() {
    	terminate();                
    }

    private Player getPlayer() {
    	Endpoint endpoint = getEndpoint();
        return (Player) getEndpoint().getResource(MediaType.AUDIO, ComponentType.PLAYER);
    }
        
    @Override
    public void reset() {
        super.reset();
        terminate();
        
        oc.reset();
        of.reset();
        
    }
    
    private void terminate() 
    {    	
    	try
    	{
    		terminateSemaphore.acquire();
    	}
    	catch(InterruptedException e)
    	{
    		
    	}
    	
    	if (player != null) 
    	{    		
    		player.removeListener(this);
    		player.deactivate();
    		player=null;
        }    	    	
    	
    	if(options!=null)
    	{
    		Options.recycle(options);    	
    		options=null;
    	}
    	
    	terminateSemaphore.release();    	    	
    }
    
    private void repeat(long delay) {
        this.delay = delay;
        startAnnouncementPhase();
    }
    
    private void next(long delay) {
        uri = segments.next().toString();
        segCount++;
        
        this.delay = delay;
        startAnnouncementPhase();
    }

    public void process(PlayerEvent event) {
        switch (event.getID()) {
            case PlayerEvent.STOP :
                logger.info(String.format("(%s) Announcement (segment=%d) has completed", getEndpoint().getLocalName(), segCount));
                if(repeatCount==-1)
                {
                	repeat(options.getInterval());
            		return;            		                	
                }
                else
                {
                	repeatCount--;
                    
                	if (repeatCount > 0) {
                		repeat(options.getInterval());
                		return;
                	}
                }
                
                if (segments.hasNext()) {
                	repeatCount = options.getRepeatCount();
                    next(options.getInterval());
                    return;
                }
                
                terminate();
                oc.fire(this, new Text("rc=100"));
                this.complete();
                
                break;
            case PlayerEvent.FAILED :
            	terminate();
                oc.fire(this, null);
                this.complete();
        }
    }        
}
