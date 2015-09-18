package org.mobicents.media.server.rtsp.controller;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;

import org.apache.log4j.Logger;
import org.apache.log4j.spi.Configurator;
import org.mobicents.media.core.ResourcesPool;
import org.mobicents.media.server.io.network.UdpManager;
import org.mobicents.media.server.rtsp.RtspEvent;
import org.mobicents.media.server.rtsp.RtspListener;
import org.mobicents.media.server.rtsp.RtspProvider;
import org.mobicents.media.server.scheduler.Scheduler;
import org.mobicents.media.server.spi.Endpoint;
import org.mobicents.media.server.spi.EndpointInstaller;
import org.mobicents.media.server.spi.MediaServer;
import org.mobicents.media.server.spi.ServerManager;
import org.mobicents.media.server.spi.listener.TooManyListenersException;

public class Controller implements RtspListener, ServerManager {

	private final static String HOME_DIR = "MMS_HOME";
	
    private final Logger logger = Logger.getLogger("RTSP");
    
    //network interface
    protected UdpManager udpInterface;
    
    //RTSP port number
    protected int port;
    
    protected Scheduler scheduler;
    
    //RTSP protocol provider
    protected RtspProvider rtspProvider;
       
    //server under control
    protected MediaServer server;
    
    protected ResourcesPool resourcesPool;
    
    //Endpoint configurator
    private Configurator configurator;
    
    
    protected int poolSize=10;
    
    /**
     * Assigns UDP network interface.
     * 
     * @param udpInterface  the UDP interface .
     */
    public void setUdpInterface(UdpManager udpInterface) {
        this.udpInterface = udpInterface;
    }
    
    /**
     * Assigns pool size.
     * 
     * @param poolSize the size of Transaction pool.
     */
    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }
    
    /**
     * Assigns RTSP port number.
     * 
     * @param port the port number.
     */
    public void setPort(int port) {
        this.port = port;
    }
    
    /**
     * Set server to control.
     * 
     * @param server the server instance.
     */
    public void setServer(MediaServer server) {
        logger.info("Set server");
        this.server = server;
        server.addManager(this);        
    }
    
    /**
     * Sets job scheduler.
     * 
     * @param scheduler the scheduler instance.
     */
    public void setScheduler(Scheduler scheduler) {
        logger.info("Set scheduler: " + scheduler);
        this.scheduler = scheduler;
    }
    
    /**
     * Modifies path to the configuration file.
     * The file must be present in the folder <MMS_HOME>/conf/ directory
     * 
     * @param fileName the name of the file.
     * @throws Exception 
     */
    public void setConfiguration(String url) throws Exception {
        try {
            if (url != null) {            	
                //getting the full path to the configuration file
            	String home = getHomeDir();
                
                if (home == null) {
                	throw new IOException(HOME_DIR + " not set");
                }
        
                String path = home + "/conf/" + url;        
                FileInputStream stream = new FileInputStream(path);
            }
        } catch (Exception e) {
            logger.error("Could not configure RTSP controller", e);
            throw e;
        }
    }
    
    public void setConfigurationByURL(URL url) throws Exception {
    	try {
    	} catch (Exception e) {
            logger.error("Could not configure RTSP controller", e);
            throw e;
        }
    }
    
    /**
    * Gets the Media Server Home directory.
    * 
    * @TODO This method duplicates the logic in org.mobicents.media.server.bootstrap.Main
    * 
    * @return the path to the home directory.
    */
    private static String getHomeDir() {
    	String mmsHomeDir = System.getProperty(HOME_DIR);
    	if (mmsHomeDir == null) {
    		mmsHomeDir = System.getenv(HOME_DIR);
    	};
    	return mmsHomeDir;
    }
    
    public void createProvider() {
    	rtspProvider = new RtspProvider(udpInterface, port, scheduler, resourcesPool);
    }
    
    
    /**
     * Starts controller.
     */
    public void start() {
        logger.info("Starting RTSP controller");

        logger.info("Starting RTSP provider");
        
        createProvider();  
        rtspProvider.activate();
        
        try {
            rtspProvider.addListener(this);
        } catch (TooManyListenersException e) {
        	logger.error(e);
        }

        
        logger.info("Controller started");
    }
    
    /**
     * Stops controller.
     */
    public void stop() {
        rtspProvider.shutdown();
        logger.info("Controller stopped");
    }

    public void process(RtspEvent event) {
			
    }
     
    public void onStarted(Endpoint endpoint,EndpointInstaller installer) {
        try {
            logger.info("Endpoint restarted: " + endpoint.getLocalName());
        } catch (Exception e) {
        	logger.error("Could not register endpoint: " + endpoint.getLocalName());
        }
    }

    public void onStopped(Endpoint endpoint) {
    }
    
}