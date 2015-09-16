package org.mobicents.media.server.ctrl.rtsp.config;

import java.util.Properties;

import org.apache.log4j.Logger;
import org.mobicents.media.server.ctrl.rtsp.RtspController;
import org.mobicents.media.server.io.network.UdpManager;
import org.mobicents.media.server.scheduler.Scheduler;

public class ServerConfig {
  public static final Logger logger = Logger.getLogger(ServerConfig.class);
  private static final String PROPERTY_RTSP_SERVER_IP = "rtsp.server.ip";
  private static final String PROPERTY_RTSP_SERVER_PORT = "rtsp.server.port";

  private Properties environment;

  public RtspController rtspServer(Properties environment) {
	  this.environment = environment;
    String ip = environment.getProperty(PROPERTY_RTSP_SERVER_IP);
    int port = Integer.parseInt(environment.getProperty(PROPERTY_RTSP_SERVER_PORT));

    
    RtspController rtspController = null;
    try {
    	Scheduler scheduler = new Scheduler();
    	UdpManager manager = new UdpManager(scheduler);
      rtspController = new RtspController(scheduler, manager);
      rtspController.setIp(ip);
      rtspController.setPort(port);
    } catch (Exception e) {
      logger.error("Create RtspServer Error.........", e);
    }
    return rtspController;
  }

  public String getIp() {
    return environment.getProperty(PROPERTY_RTSP_SERVER_IP);
  }

  public int getPort() {
    return Integer.parseInt(environment.getProperty(PROPERTY_RTSP_SERVER_PORT));
  }
}
