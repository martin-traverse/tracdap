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

package org.finos.tracdap.common.codec.consumer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.util.List;

public class CompositeArrayConsumer extends BaseCompositeConsumer {

    private int currentElement;

    public CompositeArrayConsumer(List<IJsonConsumer<?>> delegates) {
        super(delegates);
        currentElement = -1;
    }

    @Override
    public boolean consumeElement(JsonParser parser) throws IOException {

        if (currentElement == -1) {
            if (parser.currentToken() == JsonToken.START_OBJECT || parser.currentToken() == JsonToken.START_ARRAY)
                currentElement = 0;
            else
                throw new IllegalStateException();
        }

        var token = parser.nextValue();

        while (token != JsonToken.END_OBJECT && token != JsonToken.END_ARRAY) {

            // No data = wait for more
            if (token == null || token == JsonToken.NOT_AVAILABLE)
                return false;

            // Value available
            else if (token.isScalarValue() || token.isStructStart()) {

                // Ignore extra fields
                if (currentElement >= delegates.size())
                    continue;

                var delegate = delegates.get(currentElement);

                // Try to parse the next element
                if (delegate.consumeElement(parser))
                    currentElement++;
                else
                    return false;
            }

            else {
                throw new IllegalStateException();
            }

            token = parser.nextValue();
        }

        // Handle missing fields - set to null if possible, error otherwise
        while (currentElement < delegates.size()) {
            delegates.get(currentElement).setNull();
            currentElement++;
        }

        currentElement = -1;
        return true;
    }
}
