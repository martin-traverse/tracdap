/*
 * Copyright 2022 Accenture Global Solutions Limited
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

package org.finos.tracdap.common.codec.arrow;

import org.finos.tracdap.common.codec.ICodec;
import org.finos.tracdap.common.data.DataPipeline;
import org.finos.tracdap.common.data.util.ByteSeekableChannel;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.ipc.ArrowFileReader;
import org.apache.arrow.vector.ipc.ArrowReader;

import io.netty.buffer.ByteBuf;


public class ArrowFileDecoder
        extends ArrowDecoder
        implements
        ICodec.Decoder<DataPipeline.RangeApi>,
        DataPipeline.RangeApi {

    private final BufferAllocator arrowAllocator;

    public ArrowFileDecoder(BufferAllocator arrowAllocator) {
        this.arrowAllocator = arrowAllocator;
    }

    protected ArrowReader createReader(ByteBuf buffer) {
        var channel = new ByteSeekableChannel(buffer);
        return new ArrowFileReader(channel, arrowAllocator);
    }


    @Override
    public void onStart(DataPipeline.RangeSubscription subscription) {

    }

    @Override
    public void onChunk(ByteBuf chunk, long offset) {

    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public DataPipeline.RangeApi dataInterface() {
        return null;
    }
}
