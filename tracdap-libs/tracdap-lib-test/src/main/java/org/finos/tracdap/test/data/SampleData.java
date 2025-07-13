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

package org.finos.tracdap.test.data;

import com.google.common.collect.Streams;
import com.google.errorprone.annotations.Var;
import org.apache.arrow.algorithm.dictionary.HashTableBasedDictionaryBuilder;
import org.apache.arrow.algorithm.dictionary.HashTableDictionaryEncoder;
import org.apache.arrow.vector.complex.ListVector;
import org.apache.arrow.vector.complex.MapVector;
import org.apache.arrow.vector.complex.StructVector;
import org.apache.arrow.vector.dictionary.Dictionary;
import org.apache.arrow.vector.dictionary.DictionaryProvider;
import org.apache.arrow.vector.types.pojo.Field;
import org.finos.tracdap.common.data.ArrowVsrContext;
import org.finos.tracdap.common.data.ArrowVsrSchema;
import org.finos.tracdap.common.data.ArrowVsrStaging;
import org.finos.tracdap.common.data.SchemaMapping;
import org.finos.tracdap.common.exception.ETracInternal;
import org.finos.tracdap.common.exception.EUnexpected;
import org.finos.tracdap.common.metadata.TypeSystem;
import org.finos.tracdap.metadata.*;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class SampleData {

    public static final String BASIC_CSV_DATA_RESOURCE = "/sample_data/csv_basic.csv";
    public static final String BASIC_CSV_DATA_RESOURCE_V2 = "/sample_data/csv_basic_v2.csv";
    public static final String BASIC_JSON_DATA_RESOURCE = "/sample_data/json_basic.json";
    public static final String STRUCT_JSON_DATA_RESOURCE = "/sample_data/json_struct.json";
    public static final String ALT_CSV_DATA_RESOURCE = "/sample_data/csv_alt.csv";

    public static final SchemaDefinition BASIC_TABLE_SCHEMA
            = SchemaDefinition.newBuilder()
            .setSchemaType(SchemaType.TABLE)
            .setTable(TableSchema.newBuilder()
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("boolean_field")
                    .setFieldOrder(0)
                    .setFieldType(BasicType.BOOLEAN))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("integer_field")
                    .setFieldOrder(1)
                    .setFieldType(BasicType.INTEGER))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("float_field")
                    .setFieldOrder(2)
                    .setFieldType(BasicType.FLOAT))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("decimal_field")
                    .setFieldOrder(3)
                    .setFieldType(BasicType.DECIMAL))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("string_field")
                    .setFieldOrder(4)
                    .setFieldType(BasicType.STRING))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("categorical_field")
                    .setFieldOrder(5)
                    .setFieldType(BasicType.STRING)
                    .setCategorical(true))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("date_field")
                    .setFieldOrder(6)
                    .setFieldType(BasicType.DATE))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("datetime_field")
                    .setFieldOrder(7)
                    .setFieldType(BasicType.DATETIME)))
            .build();

    public static final SchemaDefinition BASIC_TABLE_SCHEMA_V2
            = BASIC_TABLE_SCHEMA.toBuilder()
            .setTable(BASIC_TABLE_SCHEMA.getTable().toBuilder()
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("extra_string_field")
                    .setFieldOrder(8)
                    .setFieldType(BasicType.STRING)))
            .build();

    public static final SchemaDefinition ALT_TABLE_SCHEMA
            = SchemaDefinition.newBuilder()
            .setSchemaType(SchemaType.TABLE)
            .setTable(TableSchema.newBuilder()
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("alt_string_field")
                    .setFieldOrder(0)
                    .setFieldType(BasicType.STRING))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("alt_categorical_field")
                    .setFieldOrder(1)
                    .setFieldType(BasicType.STRING)
                    .setCategorical(true))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("alt_value_field")
                    .setFieldOrder(2)
                    .setFieldType(BasicType.FLOAT))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("alt_value_2_field")
                    .setFieldOrder(3)
                    .setFieldType(BasicType.FLOAT))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("alt_flag")
                    .setFieldOrder(4)
                    .setFieldType(BasicType.BOOLEAN)))
            .build();

    public static final SchemaDefinition ALT_TABLE_SCHEMA_V2
            = ALT_TABLE_SCHEMA.toBuilder()
            .setTable(ALT_TABLE_SCHEMA.getTable().toBuilder()
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("alt_extra_flag")
                    .setFieldOrder(5)
                    .setFieldType(BasicType.BOOLEAN)))
            .build();

    public static final SchemaDefinition BASIC_STRUCT_SCHEMA
            = SchemaDefinition.newBuilder()
            .setSchemaType(SchemaType.STRUCT_SCHEMA)
            .setPartType(PartType.NOT_PARTITIONED)
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("boolField")
                    .setFieldType(BasicType.BOOLEAN)
                    .setNotNull(true))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("intField")
                    .setFieldType(BasicType.INTEGER)
                    .setNotNull(true))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("floatField")
                    .setFieldType(BasicType.FLOAT)
                    .setNotNull(true))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("decimalField")
                    .setFieldType(BasicType.DECIMAL)
                    .setNotNull(true))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("strField")
                    .setFieldType(BasicType.STRING)
                    .setNotNull(true))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("dateField")
                    .setFieldType(BasicType.DATE)
                    .setNotNull(true))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("datetimeField")
                    .setFieldType(BasicType.DATETIME)
                    .setNotNull(true))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("enumField")
                    .setFieldType(BasicType.STRING)
                    .setCategorical(true)
                    .setNotNull(true)
                    .setNamedEnum("ExampleEnum"))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("quotedField")
                    .setFieldType(BasicType.STRING)
                    .setNotNull(true))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("optionalField")
                    .setFieldType(BasicType.STRING)
                    .setNotNull(false))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("optionalQuotedField")
                    .setFieldType(BasicType.STRING)
                    .setNotNull(false))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("listField")
                    .setFieldType(BasicType.ARRAY)
                    .setNotNull(true)
                    .addChildren(FieldSchema.newBuilder()
                            .setFieldType(BasicType.INTEGER)
                            .setNotNull(true)))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("dictField")
                    .setFieldType(BasicType.MAP)
                    .setNotNull(true)
                    .addChildren(FieldSchema.newBuilder()
                            .setFieldType(BasicType.STRING)
                            .setNotNull(true))
                    .addChildren(FieldSchema.newBuilder()
                            .setFieldType(BasicType.DATETIME)
                            .setNotNull(true)))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("anonymousStructField")
                    .setFieldType(BasicType.STRUCT)
                    .addChildren(FieldSchema.newBuilder()
                            .setFieldName("field1")
                            .setFieldType(BasicType.STRING)
                            .setNotNull(true))
                    .addChildren(FieldSchema.newBuilder()
                            .setFieldName("field2")
                            .setFieldType(BasicType.INTEGER)
                            .setNotNull(true))
                    .addChildren(FieldSchema.newBuilder()
                            .setFieldName("enumField")
                            .setFieldType(BasicType.STRING)
                            .setCategorical(true)
                            .setNotNull(true)
                            .setNamedEnum("ExampleEnum")))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("structField")
                    .setFieldType(BasicType.STRUCT)
                    .setNamedType("DataClassSubStruct"))
            .addFields(FieldSchema.newBuilder()
                    .setFieldName("nestedStructField")
                    .setFieldType(BasicType.MAP)
                    .addChildren(FieldSchema.newBuilder()
                            .setFieldType(BasicType.STRING)
                            .setNotNull(true))
                    .addChildren(FieldSchema.newBuilder()
                            .setFieldType(BasicType.STRUCT)
                            .setNamedType("DataClassSubStruct")
                            .setNotNull(true)))
            .putNamedTypes("DataClassSubStruct", SchemaDefinition.newBuilder()
                    .setSchemaType(SchemaType.STRUCT_SCHEMA)
                    .setPartType(PartType.NOT_PARTITIONED)
                    .addFields(FieldSchema.newBuilder()
                            .setFieldName("field1")
                            .setFieldType(BasicType.STRING)
                            .setNotNull(true))
                    .addFields(FieldSchema.newBuilder()
                            .setFieldName("field2")
                            .setFieldType(BasicType.INTEGER)
                            .setNotNull(true))
                    .addFields(FieldSchema.newBuilder()
                            .setFieldName("enumField")
                            .setFieldType(BasicType.STRING)
                            .setCategorical(true)
                            .setNotNull(true)
                            .setNamedEnum("ExampleEnum"))
                    .build())
            .putNamedEnums("ExampleEnum", EnumValues.newBuilder()
                    .addValues("RED")
                    .addValues("BLUE")
                    .addValues("GREEN")
                    .build())
            .build();


    public static final FlowDefinition SAMPLE_FLOW = FlowDefinition.newBuilder()
            .putNodes("basic_data_input", FlowNode.newBuilder()
                    .setNodeType(FlowNodeType.INPUT_NODE)
                    .build())
            .putNodes("alt_data_input", FlowNode.newBuilder()
                    .setNodeType(FlowNodeType.INPUT_NODE)
                    .build())
            .putNodes("model_1", FlowNode.newBuilder()
                    .setNodeType(FlowNodeType.MODEL_NODE)
                    .addInputs("basic_data_input")
                    .addOutputs("enriched_basic_data")
                    .build())
            .putNodes("model_2", FlowNode.newBuilder()
                    .setNodeType(FlowNodeType.MODEL_NODE)
                    .addInputs("alt_data_input")
                    .addOutputs("enriched_alt_data")
                    .build())
            .putNodes("model_3", FlowNode.newBuilder()
                    .setNodeType(FlowNodeType.MODEL_NODE)
                    .addInputs("enriched_basic_data")
                    .addInputs("enriched_alt_data")
                    .addOutputs("sample_output_data")
                    .build())
            .putNodes("sample_output_data", FlowNode.newBuilder()
                    .setNodeType(FlowNodeType.OUTPUT_NODE)
                    .build())
            .addEdges(FlowEdge.newBuilder()
                    .setSource(FlowSocket.newBuilder()
                    .setNode("basic_data_input"))
                    .setTarget(FlowSocket.newBuilder()
                    .setNode("model_1")
                    .setSocket("basic_data_input")))
            .addEdges(FlowEdge.newBuilder()
                    .setSource(FlowSocket.newBuilder()
                    .setNode("alt_data_input"))
                    .setTarget(FlowSocket.newBuilder()
                    .setNode("model_2")
                    .setSocket("alt_data_input")))
            .addEdges(FlowEdge.newBuilder()
                    .setSource(FlowSocket.newBuilder()
                    .setNode("model_1")
                    .setSocket("enriched_basic_data"))
                    .setTarget(FlowSocket.newBuilder()
                    .setNode("model_3")
                    .setSocket("enriched_basic_data")))
            .addEdges(FlowEdge.newBuilder()
                    .setSource(FlowSocket.newBuilder()
                    .setNode("model_2")
                    .setSocket("enriched_alt_data"))
                    .setTarget(FlowSocket.newBuilder()
                    .setNode("model_3")
                    .setSocket("enriched_alt_data")))
            .addEdges(FlowEdge.newBuilder()
                    .setSource(FlowSocket.newBuilder()
                    .setNode("model_3")
                    .setSocket("sample_output_data"))
                    .setTarget(FlowSocket.newBuilder()
                    .setNode("sample_output_data")))
            .build();

    public static ArrowVsrContext generateBasicData(BufferAllocator arrowAllocator) {

        var javaData = new HashMap<String, List<Object>>();

        for (var field : BASIC_TABLE_SCHEMA.getTable().getFieldsList()) {

            var javaValues = generateJavaValues(field.getFieldType(), field.getCategorical(), 10, 0);
            javaData.put(field.getFieldName(), javaValues);
        }

        return convertData(BASIC_TABLE_SCHEMA, javaData, 10, arrowAllocator);
    }

    public static ArrowVsrContext generateStructData(BufferAllocator arrowAllocator) {

        var javaData = new HashMap<String, List<Object>>();

        for (var field : BASIC_STRUCT_SCHEMA.getFieldsList()) {

            var javaValues = generateJavaValues(field, 1, 2, BASIC_STRUCT_SCHEMA);
            javaData.put(field.getFieldName(), javaValues);
        }

        return convertData(BASIC_STRUCT_SCHEMA, javaData, 1, arrowAllocator);
    }

    public static List<Object> generateJavaValues(FieldSchema field, int n, int offset, SchemaDefinition schema) {

        if (TypeSystem.isPrimitive(field.getFieldType())) {
            return generateJavaValues(field.getFieldType(), field.getCategorical(), n, offset);
        }

        if (field.getFieldType() == BasicType.ARRAY) {

            var itemsField = field.getChildren(0);

            return IntStream.range(offset, n + offset)
                    .mapToObj(i -> generateJavaValues(itemsField, 10, offset, schema))
                    .collect(Collectors.toList());
        }

        if (field.getFieldType() == BasicType.MAP) {

            var keyField =  field.getChildren(0);
            var valuesField = field.getChildren(1);

            return IntStream.range(offset, n + offset)
                    .mapToObj(i -> Streams.zip(
                            generateJavaValues(keyField, 10, offset, schema).stream(),
                            generateJavaValues(valuesField, 10, offset, schema).stream(),
                            Map::entry)
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    Map.Entry::getValue)))
                    .collect(Collectors.toList());
        }

        if (field.getFieldType() == BasicType.STRUCT) {

            var structFields = field.hasNamedType()
                    ? schema.getNamedTypesOrThrow(field.getNamedType()).getFieldsList()
                    : field.getChildrenList();

            var structData = new ArrayList<>(n);

            for (int i = 0; i < n; i++) {

                var structDataMap = new HashMap<String, Object>();

                for (var structField : structFields) {

                    var javaValues = generateJavaValues(structField, 1, offset, BASIC_STRUCT_SCHEMA);
                    structDataMap.put(structField.getFieldName(), javaValues.get(0));
                }

                structData.add(structDataMap);
            }

            return structData;
        }

        throw new EUnexpected();
    }

    public static List<Object> generateJavaValues(BasicType basicType, boolean categorical, int n, int offset) {

        // NOTE: These values should match the pre-saved basic test files in resources/sample_data of -lib-test

        switch (basicType) {

            case BOOLEAN:
                return IntStream.range(offset, n + offset)
                        .mapToObj(i -> i % 2 == 0)
                        .collect(Collectors.toList());

            case INTEGER:
                return IntStream.range(offset, n + offset)
                        .mapToObj(i -> (long) i)
                        .collect(Collectors.toList());

            case FLOAT:
                return IntStream.range(offset, n + offset)
                        .mapToObj(i -> (double) i)
                        .collect(Collectors.toList());

            case DECIMAL:
                return IntStream.range(offset, n + offset)
                        .mapToObj(BigDecimal::valueOf)
                        .map(d -> d.setScale(12, RoundingMode.UNNECESSARY))
                        .collect(Collectors.toList());

            case STRING:
                if (categorical) {
                    var categories = new String[] {"RED", "BLUE", "GREEN"};
                    return IntStream.range(offset, n + offset)
                            .mapToObj(i -> categories[i % categories.length])
                            .collect(Collectors.toList());
                }
                else {
                    return IntStream.range(offset, n + offset)
                            .mapToObj(i -> "Hello world " + i)
                            .collect(Collectors.toList());
                }

            case DATE:
                return IntStream.range(offset, n + offset)
                        .mapToObj(i -> LocalDate.ofEpochDay(0).plusDays(i))
                        .collect(Collectors.toList());

            case DATETIME:
                return IntStream.range(offset, n + offset)
                        .mapToObj(i -> LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC).plusSeconds(i))
                        .collect(Collectors.toList());

            default:
                throw new EUnexpected();
        }
    }

    public static ArrowVsrContext convertData(
            SchemaDefinition schema, Map<String, List<Object>> data,
            int size, BufferAllocator arrowAllocator) {

        var arrowSchema = SchemaMapping.tracToArrow(schema, arrowAllocator);

        return convertData(arrowSchema, data, size, arrowAllocator);
    }

    public static ArrowVsrContext convertData(
            ArrowVsrSchema schema, Map<String, List<Object>> data,
            int size, BufferAllocator arrowAllocator) {

        for (var fieldName : data.keySet()) {
            if (schema.physical().findField(fieldName) == null) {
                throw new ETracInternal("Sample data field " + fieldName + " is not present in the schema");
            }
        }

        var vectors = schema.physical().getFields()
                .stream().map(field -> field.createVector(arrowAllocator))
                .map(v -> { v.allocateNew(); v.setInitialCapacity(size); return v; })
                .collect(Collectors.toList());

        var vsr = new VectorSchemaRoot(vectors);
        var dictionaries = new DictionaryProvider.MapDictionaryProvider();

        if (schema.dictionaries() != null) {
            for (var id : schema.dictionaries().getDictionaryIds()) {
                var prebuilt = schema.dictionaries().lookup(id);
                if (prebuilt != null && prebuilt.getVector().getValueCount() > 0)
                    dictionaries.put(prebuilt);
            }
        }

        var staging = new ArrayList<ArrowVsrStaging<?>>();

        var consumers = vectors.stream()
                .map(vector -> buildConsumer(vector, schema.dictionaryFields(), dictionaries, staging, arrowAllocator))
                .collect(Collectors.toList());

        for (int col = 0, nCols = schema.physical().getFields().size(); col < nCols; col++) {

            var field = schema.physical().getFields().get(col);
            var values = data.get(field.getName());
            var consumer = consumers.get(col);

            for (int i = 0; i < values.size(); i++) {
                consumer.accept(i, values.get(i));
            }

            vectors.get(col).setValueCount(values.size());
        }

        for (var staged :  staging) {
            staged.getStagingVector().setValueCount(size);
            staged.encodeVector();
            staged.getTargetVector().setValueCount(size);
        }

        var context = ArrowVsrContext.forSource(vsr, dictionaries, arrowAllocator, /* ownership = */ true);
        context.setRowCount(size);
        context.setLoaded();

        // Do not flip, client code may modify back buffer before flipping

        return context;
    }

    private static BiConsumer<Integer, Object> buildConsumer(
            FieldVector vector,
            Map<Long, Field> dictionaryFields,
            DictionaryProvider.MapDictionaryProvider dictionaries,
            List<ArrowVsrStaging<?>> stagedFields,
            BufferAllocator allocator) {

        Field field = vector.getField();

        if (field.getDictionary() != null) {

            var encoding = field.getDictionary();
            var dictionaryField =  dictionaryFields.get(encoding.getId());

            var stagingVector = (ElementAddressableVector) dictionaryField.createVector(allocator);
            stagingVector.allocateNew();

            var dictionary = dictionaries.lookup(encoding.getId());

            if (dictionary != null) {

                var staging = new ArrowVsrStaging(stagingVector, (BaseIntVector) vector, dictionary);
                stagedFields.add(staging);
            }
            else {

                var staging = new ArrowVsrStaging(stagingVector, (BaseIntVector) vector);
                stagedFields.add(staging);
                dictionaries.put(staging.getDictionary());
            }

            return buildConsumer((FieldVector) stagingVector, null, null, null, allocator);
        }

        switch (field.getType().getTypeID()) {

            case Bool:
                var booleanVec = (BitVector) vector;
                return (i, o) -> booleanVec.set(i, (Boolean) o ? 1 : 0);

            case Int:
                var intVec = (BigIntVector) vector;
                return  (i, o) -> {
                    if (o instanceof Integer)
                        intVec.set(i, (Integer) o);
                    else if (o instanceof Long)
                        intVec.set(i, (Long) o);
                    else
                        throw new EUnexpected();
                };

            case FloatingPoint:
                var floatVec = (Float8Vector) vector;
                return (i, o) -> {
                    if (o instanceof Float)
                        floatVec.set(i, (Float) o);
                    else if (o instanceof Double)
                        floatVec.set(i, (Double) o);
                    else
                        throw new EUnexpected();
                };

            case Decimal:
                var decimalVec = (DecimalVector) vector;
                return (i, o) -> decimalVec.set(i, (BigDecimal) o);

            case Utf8:
                var stringVec = (VarCharVector) vector;
                return (i, o) -> stringVec.setSafe(i, ((String) o).getBytes(StandardCharsets.UTF_8));

            case Date:
                var dateVec = (DateDayVector) vector;
                return (i, o) -> dateVec.set(i, (int) ((LocalDate) o).toEpochDay());

            case Timestamp:
                var timestampVec = (TimeStampMilliVector) vector;
                return (i, o) -> {
                    LocalDateTime datetimeNoZone = (LocalDateTime) o;
                    long unixEpochMillis =
                            (datetimeNoZone.toEpochSecond(ZoneOffset.UTC) * 1000) +
                                    (datetimeNoZone.getNano() / 1000000);
                    timestampVec.set(i, unixEpochMillis);
                };

            case List:

                var listVector = (ListVector) vector;
                var dataVector = listVector.getDataVector();
                var itemConsumer = buildConsumer(dataVector, dictionaryFields, dictionaries, stagedFields, allocator);

                return (i, o) -> {
                    @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>) o;
                    int currentSize = dataVector.getValueCount();
                    int currenCapacity = dataVector.getValueCapacity();
                    if (currentSize == 0) {
                        dataVector.setInitialCapacity(list.size());
                        currenCapacity = dataVector.getValueCapacity();
                    }
                    while (currentSize + list.size() > currenCapacity) {
                        dataVector.reAlloc();
                        currenCapacity =  dataVector.getValueCapacity();
                    }
                    listVector.startNewValue(i);
                    for (int j = 0; j < list.size(); j++) {
                        itemConsumer.accept(currentSize + j, list.get(j));
                    }
                    listVector.endValue(i, list.size());
                };

            case Map:

                var mapVector = (MapVector) vector;
                var entryVector = (StructVector) mapVector.getDataVector();
                var keyVector = (VarCharVector) entryVector.getChildrenFromFields().get(0);
                var valueVector = entryVector.getChildrenFromFields().get(1);
                var keyConsumer = buildConsumer(keyVector, dictionaryFields, dictionaries, stagedFields, allocator);
                var valueConsumer = buildConsumer(valueVector, dictionaryFields, dictionaries, stagedFields, allocator);

                return (i, o) -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) o;
                    List<Object> mapKeys = new ArrayList<>(map.keySet());
                    List<Object> mapValues = mapKeys.stream().map(Object::toString).map(map::get).collect(Collectors.toList());
                    int currentSize = entryVector.getValueCount();
                    int currenCapacity = entryVector.getValueCapacity();
                    if (currentSize == 0) {
                        entryVector.setInitialCapacity(map.size());
                        currenCapacity = entryVector.getValueCapacity();
                    }
                    while (currentSize + map.size() > currenCapacity) {
                        entryVector.reAlloc();
                        currenCapacity = entryVector.getValueCapacity();
                    }
                    mapVector.startNewValue(i);
                    for (int j = 0; j < mapKeys.size(); j++) {
                        var mapKey = mapKeys.get(j);
                        var mapValue = mapValues.get(j);
                        keyConsumer.accept(currentSize + j, mapKey);
                        valueConsumer.accept(currentSize + j, mapValue);
                        entryVector.setIndexDefined(currentSize + j);
                    }
                    entryVector.setValueCount(currentSize + mapKeys.size());
                    mapVector.endValue(i, mapKeys.size());
                };

            case Struct:

                var structVector = (StructVector) vector;
                var childVectors = structVector.getChildrenFromFields();
                var childConsumers = new HashMap<String, BiConsumer<Integer, Object>>();
                for (var child : childVectors) {
                    var childConsumer = buildConsumer(child, dictionaryFields, dictionaries, stagedFields, allocator);
                    childConsumers.put(child.getName(), childConsumer);
                }

                return (i, o) -> {

                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) o;

                    for (FieldVector childVector : childVectors) {
                        var childValue = map.get(childVector.getName());
                        childConsumers.get(childVector.getName()).accept(i, childValue);
                    }

                    structVector.setIndexDefined(i);
                };

            default:
                throw new EUnexpected();
        }
    }
}
