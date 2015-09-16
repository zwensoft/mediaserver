package org.mobicents.media.server.ctrl.rtsp.session;


public class BasicSessionListener implements RtspSessionListener {
  // private static final Logger logger = LoggerFactory.getLogger(BasicSessionListener.class);

  @Override
  public void sessionCreated(RtspSession rtspSession) {}

  @Override
  public void sessionDestroyed(RtspSession rtspSession) {}

  @Override
  public void sessionExpired(RtspSession rtspSession) {}
}
