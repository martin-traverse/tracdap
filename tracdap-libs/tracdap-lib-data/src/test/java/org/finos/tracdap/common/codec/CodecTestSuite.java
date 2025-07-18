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

import org.finos.tracdap.common.codec.arrow.ArrowFileCodec;
import org.finos.tracdap.common.data.*;
import org.finos.tracdap.common.codec.arrow.ArrowStreamCodec;
import org.finos.tracdap.common.codec.csv.CsvCodec;
import org.finos.tracdap.common.codec.json.JsonCodec;
import org.finos.tracdap.common.async.Flows;
import org.finos.tracdap.common.data.util.Bytes;
import org.finos.tracdap.common.exception.EDataCorruption;
import org.finos.tracdap.common.exception.EUnexpected;
import org.finos.tracdap.test.data.DataComparison;
import org.finos.tracdap.test.data.SampleData;
import org.finos.tracdap.test.data.SingleBatchDataSink;
import org.finos.tracdap.test.data.SingleBatchDataSource;
import org.finos.tracdap.common.util.ResourceHelpers;

import io.netty.util.concurrent.DefaultEventExecutor;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.memory.ArrowBuf;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static org.finos.tracdap.test.concurrent.ConcurrentTestHelpers.getResultOf;
import static org.finos.tracdap.test.concurrent.ConcurrentTestHelpers.waitFor;
import static org.finos.tracdap.test.data.SampleData.generateBasicData;
import static org.finos.tracdap.test.data.SampleData.generateStructData;


public abstract class CodecTestSuite {

    // Concrete test cases for codecs included in CORE_DATA

    static class ArrowStreamTest extends CodecTestSuite { @BeforeAll static void setup() {
        codec = new ArrowStreamCodec();
        basicData = null;
        structData = null;
        structSupport = true;
    } }

    static class ArrowFileTest extends CodecTestSuite { @BeforeAll static void setup() {
        codec = new ArrowFileCodec();
        basicData = null;
        structData = null;
        structSupport = true;
    } }

    static class CSVTest extends CodecTestSuite { @BeforeAll static void setup() {
        codec = new CsvCodec();
        basicData = SampleData.BASIC_CSV_DATA_RESOURCE;
        structData = null;
        structSupport = false;
    } }

    static class JSONTest extends CodecTestSuite { @BeforeAll static void setup() {
        codec = new JsonCodec();
        basicData = SampleData.BASIC_JSON_DATA_RESOURCE;
        structData = SampleData.STRUCT_JSON_DATA_RESOURCE;
        structSupport = true;
    } }

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(20);

    static ICodec codec;
    static String basicData;
    static String structData;
    static boolean structSupport;

    private boolean basicDataAvailable() {
        return basicData != null;
    }

    private boolean structDataAvailable() {
        return structData != null;
    }

    private boolean structSupported() {
        return structSupport;
    }

    @Test
    void roundTrip_basic() {

        var allocator = new RootAllocator();
        var inputData = generateBasicData(allocator);
        inputData.flip();

        roundTrip_impl(inputData, allocator);
    }

    @Test
    void roundTrip_nulls() {

        var allocator = new RootAllocator();
        var inputData = generateBasicData(allocator);

        // With the basic test data, we'll get one null value for each data type

        var limit = Math.min(inputData.getBackBuffer().getRowCount(), inputData.getBackBuffer().getFieldVectors().size());

        for (var i = 0; i < limit; i++) {

            var vector = inputData.getBackBuffer().getVector(i);

            if (BaseFixedWidthVector.class.isAssignableFrom(vector.getClass())) {

                var fixedVector = (BaseFixedWidthVector) vector;
                fixedVector.setNull(i);
            } else if (BaseVariableWidthVector.class.isAssignableFrom(vector.getClass())) {

                var variableVector = (BaseVariableWidthVector) vector;
                variableVector.setNull(i);
            } else {

                throw new EUnexpected();
            }
        }

        inputData.flip();

        roundTrip_impl(inputData, allocator);
    }

    @Test
    @EnabledIf(value = "structSupported", disabledReason = "This codec does not support STRUCT data")
    void roundTrip_struct() {

        var allocator = new RootAllocator();
        var inputData = generateStructData(allocator);
        inputData.flip();

        roundTrip_impl(inputData, allocator);
    }

    @Test
    void edgeCaseIntegers() {

        var allocator = new RootAllocator();

        var fieldType = new FieldType(true, SchemaMapping.ARROW_BASIC_INTEGER, null);
        var field = new Field("integer_field", fieldType, null);

        var intVec = new BigIntVector(field, allocator);
        intVec.allocateNew(4);

        intVec.set(0, 0);
        intVec.setNull(1);
        intVec.set(2, Long.MAX_VALUE);
        intVec.set(3, Long.MIN_VALUE);

        var root = new VectorSchemaRoot(List.of(field), List.of(intVec));

        var inputData = ArrowVsrContext.forSource(root, null, allocator);
        inputData.setRowCount(4);
        inputData.setLoaded();
        inputData.flip();

        roundTrip_impl(inputData, allocator);
    }

    @Test
    void edgeCaseFloats() {

        var allocator = new RootAllocator();

        var fieldType = new FieldType(true, SchemaMapping.ARROW_BASIC_FLOAT, null);
        var field = new Field("float_field", fieldType, null);

        var floatVec = new Float8Vector(field, allocator);
        floatVec.allocateNew(8);

        floatVec.set(0, 0.0);
        floatVec.setNull(1);
        floatVec.set(2, Double.MAX_VALUE);
        floatVec.set(3, Double.MIN_VALUE);
        floatVec.set(4, Double.MIN_NORMAL);
        floatVec.set(5, -Double.MIN_NORMAL);

        // These values may be disallowed in certain places, e.g. checks on data import or model outputs
        // However at the codec level, they should be supported
        floatVec.set(6, Double.POSITIVE_INFINITY);
        floatVec.set(7, Double.NEGATIVE_INFINITY);
        floatVec.set(8, Double.NaN);

        var root = new VectorSchemaRoot(List.of(field), List.of(floatVec));

        var inputData = ArrowVsrContext.forSource(root, null, allocator);
        inputData.setRowCount(9);
        inputData.setLoaded();
        inputData.flip();

        roundTrip_impl(inputData, allocator);
    }

    @Test
    void edgeCaseDecimals() {

        var allocator = new RootAllocator();

        var fieldType = new FieldType(true, SchemaMapping.ARROW_BASIC_DECIMAL, null);
        var field = new Field("decimal_field", fieldType, null);

        var decimalVec = new DecimalVector(field, allocator);
        decimalVec.allocateNew(6);

        var d0 = BigDecimal.ZERO.setScale(12, RoundingMode.UNNECESSARY);
        var d1 = BigDecimal.TEN.pow(25).setScale(12, RoundingMode.UNNECESSARY);
        var d2 = BigDecimal.TEN.pow(25).negate().setScale(12, RoundingMode.UNNECESSARY);
        var d3 = new BigDecimal("0.000000000001").setScale(12, RoundingMode.UNNECESSARY);
        var d4 = new BigDecimal("-0.000000000001").setScale(12, RoundingMode.UNNECESSARY);

        decimalVec.set(0, d0);
        decimalVec.setNull(1);
        decimalVec.set(2, d1);
        decimalVec.set(3, d2);
        decimalVec.set(4, d3);
        decimalVec.set(5, d4);

        var root = new VectorSchemaRoot(List.of(field), List.of(decimalVec));

        var inputData = ArrowVsrContext.forSource(root, null, allocator);
        inputData.setRowCount(6);
        inputData.setLoaded();
        inputData.flip();

        roundTrip_impl(inputData, allocator);
    }

    @Test
    void edgeCaseStrings() {

        var allocator = new RootAllocator();

        var fieldType = new FieldType(true, SchemaMapping.ARROW_BASIC_STRING, null);
        var field = new Field("string_field", fieldType, null);

        var stringVec = new VarCharVector("string_field", allocator);
        stringVec.allocateNew(10);

        stringVec.set(0, "hello".getBytes(StandardCharsets.UTF_8));
        stringVec.setNull(1);
        stringVec.set(2, "".getBytes(StandardCharsets.UTF_8));
        stringVec.set(3, " ".getBytes(StandardCharsets.UTF_8));
        stringVec.set(4, "\r\n\t".getBytes(StandardCharsets.UTF_8));
        stringVec.set(5, "\\\"/\\//\\".getBytes(StandardCharsets.UTF_8));
        stringVec.set(6, " hello\nworld ".getBytes(StandardCharsets.UTF_8));
        stringVec.set(7, "你好世界".getBytes(StandardCharsets.UTF_8));
        stringVec.set(8, "مرحبا بالعالم".getBytes(StandardCharsets.UTF_8));
        stringVec.set(9, "\0".getBytes(StandardCharsets.UTF_8));

        var root = new VectorSchemaRoot(List.of(field), List.of(stringVec));

        var inputData = ArrowVsrContext.forSource(root, null, allocator);
        inputData.setRowCount(10);
        inputData.setLoaded();
        inputData.flip();

        roundTrip_impl(inputData, allocator);
    }

    @Test
    void edgeCaseDates() {

        var allocator = new RootAllocator();

        var fieldType = new FieldType(true, SchemaMapping.ARROW_BASIC_DATE, null);
        var field = new Field("date_field", fieldType, null);

        var dateVec = new DateDayVector("date_field", allocator);
        dateVec.allocateNew(6);

        dateVec.set(0, (int) LocalDate.EPOCH.toEpochDay());
        dateVec.setNull(1);
        dateVec.set(2, (int) LocalDate.of(2001, 1, 1).toEpochDay());
        dateVec.set(3, (int) LocalDate.of(2038, 1, 20).toEpochDay());

        var minDate = LocalDate.ofEpochDay(Integer.MIN_VALUE);
        var maxDate = LocalDate.ofEpochDay(Integer.MAX_VALUE);

        dateVec.set(4, (int) minDate.toEpochDay());
        dateVec.set(5, (int) maxDate.toEpochDay());

        Assertions.assertEquals(minDate, LocalDate.ofEpochDay(dateVec.get(4)));
        Assertions.assertEquals(maxDate, LocalDate.ofEpochDay(dateVec.get(5)));

        var root = new VectorSchemaRoot(List.of(field), List.of(dateVec));

        var inputData = ArrowVsrContext.forSource(root, null, allocator);
        inputData.setRowCount(6);
        inputData.setLoaded();
        inputData.flip();

        roundTrip_impl(inputData, allocator);
    }

    @Test
    void edgeCaseDateTimes() {

        var allocator = new RootAllocator();

        var fieldType = new FieldType(true, SchemaMapping.ARROW_BASIC_DATETIME, null);
        var field = new Field("datetime_field", fieldType, null);

        var datetimeVec = new TimeStampMilliVector("datetime_field", allocator);
        datetimeVec.allocateNew(8);

        datetimeVec.set(0, toEpochMillis(LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC)));
        datetimeVec.setNull(1);
        datetimeVec.set(2, toEpochMillis(LocalDateTime.of(2000, 1, 1, 0, 0, 0)));
        datetimeVec.set(3, toEpochMillis(LocalDateTime.of(2038, 1, 19, 3, 14, 8)));

        // Fractional seconds before and after the epoch
        // Test fractions for both positive and negative encoded values
        datetimeVec.set(4, toEpochMillis(LocalDateTime.of(1972, 1, 1, 0, 0, 0, 500000000)));
        datetimeVec.set(5, toEpochMillis(LocalDateTime.of(1968, 1, 1, 23, 59, 59, 500000000)));

        var minDatetime = fromEpochMillis(Integer.MIN_VALUE);
        var maxDatetime = fromEpochMillis(Integer.MAX_VALUE);

        datetimeVec.set(6, toEpochMillis(minDatetime));
        datetimeVec.set(7, toEpochMillis(maxDatetime));

        Assertions.assertEquals(minDatetime, fromEpochMillis(datetimeVec.get(6)));
        Assertions.assertEquals(maxDatetime, fromEpochMillis(datetimeVec.get(7)));

        var root = new VectorSchemaRoot(List.of(field), List.of(datetimeVec));

        var inputData = ArrowVsrContext.forSource(root, null, allocator);
        inputData.setRowCount(8);
        inputData.setLoaded();
        inputData.flip();

        roundTrip_impl(inputData, allocator);
    }

    private long toEpochMillis(LocalDateTime localDateTime) {

        return localDateTime.toEpochSecond(ZoneOffset.UTC) * 1000 + localDateTime.getNano() / 1000000;
    }

    private LocalDateTime fromEpochMillis(long epochMillis) {

        var epochSeconds = epochMillis / 1000;
        var nanos = (epochMillis % 1000) * 1000000;

        if (epochSeconds < 0 && nanos != 0) {
            --epochSeconds;
            nanos += 1000000000;
        }

        return LocalDateTime.ofEpochSecond(epochSeconds, (int) nanos, ZoneOffset.UTC);
    }

    void roundTrip_impl(ArrowVsrContext inputData, RootAllocator allocator) {

        var ctx = new DataContext(new DefaultEventExecutor(), allocator);

        var dataSrc = new SingleBatchDataSource(inputData);
        var pipeline = DataPipeline.forSource(dataSrc, ctx);

        pipeline.addStage(codec.getEncoder(allocator, Map.of()));
        pipeline.addStage(codec.getDecoder(inputData.getSchema(), allocator, Map.of()));

        var dataSink = new SingleBatchDataSink(pipeline, batch -> DataComparison.compareBatches(inputData, batch));
        pipeline.addSink(dataSink);

        var exec = pipeline.execute();
        waitFor(TEST_TIMEOUT, exec);

        // Ensure errors are reported (pipeline errors or validation failures)
        try {
            getResultOf(exec);
        }
        catch(Exception e) {
            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            else
                throw new RuntimeException(e);
        }

        var rtSchema = dataSink.getSchema();
        var rtRowCount = dataSink.getRowCount();

        DataComparison.compareSchemas(inputData.getSchema(), rtSchema);

        Assertions.assertEquals(1, dataSink.getBatchCount());
        Assertions.assertEquals(inputData.getFrontBuffer().getRowCount(), rtRowCount);

        inputData.close();
    }

    @Test
    @EnabledIf(value = "basicDataAvailable", disabledReason = "Pre-saved test data not available for this format")
    void decode_basic() throws Exception {

        var allocator = new RootAllocator();
        var inputData = generateBasicData(allocator);

        var testData = ResourceHelpers.loadResourceAsBytes(basicData);
        var testDataBuf = Bytes.copyToBuffer(testData, allocator);
        var testDataStream = Flows.publish(List.of(testDataBuf));

        var dataCtx = new DataContext(new DefaultEventExecutor(), allocator);
        var pipeline = DataPipeline.forSource(testDataStream, dataCtx);

        var decoder = codec.getDecoder(inputData.getSchema(), allocator, Map.of());
        pipeline.addStage(decoder);

        var dataSink = new SingleBatchDataSink(pipeline, batch -> DataComparison.compareBatches(inputData, batch));
        pipeline.addSink(dataSink);

        var exec = pipeline.execute();
        waitFor(TEST_TIMEOUT, exec);
        getResultOf(exec);

        var rtSchema = dataSink.getSchema();
        var rtRowCount = dataSink.getRowCount();

        DataComparison.compareSchemas(inputData.getSchema(), rtSchema);

        Assertions.assertEquals(1, dataSink.getBatchCount());
        Assertions.assertEquals(inputData.getFrontBuffer().getRowCount(), rtRowCount);

        inputData.close();
    }

    @Test
    @EnabledIf(value = "structDataAvailable", disabledReason = "Pre-saved struct data not available for this format")
    void decode_struct() throws Exception {

        var allocator = new RootAllocator();
        var structSchema = SchemaMapping.tracToArrow(SampleData.BASIC_STRUCT_SCHEMA, allocator);
        var structExpectedResult = generateStructData(allocator);

        var testData = ResourceHelpers.loadResourceAsBytes(structData);
        var testDataBuf = Bytes.copyToBuffer(testData, allocator);
        var testDataStream = Flows.publish(List.of(testDataBuf));

        var dataCtx = new DataContext(new DefaultEventExecutor(), allocator);
        var pipeline = DataPipeline.forSource(testDataStream, dataCtx);

        var decoder = codec.getDecoder(structSchema, allocator, Map.of());
        pipeline.addStage(decoder);

        var dataSink = new SingleBatchDataSink(pipeline, batch -> DataComparison.compareBatches(structExpectedResult, batch));
        pipeline.addSink(dataSink);

        var exec = pipeline.execute();
        waitFor(TEST_TIMEOUT, exec);
        getResultOf(exec);

        var rtSchema = dataSink.getSchema();
        var rtRowCount = dataSink.getRowCount();

        DataComparison.compareSchemas(structSchema, rtSchema);

        Assertions.assertEquals(1, dataSink.getBatchCount());
        Assertions.assertEquals(1, rtRowCount);
    }

    @Test
    void decode_empty() {

        var allocator = new RootAllocator();

        // An empty stream (i.e. with no buffers)

        var noBufStream = Flows.publish(List.<ArrowBuf>of());
        var arrowSchema = SchemaMapping.tracToArrow(SampleData.BASIC_TABLE_SCHEMA);

        var dataCtx = new DataContext(new DefaultEventExecutor(), allocator);
        var pipeline = DataPipeline.forSource(noBufStream, dataCtx);

        var decoder = codec.getDecoder(arrowSchema, allocator, Map.of());
        pipeline.addStage(decoder);

        var dataSink = new SingleBatchDataSink(pipeline);
        pipeline.addSink(dataSink);

        var exec = pipeline.execute();
        waitFor(TEST_TIMEOUT, exec);

        Assertions.assertThrows(EDataCorruption.class, () -> getResultOf(exec));

        // A stream containing a series of empty buffers

        var emptyBuffers = List.of(
                allocator.getEmpty(),
                allocator.getEmpty(),
                allocator.getEmpty());

        var emptyBufStream = Flows.publish(emptyBuffers);

        var dataCtx2 = new DataContext(new DefaultEventExecutor(), allocator);
        var pipeline2 = DataPipeline.forSource(emptyBufStream, dataCtx2);

        var decoder2 = codec.getDecoder(arrowSchema, allocator, Map.of());
        pipeline2.addStage(decoder2);

        var dataSink2 = new SingleBatchDataSink(pipeline2);
        pipeline2.addSink(dataSink2);

        var exec2 = pipeline2.execute();
        waitFor(TEST_TIMEOUT, exec2);

        Assertions.assertThrows(EDataCorruption.class, () -> getResultOf(exec2));
    }

    @Test
    void decode_garbled() throws Exception {

        // Send a stream of random bytes - 3 chunks worth

        var arrowSchema = SchemaMapping.tracToArrow(SampleData.BASIC_TABLE_SCHEMA);

        var testData = List.of(
                new byte[10000],
                new byte[10000],
                new byte[10000]);

        var random = new Random();
        testData.forEach(random::nextBytes);

        var allocator = new RootAllocator();
        var testDataBuf = testData.stream()
                .map(bytes -> Bytes.copyToBuffer(bytes, allocator))
                .collect(Collectors.toList());
        var testDataStream = Flows.publish(testDataBuf);

        // Run the garbage data through the decoder

        var dataCtx = new DataContext(new DefaultEventExecutor(), allocator);
        var pipeline = DataPipeline.forSource(testDataStream, dataCtx);

        var decoder = codec.getDecoder(arrowSchema, allocator, Map.of());
        pipeline.addStage(decoder);

        var dataSink = new SingleBatchDataSink(pipeline);
        pipeline.addSink(dataSink);

        var exec = pipeline.execute();
        waitFor(TEST_TIMEOUT, exec);

        Assertions.assertThrows(EDataCorruption.class, () -> getResultOf(exec));
    }

    @Test
    void decode_garbled_arrow_stream() {

        // Garbled data deliberately designed to break the arrow stream codec
        // The first 8 bytes are a valid continuation and message length, followed by a garbage message
        // There should be an error parsing the Arrow FlatBuffers message, which should be handled as EDataCorruption

        var arrowSchema = SchemaMapping.tracToArrow(SampleData.BASIC_TABLE_SCHEMA);

        var testDataHeader = new byte[] {
                (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
                (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00};

        var testDataMessage = new byte[256];
        var random = new Random();
        random.nextBytes(testDataMessage);

        var testData = List.of(testDataHeader, testDataMessage);

        var allocator = new RootAllocator();
        var testDataBuf = testData.stream()
                .map(bytes -> Bytes.copyToBuffer(bytes, allocator))
                .collect(Collectors.toList());
        var testDataStream = Flows.publish(testDataBuf);

        // Run the garbage data through the decoder

        var dataCtx = new DataContext(new DefaultEventExecutor(), allocator);
        var pipeline = DataPipeline.forSource(testDataStream, dataCtx);

        var decoder = codec.getDecoder(arrowSchema, allocator, Map.of());
        pipeline.addStage(decoder);

        var dataSink = new SingleBatchDataSink(pipeline);
        pipeline.addSink(dataSink);

        var exec = pipeline.execute();
        waitFor(TEST_TIMEOUT, exec);

        Assertions.assertThrows(EDataCorruption.class, () -> getResultOf(exec));
    }
}
