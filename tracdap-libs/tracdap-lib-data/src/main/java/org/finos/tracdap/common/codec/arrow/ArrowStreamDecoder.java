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
import org.finos.tracdap.common.data.util.ByteInputChannel;

import org.apache.arrow.flatbuf.MessageHeader;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.ipc.ArrowStreamReader;

import io.netty.buffer.ByteBuf;
import org.finos.tracdap.common.exception.EUnexpected;


public class ArrowStreamDecoder
        extends ArrowDecoder
        implements
        ICodec.Decoder<DataPipeline.StreamApi>,
        DataPipeline.StreamApi {

    private final ArrowStreamMessageReader messageReader;
    private final ArrowStreamReader arrowReader;

    private boolean schemaDone;


    public ArrowStreamDecoder(BufferAllocator arrowAllocator) {

        var inputChannel = new ByteInputChannel();
        this.messageReader = new ArrowStreamMessageReader(inputChannel, arrowAllocator);
        this.arrowReader = new ArrowStreamReader(messageReader, arrowAllocator);
    }

    @Override
    public DataPipeline.StreamApi dataInterface() {
        return this;
    }

    @Override
    public boolean isReady() {
        return consumerReady();
    }

    @Override
    public void onStart() {

        onReader(arrowReader);
    }

    @Override
    public void onNext(ByteBuf chunk) {

        handleErrors(() -> {

            messageReader.feedBytes(chunk);

            if (!schemaDone) {
                if (messageReader.messageAvailable(MessageHeader.Schema)) {
                    schemaDone = true;
                    onSchema();
                } else {
                    return null;
                }
            }

            while (messageReader.messageAvailable(MessageHeader.RecordBatch)) {
                onBatch();
            }

            return null;
        });
    }

    @Override
    public void onComplete() {

        super.onComplete();
    }

    @Override
    public void onError(Throwable error) {

        super.onError(error);
    }

    @Override
    public void close() {

        try {

            arrowReader.close();
            messageReader.close();
            super.close();
        }
        catch (Exception e) {

            //log.error("Unexpected error while shutting down Arrow stream decoder", e);
            throw new EUnexpected(e);
        }
    }

}
