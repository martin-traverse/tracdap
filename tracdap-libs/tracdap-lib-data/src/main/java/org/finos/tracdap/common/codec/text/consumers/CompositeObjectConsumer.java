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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.finos.tracdap.common.codec.text.IJsonConsumer;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class CompositeObjectConsumer extends BaseCompositeConsumer {

    private final boolean useFieldNames;
    private final Map<String, Integer> fieldNameMap;

    private final boolean[] consumedFields;
    private int currentFieldIndex;

    public CompositeObjectConsumer(List<IJsonConsumer<?>> delegates, boolean isCaseSensitive) {

        super(delegates);

        useFieldNames = true;
        fieldNameMap = buildFieldNameMap(delegates, isCaseSensitive);

        consumedFields = new boolean[delegates.size()];
        currentFieldIndex = -1;
    }

    @Override
    public boolean consumeElement(JsonParser parser) throws IOException {

        if (currentFieldIndex == -1) {

            if (parser.currentToken() != JsonToken.START_OBJECT)
                throw new IllegalStateException();

            currentFieldIndex = 0;
            resetConsumedFields();
        }

        var token = parser.nextValue();

        while (token != JsonToken.END_OBJECT) {

            // No data = wait for more
            if (token == null || token == JsonToken.NOT_AVAILABLE)
                return false;

            // Value available
            else if (token.isScalarValue() || token.isStructStart()) {

                var fieldName = parser.currentName();
                var fieldIndex = fieldNameMap.get(fieldName);

                // Ignore unknown fields
                if (fieldIndex == null)
                    continue;

                var delegate = delegates.get(fieldIndex);

                // Try to parse the field
                if (delegate.consumeElement(parser)) {
                    consumedFields[fieldIndex] = true;
                    currentFieldIndex++;
                }
                else
                    return false;
            }

            else {
                throw new IllegalStateException();
            }

            token = parser.nextValue();
        }

        // Handle missing fields - set to null if possible, error otherwise
        checkConsumedFields(parser);

        currentFieldIndex = -1;
        return true;
    }

    private static Map<String, Integer> buildFieldNameMap(List<IJsonConsumer<?>> delegates, boolean isCaseSensitive) {

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

    IJsonConsumer<?> getFieldDelegate(JsonParser parser) throws IOException {

        if (useFieldNames) {

            var fieldName = parser.currentName();
            var fieldIndex = fieldNameMap.get(fieldName);

            if (fieldIndex != null)
                return delegates.get(fieldIndex);
        }
        else {

            if (currentFieldIndex >= 0 && currentFieldIndex < delegates.size())
                return delegates.get(currentFieldIndex);
        }

        return null;
    }

    private void resetConsumedFields() {

        Arrays.fill(consumedFields, false);
    }

    private void checkConsumedFields(JsonParser parser) throws IOException {

        for (int i = 0; i < consumedFields.length; i++) {
            if (!consumedFields[i]) {

                var delegate = delegates.get(i);
                var field = delegate.getVector().getField();

                if (field.isNullable()) {
                    delegate.setNull();
                }
                else {
                    var msg = String.format("Invalid JSON table: Missing required field [%s]", field.getName());
                    throw new JsonParseException(parser, msg, parser.currentLocation());
                }
            }
        }
    }
}
