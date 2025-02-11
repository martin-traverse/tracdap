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

import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.finos.tracdap.common.codec.StreamingEncoder;
import org.finos.tracdap.common.data.util.ByteOutputStream;
import org.finos.tracdap.common.exception.EUnexpected;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


public class AvroEncoder extends StreamingEncoder {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final BufferAllocator allocator;
    private final ByteOutputStream out;

    private VectorSchemaRoot root;

    private Schema avroSchema;
    private DataFileWriter<GenericRecord> avroWriter;
    private Encoder avroEncoder;

    public AvroEncoder(BufferAllocator allocator) {
        this.allocator = allocator;
        this.out = new ByteOutputStream(allocator, consumer()::onNext);
    }

    @Override
    public void onStart(VectorSchemaRoot root) {

        try {

            consumer().onStart();

            this.root = root;
            this.avroSchema = arrowToAvroSchema(root);

            var datumWriter = new GenericDatumWriter<GenericRecord>(avroSchema);
            var dataWriter = new DataFileWriter<>(datumWriter);

            var baseEncoder = EncoderFactory.get().binaryEncoder(out, null);
            var encoder = EncoderFactory.get().validatingEncoder(avroSchema, baseEncoder);

            dataWriter.create(avroSchema, out);

            this.avroWriter = dataWriter;
        }
        catch (IOException e) {

            throw new RuntimeException(e);  // TODO
        }
    }

    @Override
    public void onBatch() {

        try {

            if (log.isTraceEnabled())
                log.trace("AVRO ENCODER: onNext()");

            var nRows = root.getRowCount();
            var nCols = root.getSchema().getFields().size();

            var recordBuilder = new GenericRecordBuilder(avroSchema);
            var avroFields = avroSchema.getFields();

            for (var row = 0; row < nRows; row++) {
                for (var col = 0; col < nCols; col++) {

                    var vector = root.getVector(col);

                    AvroValues.getAndGenerate(vector, row, avroEncoder);

                    avroEncoder.item

                    if (vector.isNull(row)) {
                        recordBuilder.clear(field);
                    }
                    else {
                        var value = vector.getObject(row);
                        recordBuilder.set(field, value);
                    }

                    avroWriter.append(recordBuilder.build());
                }
            }

            avroWriter.flush();
        }
        catch (IOException e) {

            // Output stream is writing to memory buffers, IO errors are not expected
            log.error("Unexpected error writing to codec buffer: {}", e.getMessage(), e);

            close();

            throw new EUnexpected(e);
        }

    }

    @Override
    public void onComplete() {

        try {

            if (log.isTraceEnabled())
                log.trace("AVRO ENCODER: onComplete()");

            // Flush and close output

            avroWriter.close();
            avroWriter = null;

            out.close();
            out = null;

            markAsDone();
            consumer().onComplete();
        }
        catch (IOException e) {

            // Output stream is writing to memory buffers, IO errors are not expected
            log.error("Unexpected error writing to codec buffer: {}", e.getMessage(), e);

            close();

            throw new EUnexpected(e);
        }
    }

    @Override
    public void onError(Throwable error) {

    }

    @Override
    public void close() {

        try {

            if (out != null) {
                out.close();
                out = null;
            }

            // Encoder does not own root, do not try to close it
            if (root != null) {
                root = null;
            }
        }
        catch (IOException e) {

            // Output stream is writing to memory buffers, IO errors are not expected
            log.error("Unexpected error closing encoder: {}", e.getMessage(), e);
            throw new EUnexpected(e);
        }
    }

    private Schema arrowToAvroSchema(VectorSchemaRoot root) {

        var arrowSchema = root.getSchema();

        var avroSchema = SchemaBuilder.record("").namespace("namespace");
        var avroFields = avroSchema.fields();

        for (var field : arrowSchema.getFields()) {
            avroFields = encodeField(field, avroFields);
        }

        return avroFields.endRecord();
    }

    private SchemaBuilder.FieldAssembler<Schema>
    encodeField(Field field, SchemaBuilder.FieldAssembler<Schema> avroFields) {

        return avroFields
                .name(field.getName())
                .type()
                .booleanType()
                .noDefault();

    }
}
