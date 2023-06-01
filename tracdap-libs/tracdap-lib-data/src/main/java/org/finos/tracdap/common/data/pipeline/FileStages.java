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

import org.apache.arrow.memory.ArrowBuf;
import org.finos.tracdap.common.data.DataPipeline;
import org.finos.tracdap.common.data.DataPipeline.*;
import org.finos.tracdap.common.storage.FileIO;

import java.util.concurrent.Flow;


public class FileStages {


    DataProducer<BufferApi> readAll(FileIO stream) {

        return readChunk(stream, 0, stream.size());
    }

    DataProducer<BufferApi> readChunk(FileIO stream, long offset, long size) {

        stream.position(offset);

        var read = stream.read(size);


    }

    DataProducer<StreamApi> readStream(DataPipelineImpl pipeline, FileIO stream) {

        var publisher = new FileReadStream(stream);

        return new ReactiveByteSource(pipeline, publisher);
    }

    DataProducer<FileApi> readFile(DataPipelineImpl pipeline, FileIO stream) {

        var publisher = new FileReadStream(stream);

        return new ReactiveByteSource(null, publisher);
    }

    DataConsumer<StreamApi> writeStream(DataPipelineImpl pipeline, FileIO stream) {

        var subscriber = new FileWriteStream(stream);

        return new ReactiveByteSink(pipeline, subscriber);
    }


    static class FileReader extends BaseDataProducer<FileApi>  {

        private final FileIO fileIO;
        private boolean started;

        public FileReader(FileIO fileIO) {
            super(FileApi.class);
            this.fileIO = fileIO;
        }

        @Override
        public boolean isReady() {
            return ! isDone();
        }

        @Override
        public void pump() {

            if (!started && consumerReady()) {
                started = true;
                consumer().onStart(fileIO.size(), new Subscription());
            }
        }

        @Override
        public void close() throws Exception {
            markAsDone();
            fileIO.close();
        }

        private class Subscription implements FileSubscription {

            @Override
            public void request(long offset, long size) {
                fileIO.position(offset);
                fileIO.read(size).whenComplete((chunk, error) -> readCallback(offset, chunk, error));
            }

            @Override
            public void close() {
                markAsDone();
                fileIO.close();
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

        private final FileIO fileIO;

        public FileReadStream(FileIO fileIO) {
            this.fileIO = fileIO;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ArrowBuf> subscriber) {

        }
    }

    static class FileWriteStream implements Flow.Subscriber<ArrowBuf> {

        private final FileIO fileIO;

        public FileWriteStream(FileIO fileIO) {
            this.fileIO = fileIO;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {

        }

        @Override
        public void onNext(ArrowBuf chunk) {
            fileIO.write(chunk);
        }

        @Override
        public void onError(Throwable throwable) {

        }

        @Override
        public void onComplete() {
            fileIO.flush().thenCompose

        }
    }
}
