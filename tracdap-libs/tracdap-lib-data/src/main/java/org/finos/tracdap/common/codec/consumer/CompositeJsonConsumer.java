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

public class CompositeJsonConsumer {

    private final List<IJsonConsumer<?>> consumers;
    private int currentIndex;
    private int currentElement;

    public CompositeJsonConsumer(List<IJsonConsumer<?>> consumers) {
        this.consumers = consumers;
        this.currentIndex = 0;
        this.currentElement = 0;
    }

    public boolean consumeRecord(JsonParser parser) throws IOException {

        while (currentElement < consumers.size() && parser.currentToken() != JsonToken.NOT_AVAILABLE) {

            var consumer = consumers.get(currentElement);

            if (!consumer.consumeElement(parser))
                return false;

            currentElement++;
        }

        currentIndex++;
        currentElement = 0;

        return true;
    }
}
