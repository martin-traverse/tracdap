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

import org.finos.tracdap.common.codec.text.BuildConsumers;
import org.finos.tracdap.common.codec.text.BufferedTextDecoder;
import org.finos.tracdap.common.codec.text.IBatchConsumer;
import org.finos.tracdap.common.codec.text.consumers.BatchConsumer;
import org.finos.tracdap.common.codec.text.consumers.CompositeArrayConsumer;
import org.finos.tracdap.common.data.ArrowVsrContext;
import org.finos.tracdap.common.data.ArrowVsrSchema;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.dataformat.csv.CsvFactory;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import org.apache.arrow.memory.BufferAllocator;


public class CsvDecoder extends BufferedTextDecoder {

    private static final boolean DEFAULT_HEADER_FLAG = true;

    private static final CsvFactory csvFactory = buildCsvFactory();

    private final ArrowVsrSchema arrowSchema;


    public CsvDecoder(BufferAllocator arrowAllocator, ArrowVsrSchema arrowSchema) {

        super(arrowAllocator, arrowSchema);

        // Schema cannot be inferred from CSV, so it must always be set from a TRAC schema
        this.arrowSchema = arrowSchema;
    }

    @Override
    protected JsonFactory getParserFactory() {

        return csvFactory;
    }

    private static CsvFactory buildCsvFactory() {

        return new CsvFactory()
                // Require strict adherence to the schema
                .enable(CsvParser.Feature.FAIL_ON_MISSING_COLUMNS)
                // Always allow nulls during parsing (they will be rejected later for non-nullable fields)
                .enable(CsvParser.Feature.EMPTY_UNQUOTED_STRING_AS_NULL)
                // Permissive handling of extra space (strings with leading/trailing spaces must be quoted anyway)
                .enable(CsvParser.Feature.TRIM_SPACES);
    }

    @Override
    protected JsonParser configureParser(JsonParser parser) {

        var csvSchema = CsvSchemaMapping
                .arrowToCsv(arrowSchema.decoded())
                //.setNullValue("")
                .build();

        csvSchema = DEFAULT_HEADER_FLAG
                ? csvSchema.withHeader()
                : csvSchema.withoutHeader();

        parser.setSchema(csvSchema);

        return parser;
    }

    @Override
    protected IBatchConsumer createConsumer(ArrowVsrContext context) {

        var fieldVectors = context.getStagingVectors();
        var fieldConsumers = BuildConsumers.createConsumers(fieldVectors);

        var recordConsumer = new CompositeArrayConsumer(fieldConsumers);

        return new BatchConsumer(recordConsumer, context);
    }
}
