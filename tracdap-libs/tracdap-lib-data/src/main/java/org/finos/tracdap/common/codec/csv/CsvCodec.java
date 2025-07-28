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

package org.finos.tracdap.common.codec.csv;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.dataformat.csv.CsvFactory;
import com.fasterxml.jackson.dataformat.csv.CsvGenerator;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.finos.tracdap.common.codec.ICodec;
import org.finos.tracdap.common.codec.text.BaseTextEncoder;
import org.finos.tracdap.common.codec.text.BufferedTextDecoder;
import org.finos.tracdap.common.codec.text.TextFileConfig;
import org.finos.tracdap.common.data.ArrowVsrContext;
import org.finos.tracdap.common.data.ArrowVsrSchema;
import org.finos.tracdap.common.data.DataPipeline;

import org.apache.arrow.memory.BufferAllocator;
import org.finos.tracdap.common.data.SchemaMapping;
import org.finos.tracdap.common.exception.EDataConstraint;
import org.finos.tracdap.common.exception.EDataCorruption;
import org.finos.tracdap.metadata.SchemaDefinition;
import org.finos.tracdap.metadata.SchemaType;

import java.util.List;
import java.util.Map;


public class CsvCodec implements ICodec {

    private static final boolean DEFAULT_HEADER_FLAG = true;
    private static final int BATCH_SIZE = 1024;

    private static final String DEFAULT_FILE_EXTENSION = "csv";

    private static final CsvFactory csvFactory = new CsvFactory()
            // Make sure empty strings are quoted, so they can be distinguished from nulls
            .enable(CsvGenerator.Feature.ALWAYS_QUOTE_EMPTY_STRINGS)
            // Require strict adherence to the schema
            .enable(CsvParser.Feature.FAIL_ON_MISSING_COLUMNS)
            // Always allow nulls during parsing (they will be rejected later for non-nullable fields)
            .enable(CsvParser.Feature.EMPTY_UNQUOTED_STRING_AS_NULL)
            // Permissive handling of extra space (strings with leading/trailing spaces must be quoted anyway)
            .enable(CsvParser.Feature.TRIM_SPACES);

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

        var config = new TextFileConfig(csvFactory, null, BATCH_SIZE, false);
        return new BaseTextEncoder(allocator, config, this::generatorSetup);
    }

    @Override
    public Decoder<?> getDecoder(BufferAllocator allocator, Map<String, String> options) {

        throw new EDataConstraint("CSV decoder requires a TRAC schema");
    }

    @Override
    public Decoder<DataPipeline.BufferApi>
    getDecoder(SchemaDefinition tracSchema, BufferAllocator allocator, Map<String, String> options) {

        if (tracSchema.getSchemaType() != SchemaType.TABLE_SCHEMA) {
            throw new EDataConstraint("CSV decoder only support TABLE_SCHEMA");
        }

        var arrowSchema = SchemaMapping.tracToArrow(tracSchema, allocator);
        return getDecoder(arrowSchema, allocator, options);
    }

    @Override
    public Decoder<DataPipeline.BufferApi>
    getDecoder(ArrowVsrSchema arrowSchema, BufferAllocator allocator, Map<String, String> options) {

        var config = new TextFileConfig(csvFactory, null, BATCH_SIZE, false);
        return new BufferedTextDecoder(arrowSchema, allocator, config, this::parserSetup);
    }

    protected void generatorSetup(JsonGenerator generator, ArrowVsrContext context) {

        var csvSchema = CsvSchemaMapping
                .arrowToCsv(context.getSchema().logical())
                .build()
                .withHeader();

        generator.setSchema(csvSchema);
    }

    protected void parserSetup(JsonParser parser, ArrowVsrContext context) {

        var receivedSchema = (CsvSchema) parser.getSchema();

        if (receivedSchema.size() == 0) {
            parser.setSchema(CsvSchema.emptySchema().withHeader());
            return;
        }

        var logicalSchema = context.getSchema().logical();
        var suppliedSchema = CsvSchemaMapping
                .arrowToCsv(logicalSchema)
                .build();

        var schemaMarks = new boolean[suppliedSchema.size()];
        var schemaBuilder = CsvSchema.builder();

        for (int i = 0; i < receivedSchema.size(); i++) {

            var receivedName = receivedSchema.columnName(i);
            var suppliedIndex = suppliedSchema.columnIndex(receivedName);

            if (suppliedIndex < 0)
                throw new EDataCorruption("Field name [" + receivedName + "] is not defined in the schema");

            var suppliedColumn = suppliedSchema.column(suppliedIndex);
            schemaBuilder.addColumn(suppliedColumn);
            schemaMarks[suppliedIndex] = true;
        }

        for (int i = 0; i < schemaMarks.length; i++) {
            var field =logicalSchema.getFields().get(i);
            if (!schemaMarks[i] && !field.isNullable())
                throw new EDataCorruption("Field [" + field.getName() + "] is missing in the data");
        }

        var csvSchema = DEFAULT_HEADER_FLAG
                ? schemaBuilder.build().withHeader()
                : schemaBuilder.build().withoutHeader();

        parser.setSchema(csvSchema);
    }
}
