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

import java.io.IOException;


public class BatchConsumer implements IBatchConsumer {

    private final ICompositeConsumer recordConsumer;
    private final ArrowVsrContext context;

    private final int currentBatchSize = 1000;
    private int currentIndex;

    public BatchConsumer(ICompositeConsumer recordConsumer, ArrowVsrContext context) {
        this.recordConsumer = recordConsumer;
        this.context = context;
        this.currentIndex = 0;
    }

    @Override
    public boolean consumeBatch(JsonParser parser) throws IOException {

        JsonToken token = parser.nextToken();

        while (currentIndex < currentBatchSize && token != JsonToken.END_ARRAY && token != null) {

            if (token == JsonToken.NOT_AVAILABLE)
                return false;

            var rowConsumed = recordConsumer.consumeElement(parser);

            if (!rowConsumed)
                return false;

            currentIndex++;

            token = parser.nextToken();
        }

        if (currentIndex > 0) {

            context.setRowCount(currentIndex);
            context.encodeDictionaries();
            context.setLoaded();
            currentIndex = 0;
        }

        return true;
    }

    @Override
    public void resetBatch(VectorSchemaRoot batch) {

        recordConsumer.resetVectors(batch.getFieldVectors());
        currentIndex = 0;
    }
}
