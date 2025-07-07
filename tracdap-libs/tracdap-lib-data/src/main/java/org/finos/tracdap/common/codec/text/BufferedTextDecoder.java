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

import org.finos.tracdap.common.codec.BufferDecoder;
import org.finos.tracdap.common.codec.text.consumers.BatchConsumer;
import org.finos.tracdap.common.codec.text.consumers.CompositeObjectConsumer;
import org.finos.tracdap.common.codec.text.consumers.SingleRecordConsumer;
import org.finos.tracdap.common.data.ArrowVsrContext;
import org.finos.tracdap.common.data.util.ByteSeekableChannel;
import org.finos.tracdap.common.data.util.Bytes;
import org.finos.tracdap.common.exception.EDataCorruption;
import org.finos.tracdap.common.exception.ETrac;
import org.finos.tracdap.common.exception.ETracInternal;
import org.finos.tracdap.common.exception.EUnexpected;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import org.apache.arrow.memory.ArrowBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.Channels;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;


public class BufferedTextDecoder extends BufferDecoder {

    private static final int BATCH_SIZE = 1024;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ArrowVsrContext context;
    private final JsonFactory jsonFactory;
    private final BiConsumer<JsonParser, ArrowVsrContext> configureParser;

    private List<ArrowBuf> buffer;
    private JsonParser csvParser;
    private IBatchConsumer csvConsumer;

    public BufferedTextDecoder(
            ArrowVsrContext context, JsonFactory jsonFactory,
            BiConsumer<JsonParser, ArrowVsrContext> configureParser) {

        // Schema cannot be inferred from text formats
        // Context must always be pre-configured from a TRAC schema

        this.context = context;
        this.jsonFactory = jsonFactory;
        this.configureParser = configureParser;
    }

    @Override
    public void onBuffer(List<ArrowBuf> buffer) {

        if (log.isTraceEnabled())
            log.trace("CSV DECODER: onBuffer()");

        // Sanity check, should never happen
        if (isDone() || this.buffer != null) {
            var error = new ETracInternal("CSV data parsed twice (this is a bug)");
            log.error(error.getMessage(), error);
            throw error;
        }

        // Empty file can and does happen, treat it as data corruption
        if (Bytes.readableBytes(buffer) == 0) {
            var error = new EDataCorruption("CSV data is empty");
            log.error(error.getMessage(), error);
            throw error;
        }

        handleErrors(() -> {

            // Set up all the resources needed for parsing

            this.buffer = buffer;

            var channel = new ByteSeekableChannel(buffer);
            var stream = Channels.newInputStream(channel);
            this.csvParser = jsonFactory.createParser(stream);

            if (configureParser != null)
                configureParser.accept(csvParser, context);

            this.csvConsumer = createConsumer(context);

            consumer().onStart(context);

            // Call the parsing function - this may result in a partial parse

            var isComplete = doParse(csvParser, context, csvConsumer);

            // If the parse is done, emit the EOS signal and clean up resources
            // Otherwise, wait until a callback on pump()

            if (isComplete) {
                markAsDone();
                consumer().onComplete();
                close();
            }

            return null;
        });
    }

    private IBatchConsumer createConsumer(ArrowVsrContext context) {

        var fieldVectors = context.getStagingVectors();
        var fieldConsumers = BuildConsumers.createConsumers(fieldVectors);

        var recordConsumer = new CompositeObjectConsumer(fieldConsumers, /* caseSensitive = */ true);

        if (context.getSchema().isSingleRecord())
            return new SingleRecordConsumer(recordConsumer, context);
        else
            return new BatchConsumer(recordConsumer, context);
    }

    @Override
    public void onError(Throwable error) {

        try {

            if (log.isTraceEnabled())
                log.trace("CSV DECODER: onError()");

            markAsDone();
            consumer().onError(error);
        }
        finally {
            close();
        }
    }

    @Override
    public void pump() {

        // Don't try to pump if the data hasn't arrived yet, or if it has already gone
        if (csvParser == null || context == null)
            return;

        handleErrors(() -> {

            var isComplete = doParse(csvParser, context, csvConsumer);

            // If the parse is done, emit the EOS signal and clean up resources
            // Otherwise, wait until a callback on pump()

            if (isComplete) {
                markAsDone();
                consumer().onComplete();
                close();
            }

            return null;
        });
    }

    boolean doParse(JsonParser parser, ArrowVsrContext context, IBatchConsumer consumer) throws Exception {

        do {

            if (context.readyToFlip())
                context.flip();

            if (context.readyToUnload() && consumerReady())
                consumer().onBatch();

            if (context.readyToLoad() && ! consumer.endOfStream()) {
                if (consumer.consumeBatch(parser))
                    context.setLoaded();
                else
                    return false;
            }

        } while (context.readyToFlip());

        return consumer.endOfStream();
    }

    void handleErrors(Callable<Void> parseFunc) {

        try {
            parseFunc.call();
        }
        catch (ETrac e) {

            // Error has already been handled, propagate as-is

            var errorMessage = "CSV decoding failed: " + e.getMessage();

            log.error(errorMessage, e);
            throw e;
        }
        catch (JacksonException e) {

            // This exception is a "well-behaved" parse failure, parse location and message should be meaningful

            var errorMessage = String.format("CSV decoding failed on line %d: %s",
                    e.getLocation().getLineNr(),
                    e.getOriginalMessage());

            log.error(errorMessage, e);
            throw new EDataCorruption(errorMessage, e);
        }
        catch (IOException e) {

            // Decoders work on a stream of buffers, "real" IO exceptions should not occur
            // IO exceptions here indicate parse failures, not file/socket communication errors
            // This is likely to be a more "badly-behaved" failure, or at least one that was not anticipated

            var errorMessage = "CSV decoding failed, content is garbled: " + e.getMessage();

            log.error(errorMessage, e);
            throw new EDataCorruption(errorMessage, e);
        }
        catch (Throwable e)  {

            // Ensure unexpected errors are still reported to the Flow API

            log.error("Unexpected error in CSV decoding", e);
            throw new EUnexpected(e);
        }
    }

    @Override
    public void close() {

        try {

            context.close();

            if (csvParser != null) {
                csvParser.close();
                csvParser = null;
            }

            if (buffer != null) {
                buffer.forEach(ArrowBuf::close);
                buffer = null;
            }
        }
        catch (IOException e) {
            throw new ETracInternal("Unexpected error shutting down the CSV parser: " + e.getMessage(), e);
        }
    }
}
