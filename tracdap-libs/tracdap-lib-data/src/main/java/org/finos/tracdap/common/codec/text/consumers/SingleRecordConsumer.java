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

package org.finos.tracdap.common.codec.text.consumers;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.finos.tracdap.common.codec.text.IBatchConsumer;
import org.finos.tracdap.common.codec.text.ICompositeConsumer;
import org.finos.tracdap.common.data.ArrowVsrContext;
import org.finos.tracdap.common.exception.EDataCorruption;

import java.io.IOException;

public class SingleRecordConsumer implements IBatchConsumer {

    private final ICompositeConsumer recordConsumer;
    private final ArrowVsrContext context;

    private boolean gotFirstToken;
    private boolean recordConsumed;

    public SingleRecordConsumer(ICompositeConsumer recordConsumer, ArrowVsrContext context) {
        this.recordConsumer = recordConsumer;
        this.context = context;
    }

    @Override
    public boolean consumeBatch(JsonParser parser) throws IOException {

        if (!gotFirstToken) {

            if (parser.nextToken() != JsonToken.START_OBJECT)
                throw new EDataCorruption("Unexpected token: " + parser.getCurrentToken());

            gotFirstToken = true;
        }

        if (recordConsumed)
            throw new IllegalStateException("Record has already been consumed");

        recordConsumed = recordConsumer.consumeElement(parser);

        if (!recordConsumed)
            return false;

        var nextToken = parser.nextToken();

        if (nextToken != null && nextToken != JsonToken.NOT_AVAILABLE)
            throw new EDataCorruption("Unexpected token: " + nextToken);

        context.setRowCount(1);
        context.encodeDictionaries();
        context.setLoaded();

        return true;
    }

    @Override
    public boolean endOfStream() {
        return false;
    }

    @Override
    public void resetBatch(VectorSchemaRoot batch) {

        recordConsumer.resetVectors(batch.getFieldVectors());
    }
}
