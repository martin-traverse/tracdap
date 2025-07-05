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
import org.finos.tracdap.common.codec.text.ICompositeConsumer;
import org.finos.tracdap.common.data.ArrowVsrContext;

import java.io.IOException;


public class ArrayConsumer {

    private final ArrowVsrContext context;
    private final ICompositeConsumer recordConsumer;

    private final int batchSize = 1000;
    private int batchRow;

    public ArrayConsumer(ArrowVsrContext context, ICompositeConsumer recordConsumer) {
        this.context = context;
        this.recordConsumer = recordConsumer;
        this.batchRow = 0;
    }

    boolean consumeBatch(JsonParser parser) throws IOException {

        JsonToken token = parser.nextToken();

        while (batchRow < batchSize && token != JsonToken.END_ARRAY && token != null) {

            if (token == JsonToken.NOT_AVAILABLE)
                return false;

            var rowConsumed = recordConsumer.consumeElement(parser);

            if (!rowConsumed)
                return false;

            batchRow++;

            token = parser.nextToken();
        }

        if (batchRow > 0) {

            context.setRowCount(batchRow);
            context.encodeDictionaries();
            context.setLoaded();
            batchRow = 0;
        }

        return true;
    }
}
