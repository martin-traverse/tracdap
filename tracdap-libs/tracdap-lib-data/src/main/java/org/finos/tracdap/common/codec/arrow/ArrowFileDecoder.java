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

package org.finos.tracdap.common.codec.arrow;

import org.apache.arrow.vector.VectorSchemaRoot;
import org.finos.tracdap.common.codec.ICodec;
import org.finos.tracdap.common.data.DataPipeline.*;
import org.finos.tracdap.common.data.pipeline.BaseDataProducer;

import org.apache.arrow.memory.ArrowBuf;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.ipc.ArrowFileAsyncReader;


public class ArrowFileDecoder
        extends BaseDataProducer<ArrowApi>
        implements FileApi, ICodec.Decoder<FileApi> {

    private static final int PRELOAD_CHUNKS = 2;

    private final BufferAllocator allocator;

    private FileSubscription subscription;
    private ArrowFileAsyncReader arrowReader;
    private VectorSchemaRoot root;

    private int pendingChunks;

    public ArrowFileDecoder(BufferAllocator allocator) {
        super(ArrowApi.class);
        this.allocator = allocator;
    }

    @Override
    public FileApi dataInterface() {
        return this;
    }

    @Override
    public boolean isReady() {
        return consumerReady();
    }

    @Override
    public void onStart(long fileSize, FileSubscription subscription) {

        this.subscription = subscription;
        this.arrowReader = new ArrowFileAsyncReader(allocator);

        var firstChunk = arrowReader.nextBytes();
        subscription.request(firstChunk.getOffset(), firstChunk.getSize());

        pendingChunks += 1;
    }

    @Override
    public void onChunk(long offset, ArrowBuf chunk) {

        pendingChunks -= 1;

        if (arrowReader == null) {
            chunk.close();
            return;
        }

        arrowReader.feedBytes(offset, chunk);

        pump();
    }

    @Override
    public void onError(Throwable error) {

    }

    @Override
    public void pump() {

        processData();

        if (arrowReader.bufferedChunks() < PRELOAD_CHUNKS)
            requestMore();
    }

    private void processData() {

    }

    private void requestMore() {

        while (arrowReader.bufferedChunks() + pendingChunks < PRELOAD_CHUNKS) {

            var nextChunk = arrowReader.nextBytes();

            if (nextChunk == null)
                return;

            subscription.request(nextChunk.getOffset(), nextChunk.getSize());
            pendingChunks += 1;
        }
    }

    @Override
    public void close() throws Exception {

        if (root != null) {
            root.close();
            root = null;
        }

        if (arrowReader != null) {
            arrowReader.close();;
            arrowReader = null;
        }

        if (subscription != null) {
            subscription.close();
            subscription = null;
        }
    }
}
