package org.mobicents.media.server.rtsp.controller.stack;

import gov.nist.core.StringTokenizer;
import gov.nist.javax.sdp.SessionDescriptionImpl;
import gov.nist.javax.sdp.fields.SDPField;
import gov.nist.javax.sdp.parser.ParserFactory;
import gov.nist.javax.sdp.parser.SDPParser;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.ReferenceCountUtil;

import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sdp.SessionDescription;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A future to handler reponse of request
 * 
 * @author chenxh
 */
public class ResponseFuture implements Future<HttpResponseStatus>,
		Callable<HttpResponseStatus> {
	private static final Logger logger = LoggerFactory.getLogger(ResponseFuture.class);
	
	private FullHttpRequest request;
	
	private HttpResponseStatus status;
	private HttpHeaders headers = HttpHeaders.EMPTY_HEADERS;
	private SessionDescription sessionDescription;
	private Throwable ex;

	final private FutureTask<HttpResponseStatus> task;

	public ResponseFuture(FullHttpRequest request) {
		this.request = request;
		this.task = new FutureTask<HttpResponseStatus>(this);
	}

	protected FullHttpRequest getRequest() {
		return request;
	}

	protected void setException(Throwable val) {
		this.ex = val;
		task.run();
	}
	
	protected void read(FullHttpResponse val) {
		// read content
		ByteBuf content = val.content();
		String contentType = val.headers().get("content-type");
		if (content.readableBytes() > 0
				&& StringUtils.contains(contentType, "sdp")) {
			String desciptor = content.toString(Charset.forName("UTF8"));
			SessionDescriptionImpl sd = new SessionDescriptionImpl();
			StringTokenizer tokenizer = new StringTokenizer(desciptor);
			while (tokenizer.hasMoreChars()) {
				String line = tokenizer.nextToken();

				try {
					SDPParser paser = ParserFactory.createParser(line);
					if (null != paser) {
						SDPField obj = paser.parse();
						sd.addField(obj);
					}
				} catch (ParseException e) {
					logger.warn("fail parse [{}]", line, e);
				}
			}
			this.sessionDescription = sd;
		}

		// read header(s) and status
		DefaultHttpHeaders headers = new DefaultHttpHeaders();
		headers.add(val.headers());
		this.headers = headers;
		this.status = val.getStatus();
		
		
		ReferenceCountUtil.release(val);
		task.run();
	}

	public HttpHeaders headers() {
		return headers;
	}
	
	public Transport getTransport() {
		String text = headers.get("Transport");
		if(null != text) {
			return new Transport(text);
		} else {
			return null;
		}
	}
	
	@Override
	public HttpResponseStatus call() throws Exception {
		if (ex instanceof Exception) {
			throw (Exception)ex;
		} else if (ex != null) {
			throw new ExecutionException(ex);
		}

		return status;
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return task.cancel(mayInterruptIfRunning);
	}

	@Override
	public boolean isCancelled() {
		return task.isCancelled();
	}

	@Override
	public boolean isDone() {
		return task.isDone();
	}

	@Override
	public HttpResponseStatus get() throws InterruptedException,
			ExecutionException {
		return task.get();
	}
	
	public SessionDescription getSessionDescription() {
		return sessionDescription;
	}

	@Override
	public HttpResponseStatus get(long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
		return task.get(timeout, unit);
	}
}
