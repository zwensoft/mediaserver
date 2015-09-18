package org.mobicents.media.server.rtsp.dsp;

import org.mobicents.media.server.spi.dsp.Codec;
import org.mobicents.media.server.spi.dsp.Processor;
import org.mobicents.media.server.spi.format.Format;
import org.mobicents.media.server.spi.memory.Frame;

public class SimpleProcessor implements Processor {

	@Override
	public Codec[] getCodecs() {
		return null;
	}

	@Override
	public Frame process(Frame frame, Format source, Format destination) {
		return frame;
	}

}
