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
import org.apache.arrow.vector.Decimal256Vector;

import java.io.IOException;

public class JsonDecimal256Consumer extends BaseJsonConsumer<Decimal256Vector> {

    private final int scale;

    public JsonDecimal256Consumer(Decimal256Vector vector) {
        super(vector);
        this.scale = vector.getScale();
    }

    @Override
    public boolean consumeElement(JsonParser parser) throws IOException {

        var value = JsonParsing.parseBigDecimal(parser, scale);
        vector.set(currentIndex++, value);
        return true;
    }
}
