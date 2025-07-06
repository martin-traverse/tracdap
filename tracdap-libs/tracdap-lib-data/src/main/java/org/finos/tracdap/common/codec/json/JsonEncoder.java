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

package org.finos.tracdap.common.codec.json;

import org.finos.tracdap.common.codec.text.BaseTextEncoder;
import org.finos.tracdap.common.codec.text.BuildProducers;
import org.finos.tracdap.common.codec.text.IBatchProducer;
import org.finos.tracdap.common.codec.text.producers.BatchProducer;
import org.finos.tracdap.common.codec.text.producers.CompositeObjectProducer;
import org.finos.tracdap.common.data.ArrowVsrContext;

import org.apache.arrow.memory.BufferAllocator;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.io.OutputStream;


public class JsonEncoder extends BaseTextEncoder {

    public JsonEncoder(BufferAllocator allocator) {
        super(allocator);
    }

    @Override
    protected JsonGenerator createGenerator(ArrowVsrContext context, OutputStream out) throws IOException {

        var factory = new JsonFactory();
        return factory.createGenerator(out, JsonEncoding.UTF8);
    }

    @Override
    protected IBatchProducer createProducer(ArrowVsrContext context) {

        var fieldProducers = BuildProducers.createProducers(
                context.getFrontBuffer().getFieldVectors(),
                context.getDictionaries());

        var recordProducer = new CompositeObjectProducer(fieldProducers);

        return new BatchProducer(recordProducer);
    }
}
