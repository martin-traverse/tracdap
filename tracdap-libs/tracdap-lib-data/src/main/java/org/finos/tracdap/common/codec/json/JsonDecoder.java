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

import org.finos.tracdap.common.codec.text.BaseTextDecoder;
import org.finos.tracdap.common.codec.text.BuildConsumers;
import org.finos.tracdap.common.codec.text.IBatchConsumer;
import org.finos.tracdap.common.codec.text.consumers.BatchConsumer;
import org.finos.tracdap.common.codec.text.consumers.CompositeObjectConsumer;
import org.finos.tracdap.common.data.ArrowVsrContext;
import org.finos.tracdap.common.data.ArrowVsrSchema;
import org.finos.tracdap.common.exception.EUnexpected;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonFactory;
import org.apache.arrow.memory.BufferAllocator;


public class JsonDecoder extends BaseTextDecoder {

    private static final boolean CASE_SENSITIVE = true;

    private static final JsonFactory jsonFactory = buildJsonFactory();

    public JsonDecoder(BufferAllocator arrowAllocator, ArrowVsrSchema arrowSchema) {

        super(arrowAllocator, arrowSchema);
    }

    @Override
    protected JsonFactory getParserFactory() {

        return jsonFactory;
    }

    private static JsonFactory buildJsonFactory() {

        return new JsonFactory();
    }

    @Override
    protected JsonParser configureParser(JsonParser parser) {

        // No special configuration needed
        return parser;
    }

    @Override
    protected IBatchConsumer createConsumer(ArrowVsrContext context) throws EUnexpected {

        var fieldVectors = context.getStagingVectors();
        var fieldConsumers = BuildConsumers.createConsumers(fieldVectors);

        var recordConsumer = new CompositeObjectConsumer(fieldConsumers, CASE_SENSITIVE);

        return new BatchConsumer(recordConsumer, context);
    }

}
