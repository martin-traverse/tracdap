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
import org.finos.tracdap.common.codec.text.IJsonConsumer;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class CompositeArrayConsumer extends BaseCompositeConsumer {

    private final boolean useFieldNames;
    private final Map<String, Integer> fieldNameMap;

    private int currentElement;

    public CompositeArrayConsumer(List<IJsonConsumer<?>> delegates) {

        super(delegates);
        currentElement = -1;

        useFieldNames = false;
        fieldNameMap = null;
    }

    public CompositeArrayConsumer(List<IJsonConsumer<?>> delegates, boolean isCaseSensitive) {

        super(delegates);
        currentElement = -1;

        useFieldNames = true;
        fieldNameMap = buildFieldMap(delegates, isCaseSensitive);
    }

    private static Map<String, Integer> buildFieldMap(List<IJsonConsumer<?>> delegates, boolean isCaseSensitive) {

        var casedMap = IntStream.range(0, delegates.size())
                .boxed()
                .collect(Collectors.toMap(
                        i -> delegates.get(i).getVector().getName(),
                        i -> i));

        if (isCaseSensitive)
            return casedMap;

        else {

            var uncasedMap = new TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);
            uncasedMap.putAll(casedMap);

            return uncasedMap;
        }
    }

    @Override
    public boolean consumeElement(JsonParser parser) throws IOException {

        if (currentElement == -1) {
            if (parser.currentToken() == JsonToken.START_OBJECT)
                currentElement = 0;
            else
                throw new IllegalStateException();
        }

        var token = parser.nextValue();

        while (token != JsonToken.END_OBJECT) {

            // No data = wait for more
            if (token == null || token == JsonToken.NOT_AVAILABLE)
                return false;

            // Value available
            else if (token.isScalarValue() || token.isStructStart()) {

                var delegate = getFieldDelegate(parser);

                // Ignore unknown / extra fields
                if (delegate == null)
                    continue;

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

    IJsonConsumer<?> getFieldDelegate(JsonParser parser) throws IOException {

        if (useFieldNames) {

            var fieldName = parser.currentName();
            var fieldIndex = fieldNameMap.get(fieldName);

            if (fieldIndex != null)
                return delegates.get(fieldIndex);
        }
        else {

            if (currentElement >= 0 && currentElement < delegates.size())
                return delegates.get(currentElement);
        }

        return null;
    }
}
