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

package org.finos.tracdap.common.codec;

import org.finos.tracdap.common.data.ArrowVsrSchema;
import org.finos.tracdap.metadata.SchemaDefinition;
import org.finos.tracdap.common.data.DataPipeline;

import org.apache.arrow.memory.BufferAllocator;

import java.util.List;
import java.util.Map;

public interface ICodec {

    interface Encoder <API_T extends DataPipeline.DataInterface<API_T>>
        extends
            DataPipeline.DataConsumer<DataPipeline.ArrowApi>,
            DataPipeline.DataProducer<API_T> {}

    interface Decoder <API_T extends DataPipeline.DataInterface<API_T>>
        extends
            DataPipeline.DataConsumer<API_T>,
            DataPipeline.DataProducer<DataPipeline.ArrowApi> {}

    List<String> options();

    String defaultFileExtension();

    Encoder<?> getEncoder(
            BufferAllocator allocator,
            Map<String, String> options);

    Decoder<?> getDecoder(
            BufferAllocator allocator,
            Map<String, String> options);

    Decoder<?> getDecoder(
            SchemaDefinition tracSchema,
            BufferAllocator allocator,
            Map<String, String> options);

    Decoder<?> getDecoder(
            ArrowVsrSchema arrowSchema,
            BufferAllocator allocator,
            Map<String, String> options);
}
