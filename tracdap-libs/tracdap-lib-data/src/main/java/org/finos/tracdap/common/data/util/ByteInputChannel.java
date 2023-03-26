/*
 * Copyright 2023 Accenture Global Solutions Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.finos.tracdap.common.data.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;


public class ByteInputChannel implements ReadableByteChannel {

    private final CompositeByteBuf buffer;

    public ByteInputChannel() {

        buffer = Unpooled.compositeBuffer();
    }

    public void feedBytes(ByteBuf chunk) {

        buffer.addComponent(true, chunk);
    }

    public int readableBytes() {
        return buffer.readableBytes();
    }

    public void peek(ByteBuffer dst, int skip, int size) throws IOException {

        if (skip + size > buffer.readableBytes())
            throw new IOException("Data not available on input channel");

        var offset = buffer.readerIndex() + skip;

        buffer.getBytes(offset, dst);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {

        var startPosition = buffer.readerIndex();
        buffer.readBytes(dst);
        var endPosition = buffer.readerIndex();

        buffer.discardReadComponents();

        return endPosition - startPosition;
    }

    @Override
    public boolean isOpen() {
        return buffer.refCnt() > 0;
    }

    @Override
    public void close() throws IOException {
        buffer.release();
    }
}
