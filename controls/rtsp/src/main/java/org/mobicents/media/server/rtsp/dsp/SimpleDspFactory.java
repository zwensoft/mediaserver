package org.mobicents.media.server.rtsp.dsp;

import java.util.List;

import org.mobicents.media.server.spi.dsp.DspFactory;
import org.mobicents.media.server.spi.dsp.Processor;

public class SimpleDspFactory implements DspFactory {

	@Override
	public Processor newProcessor() throws RuntimeException {
		return new SimpleProcessor();
	}

	@Override
	public void setCodecs(List<String> list) {

	}

}
