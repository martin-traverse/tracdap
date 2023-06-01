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

package org.finos.tracdap.common.data.pipeline;

import io.netty.channel.ChannelException;
import org.apache.arrow.memory.ArrowBuf;
import org.finos.tracdap.common.data.DataPipeline.*;
import org.finos.tracdap.common.storage.IFileChannel;

import java.io.IOException;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;


public class FileStages {


    DataProducer<BufferApi> readAll(IFileChannel stream) {

        return readChunk(stream, 0, stream.size());
    }

    DataProducer<BufferApi> readChunk(IFileChannel stream, long offset, long size) {

        stream.position(offset);

        var read = stream.read(size);


    }

    DataProducer<StreamApi> readStream(DataPipelineImpl pipeline, IFileChannel stream) {

        var publisher = new FileReadStream(stream);

        return new ReactiveByteSource(pipeline, publisher);
    }

    DataProducer<FileApi> readFile(DataPipelineImpl pipeline, IFileChannel stream) {

        var publisher = new FileReadStream(stream);

        return new ReactiveByteSource(null, publisher);
    }

    DataConsumer<StreamApi> writeStream(DataPipelineImpl pipeline, IFileChannel stream) {

        var subscriber = new FileWriteStream(stream);

        return new ReactiveByteSink(pipeline, subscriber);
    }


    static class FileReader extends BaseDataProducer<FileApi>  {

        private final IFileChannel IFileChannel;
        private boolean started;

        public FileReader(IFileChannel IFileChannel) {
            super(FileApi.class);
            this.IFileChannel = IFileChannel;
        }

        @Override
        public boolean isReady() {
            return ! isDone();
        }

        @Override
        public void pump() {

            if (!started && consumerReady()) {
                started = true;
                consumer().onStart(IFileChannel.size(), new Subscription());
            }
        }

        @Override
        public void close() throws Exception {
            markAsDone();
            IFileChannel.close();
        }

        private class Subscription implements FileSubscription {

            @Override
            public void request(long offset, long size) {
                IFileChannel.position(offset);
                IFileChannel.read(size).whenComplete((chunk, error) -> readCallback(offset, chunk, error));
            }

            @Override
            public void close() {
                markAsDone();
                IFileChannel.close();
            }
        }

        private void readCallback(long offset, ArrowBuf chunk, Throwable error) {
            if (error != null)
                consumer().onError(error);
            else
                consumer().onChunk(offset, chunk);
        }
    }


    static class FileReadStream implements Flow.Publisher<ArrowBuf> {

        private final IFileChannel channel;
        private boolean readPending;
        private boolean closePending;

        public FileReadStream(IFileChannel channel) {
            this.channel = channel;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ArrowBuf> subscriber) {

        }

        private class Subscription extends Flow.Subscription {

            @Override
            public void request(long n) {

            }

            @Override
            public void cancel() {

            }
        }
    }

    static class FileWriteStream implements Flow.Subscriber<ArrowBuf> {

        private final CompletableFuture<Long> signal;
        private final IFileChannel channel;
        private final Callback callback;

        private Flow.Subscription subscription;
        private boolean writePending;
        private boolean closePending;

        public FileWriteStream(IFileChannel channel, CompletableFuture<Long> signal) {
            this.signal = signal;
            this.channel = channel;
            this.callback = new Callback();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(ArrowBuf chunk) {

            try {
                writePending = true;
                var buffer = chunk.nioBuffer().asReadOnlyBuffer();
                channel.write(buffer, chunk, callback);
            }
            catch (ChannelException e) {

            }
        }

        @Override
        public void onError(Throwable throwable) {

            try {
                channel.close();
            }
            catch (IOException e) {

            }
        }

        @Override
        public void onComplete() {

            try {
                if (writePending)
                    closePending = true;
                else
                    channel.close();
            }
            catch (IOException e) {

            }
        }

        private class Callback implements CompletionHandler<Integer, ArrowBuf> {

            @Override
            public void completed(Integer nBytes, ArrowBuf chunk) {
                writePending = false;
                if (closePending)
                    channel.close();
                else
                    subscription.request(1);

            }

            @Override
            public void failed(Throwable error, ArrowBuf chunk) {

                try (chunk) {
                    writePending = false;
                    signal.completeExceptionally(error);
                }
                finally {
                    channel.close();
                    chunk.close();
                }
            }
        }
    }
}
