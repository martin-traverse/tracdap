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
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.complex.writer.VarCharWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class JsonVarCharConsumer extends BaseJsonConsumer<VarCharVector> {

    public JsonVarCharConsumer(VarCharVector vector) {
        super(vector);
    }

    @Override
    public boolean consumeElement(JsonParser parser) throws IOException {

        parser.getText();
        parser.getTextCharacters();
        int offset = parser.getTextOffset();
        int length = parser.getTextLength();

        String value = parser.getValueAsString();

        VarCharWriter writer = new VarCharWriter();

        // For variable width vectors, the required size of the content buffer is not known up front
        // Arrow makes an initial guess, but sometimes it will need to reallocate on write
        // So, we need to call setSafe() instead of set(), to avoid a buffer overflow

        vector.setSafe(currentIndex++, value.getBytes(StandardCharsets.UTF_8));
        return true;
    }
}
