/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
 * by the @authors tag. 
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

package org.mobicents.media.server.component;

import org.mobicents.media.server.concurrent.ConcurrentCyclicFIFO;
import org.mobicents.media.server.impl.AbstractSink;
import org.mobicents.media.server.impl.AbstractSource;
import org.mobicents.media.server.scheduler.Scheduler;
import org.mobicents.media.server.spi.memory.Frame;

/**
 * Implements output for compound components.
 * 
 * @author Yulian Oifa
 * @author Henrique Rosa (henrique.rosa@telestax.com)
 */
public class MediaOutput extends AbstractSource {

    private static final long serialVersionUID = 841004045995318464L;

    private static final String PREFIX_NAME = "compound.output.";

    // Media output properties
    private final int outputId;
    private final ConcurrentCyclicFIFO<Frame> buffer;

    public MediaOutput(int outputId, Scheduler scheduler) {
        super(PREFIX_NAME + outputId, scheduler, Scheduler.OUTPUT_QUEUE);

        // Media output properties
        this.outputId = outputId;
        this.buffer = new ConcurrentCyclicFIFO<Frame>();
    }

    public int getOutputId() {
        return outputId;
    }

    private void resetBuffer() {
        while (buffer.size() > 0) {
            buffer.poll().recycle();
        }
    }

    public void offer(Frame frame) {
        if (isStarted()) {
            buffer.offer(frame);
        }
    }

    public void join(AbstractSink sink) {
        connect(sink);
    }

    public void unjoin() {
        disconnect();
    }

    @Override
    public Frame evolve(long timestamp) {
        return buffer.poll();
    }

    @Override
    public void stop() {
        super.stop();
        resetBuffer();
    }

}
