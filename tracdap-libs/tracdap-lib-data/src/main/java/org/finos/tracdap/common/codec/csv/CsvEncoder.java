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

package org.finos.tracdap.common.codec.csv;

import org.finos.tracdap.common.codec.text.BaseTextEncoder;
import org.finos.tracdap.common.codec.text.BuildProducers;
import org.finos.tracdap.common.codec.text.IBatchProducer;
import org.finos.tracdap.common.codec.text.producers.BatchProducer;
import org.finos.tracdap.common.codec.text.producers.CompositeArrayProducer;
import org.finos.tracdap.common.data.ArrowVsrContext;

import org.apache.arrow.memory.BufferAllocator;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.dataformat.csv.CsvFactory;
import com.fasterxml.jackson.dataformat.csv.CsvGenerator;

import java.io.IOException;
import java.io.OutputStream;


public class CsvEncoder extends BaseTextEncoder {

    CsvEncoder(BufferAllocator allocator) {
        super(allocator);
    }

    @Override
    protected JsonGenerator createGenerator(ArrowVsrContext context, OutputStream out) throws IOException {

        var factory = new CsvFactory()
                // Make sure empty strings are quoted, so they can be distinguished from nulls
                .enable(CsvGenerator.Feature.ALWAYS_QUOTE_EMPTY_STRINGS);

        var arrowSchema = context.getSchema();

        var csvSchema = CsvSchemaMapping.arrowToCsv(arrowSchema.decoded())
                .build()
                .withHeader();

        var generator = factory.createGenerator(out, JsonEncoding.UTF8);
        generator.setSchema(csvSchema);

        return generator;
    }

    @Override
    protected IBatchProducer createProducer(ArrowVsrContext context) {

        var fieldProducers = BuildProducers.createProducers(
                context.getFrontBuffer().getFieldVectors(),
                context.getDictionaries());

        var recordProducer = new CompositeArrayProducer(fieldProducers);

        return new BatchProducer(recordProducer);
    }
}
