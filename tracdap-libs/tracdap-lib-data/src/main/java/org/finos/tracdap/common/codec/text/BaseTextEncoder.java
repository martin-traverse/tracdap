/*
 * Licensed to the Fintech Open Source Foundation (FINOS) under one or
 * more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * FINOS licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.finos.tracdap.common.codec.text;

import com.fasterxml.jackson.core.JsonFactory;
import org.finos.tracdap.common.codec.StreamingEncoder;
import org.finos.tracdap.common.codec.text.producers.BatchProducer;
import org.finos.tracdap.common.codec.text.producers.CompositeObjectProducer;
import org.finos.tracdap.common.codec.text.producers.SingleRecordProducer;
import org.finos.tracdap.common.data.ArrowVsrContext;
import org.finos.tracdap.common.data.util.ByteOutputStream;
import org.finos.tracdap.common.exception.EUnexpected;

import org.apache.arrow.memory.BufferAllocator;
import com.fasterxml.jackson.core.JsonGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.BiConsumer;


public class BaseTextEncoder extends StreamingEncoder implements AutoCloseable {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final BufferAllocator allocator;
    private final JsonFactory jsonFactory;
    private final BiConsumer<JsonGenerator, ArrowVsrContext> configureGenerator;

    private ArrowVsrContext context;
    private OutputStream out;
    private JsonGenerator generator;
    private IBatchProducer producer;

    public BaseTextEncoder(
            BufferAllocator allocator, JsonFactory jsonFactory,
            BiConsumer<JsonGenerator, ArrowVsrContext> configureGenerator) {

        this.allocator = allocator;
        this.jsonFactory = jsonFactory;
        this.configureGenerator = configureGenerator;
    }

    @Override
    public void onStart(ArrowVsrContext context) {

        try {

            if (log.isTraceEnabled())
                log.trace("JSON ENCODER: onStart()");

            consumer().onStart();

            this.context = context;

            this.out = new ByteOutputStream(allocator, consumer()::onNext);
            this.generator = jsonFactory.createGenerator(out);

            if (configureGenerator != null)
                configureGenerator.accept(this.generator, context);

            this.producer = createProducer(context);

            producer.produceStart(generator);
        }
        catch (IOException e) {

            // Output stream is writing to memory buffers, IO errors are not expected
            log.error("Unexpected error writing to codec buffer: {}", e.getMessage(), e);

            close();

            throw new EUnexpected(e);
        }
    }

    private IBatchProducer createProducer(ArrowVsrContext context) {

        var fieldProducers = BuildProducers.createProducers(
                context.getFrontBuffer().getFieldVectors(),
                context.getDictionaries());

        var recordProducer = new CompositeObjectProducer(fieldProducers);

        if (context.getSchema().isSingleRecord())
            return new SingleRecordProducer(recordProducer);
        else
            return new BatchProducer(recordProducer);
    }

    @Override
    public void onBatch() {

        try {

            if (log.isTraceEnabled())
                log.trace("JSON ENCODER: onNext()");

            var batch = context.getFrontBuffer();

            producer.resetBatch(batch);
            producer.produceBatch(generator);

            context.setUnloaded();
        }
        catch (IOException e) {

            // Output stream is writing to memory buffers, IO errors are not expected
            log.error("Unexpected error writing to codec buffer: {}", e.getMessage(), e);

            close();

            throw new EUnexpected(e);
        }
    }

    @Override
    public void onComplete() {

        try {

            if (log.isTraceEnabled())
                log.trace("JSON ENCODER: onComplete()");

            producer.produceEnd(generator);

            // Flush and close output

            producer = null;

            generator.close();
            generator = null;

            out.flush();
            out = null;

            markAsDone();
            consumer().onComplete();
        }
        catch (IOException e) {

            // Output stream is writing to memory buffers, IO errors are not expected
            log.error("Unexpected error writing to codec buffer: {}", e.getMessage(), e);
            throw new EUnexpected(e);
        }
        finally {

            close();
        }
    }

    @Override
    public void onError(Throwable error) {

        try {

            if (log.isTraceEnabled())
                log.trace("JSON ENCODER: onError()");

            markAsDone();
            consumer().onError(error);
        }
        finally {
            close();
        }
    }

    @Override
    public void close() {

        try {

            if (producer != null) {
                producer = null;
            }

            if (generator != null) {
                generator.close();
                generator = null;
            }

            if (out != null) {
                out.close();
                out = null;
            }

            // Encoder does not own context, do not close it

            if (context != null) {
                context = null;
            }
        }
        catch (IOException e) {

            // Output stream is writing to memory buffers, IO errors are not expected
            log.error("Unexpected error closing encoder: {}", e.getMessage(), e);
            throw new EUnexpected(e);
        }
    }
}
