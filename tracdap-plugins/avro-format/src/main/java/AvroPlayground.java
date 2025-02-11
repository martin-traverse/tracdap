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

import org.apache.arrow.adapter.avro.AvroToArrow;
import org.apache.arrow.adapter.avro.AvroToArrowConfigBuilder;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.avro.Schema;
import org.apache.avro.io.DecoderFactory;

import java.io.File;
import java.io.FileInputStream;

public class AvroPlayground {

    public void play() {

        var decoder = new DecoderFactory().binaryDecoder(new FileInputStream("./thirdpartydeps/avro/users.avro"), null);
        var schema = new Schema.Parser().parse(new File("./thirdpartydeps/avro/user.avsc"));
        try (var allocator = new RootAllocator()) {

            var config = new AvroToArrowConfigBuilder(allocator)
                    .

                    .build();
            try (var avroToArrowVectorIterator = AvroToArrow.avroToArrowIterator(schema, decoder, config)) {
                while(avroToArrowVectorIterator.hasNext()) {
                    try (VectorSchemaRoot root = avroToArrowVectorIterator.next()) {
                        System.out.print(root.contentToTSVString());
                    }
                }
            }
        }
    }
}
