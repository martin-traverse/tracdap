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

package org.finos.tracdap.common.codec.arrow;

import org.finos.tracdap.common.codec.ICodec;

import org.apache.arrow.memory.BufferAllocator;
import org.finos.tracdap.common.data.ArrowVsrSchema;
import org.finos.tracdap.common.data.DataPipeline;
import org.finos.tracdap.metadata.SchemaDefinition;

import java.util.List;
import java.util.Map;


public class ArrowFileCodec implements ICodec {

    private static final String DEFAULT_FILE_EXTENSION = "arrow";

    @Override
    public List<String> options() {
        return List.of();
    }

    @Override
    public String defaultFileExtension() {
        return DEFAULT_FILE_EXTENSION;
    }

    @Override
    public Encoder<DataPipeline.StreamApi>
    getEncoder(BufferAllocator allocator, Map<String, String> options) {
        return new ArrowFileEncoder(allocator);
    }

    @Override
    public Decoder<?> getDecoder(BufferAllocator allocator, Map<String, String> options) {
        return new ArrowFileDecoder(allocator);
    }

    @Override
    public Decoder<?> getDecoder(SchemaDefinition tracSchema, BufferAllocator allocator, Map<String, String> options) {
        return new ArrowFileDecoder(allocator);
    }

    @Override
    public Decoder<?> getDecoder(ArrowVsrSchema arrowSchema, BufferAllocator allocator, Map<String, String> options) {
        return new ArrowFileDecoder(allocator);
    }
}
