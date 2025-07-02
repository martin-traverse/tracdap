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
import org.apache.arrow.vector.TinyIntVector;
import org.finos.tracdap.common.exception.EDataCorruption;

import java.io.IOException;


public class JsonTinyIntConsumer extends BaseJsonConsumer<TinyIntVector> {

    public JsonTinyIntConsumer(TinyIntVector vector) {
        super(vector);
    }

    @Override
    public boolean consumeElement(JsonParser parser) throws IOException {

        // Token is the expected value
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_INT) {
            byte value = parser.getByteValue();
            vector.set(currentIndex++, value);
            return true;
        }

        // No data available (EOF or wait for more)
        if (parser.currentToken() == null || parser.currentToken() == JsonToken.NOT_AVAILABLE)
            return false;

        // Unexpected token - input data is corrupt
        var error = String.format("Unexpected token %s", parser.getCurrentToken().name());
        throw new EDataCorruption(error);
    }
}
