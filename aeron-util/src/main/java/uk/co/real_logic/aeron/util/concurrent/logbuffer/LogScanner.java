/*
 * Copyright 2014 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.aeron.util.concurrent.logbuffer;

import uk.co.real_logic.aeron.util.concurrent.AtomicBuffer;

import java.nio.ByteOrder;

import static uk.co.real_logic.aeron.util.BitUtil.align;
import static uk.co.real_logic.aeron.util.concurrent.logbuffer.FrameDescriptor.*;
import static uk.co.real_logic.aeron.util.concurrent.logbuffer.LogBufferDescriptor.*;

/**
 * Cursor that scans a log buffer reading MTU (Maximum Transmission Unit) ranges of messages
 * as they become available due to the tail progressing. This scanner makes the assumption that
 * the buffer is built in an append only fashion with no gaps.
 *
 * <b>Note:</b> An instance of this class is not threadsafe. Each thread must have its own instance.
 */
public class LogScanner
{
    /**
     * Handler for notifying availability from latest offset.
     */
    @FunctionalInterface
    public interface AvailabilityHandler
    {
        void onAvailable(final int offset, final int length);
    }

    private final AtomicBuffer logBuffer;
    private final AtomicBuffer stateBuffer;
    private final int alignedHeaderLength;
    private final int capacity;

    private int offset = 0;

    /**
     * Construct a reader that iterates over a log buffer with associated state buffer. Messages are identified as
     * they become available up to the MTU limit.
     *
     * @param logBuffer containing the framed messages.
     * @param stateBuffer containing the state variables indicating the tail progress.
     * @param headerLength of frame before payload begins.
     */
    public LogScanner(final AtomicBuffer logBuffer,
                      final AtomicBuffer stateBuffer,
                      final int headerLength)
    {
        checkLogBuffer(logBuffer);
        checkStateBuffer(stateBuffer);
        checkHeaderLength(headerLength);

        this.logBuffer = logBuffer;
        this.stateBuffer = stateBuffer;
        alignedHeaderLength = align(headerLength, FRAME_ALIGNMENT);
        capacity = logBuffer.capacity();
    }

    /**
     * The capacity of the underlying log buffer.
     *
     * @return the capacity of the underlying log buffer.
     */
    public int capacity()
    {
        return capacity;
    }

    /**
     * The offset at which the next frame begins.
     *
     * @return the offset at which the next frame begins.
     */
    public int offset()
    {
        return offset;
    }

    /**
     * Is the scanning of the log buffer complete?
     *
     * @return is the scanning of the log buffer complete?
     */
    public boolean isComplete()
    {
        return offset >= capacity;
    }

    /**
     * Scan forward in the buffer for available frames limited by what will fit in limit.
     *
     * @param limit in bytes to scan.
     * @param handler called back if a frame is available.
     * @return number of frames available
     */
    public int scanNext(final int limit, final AvailabilityHandler handler)
    {
        int frameCount = 0;

        if (!isComplete())
        {
            final int tail = tail();
            final int offset = this.offset;
            if (tail > offset)
            {
                int length = 0;
                int padding = 0;

                do
                {
                    int alignedFrameLength = align(waitForFrameLength(offset + length), FRAME_ALIGNMENT);

                    if (PADDING_FRAME_TYPE == frameType(offset + length))
                    {
                        padding = alignedFrameLength - alignedHeaderLength;
                        alignedFrameLength = alignedHeaderLength;
                    }

                    length += alignedFrameLength;

                    if (length > limit)
                    {
                        length -= alignedFrameLength;
                        break;
                    }

                    ++frameCount;
                }
                while ((offset + length + padding) < tail);

                if (length > 0)
                {
                    this.offset += (length + padding);
                    handler.onAvailable(offset, length);
                }
            }
        }

        return frameCount;
    }

    /**
     * Seek within a log buffer and get ready for the next scan.
     *
     * @param offset in the log buffer to seek to for next scan.
     * @throws IllegalStateException if the offset is beyond the tail.
     */
    public void seek(final int offset)
    {
        final int tail = tail();
        if (offset < 0 || offset > tail)
        {
            throw new IllegalStateException(String.format("Invalid offset %d: range is 0 - %d", offset, tail));
        }

        this.offset = offset;
    }

    private int waitForFrameLength(final int frameOffset)
    {
        return FrameDescriptor.waitForFrameLength(logBuffer, frameOffset);
    }

    private int frameType(final int frameOffset)
    {
        return logBuffer.getShort(typeOffset(frameOffset), ByteOrder.LITTLE_ENDIAN) & 0xFFFF;
    }

    private int tail()
    {
        return stateBuffer.getIntVolatile(LogBufferDescriptor.TAIL_COUNTER_OFFSET);
    }
}
