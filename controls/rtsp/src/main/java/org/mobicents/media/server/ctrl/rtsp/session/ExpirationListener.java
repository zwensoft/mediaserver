package org.mobicents.media.server.ctrl.rtsp.session;

/**
 * A listener for expired object events.
 */
public interface ExpirationListener<E> {
  void expired(E expiredObject);
}
