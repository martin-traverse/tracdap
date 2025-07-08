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

package org.finos.tracdap.common.data;

import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.dictionary.Dictionary;
import org.apache.arrow.vector.dictionary.DictionaryProvider;
import org.apache.arrow.vector.types.DateUnit;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.TimeUnit;
import org.apache.arrow.vector.types.pojo.*;
import org.finos.tracdap.common.exception.EUnexpected;
import org.finos.tracdap.common.exception.EValidationGap;
import org.finos.tracdap.metadata.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class SchemaMapping {

    // Default decimal settings - 38:12 with 128 bit width
    private static final int DECIMAL_PRECISION = 38;
    private static final int DECIMAL_SCALE = 12;
    private static final int DECIMAL_BIT_WIDTH = 128;

    // Default datetime settings
    private static final TimeUnit TIMESTAMP_PRECISION = TimeUnit.MILLISECOND;
    private static final String NO_ZONE = null;

    public static final ArrowType ARROW_BASIC_BOOLEAN = ArrowType.Bool.INSTANCE;
    public static final ArrowType ARROW_BASIC_INTEGER = new ArrowType.Int(64, true);
    public static final ArrowType ARROW_BASIC_FLOAT = new ArrowType.FloatingPoint(FloatingPointPrecision.DOUBLE);
    public static final ArrowType ARROW_BASIC_DECIMAL =  new ArrowType.Decimal(DECIMAL_PRECISION, DECIMAL_SCALE, DECIMAL_BIT_WIDTH);
    public static final ArrowType ARROW_BASIC_STRING = ArrowType.Utf8.INSTANCE;
    public static final ArrowType ARROW_BASIC_DATE = new ArrowType.Date(DateUnit.DAY);
    public static final ArrowType ARROW_BASIC_DATETIME = new ArrowType.Timestamp(TIMESTAMP_PRECISION, NO_ZONE);  // Using type without timezone

    public static final ArrowType ARROW_LIST = new ArrowType.List();
    public static final ArrowType ARROW_MAP = new ArrowType.Map(/* keysSorted = */ false);
    public static final ArrowType ARROW_STRUCT = new ArrowType.Struct();

    private static final Map<BasicType, ArrowType> TRAC_ARROW_TYPE_MAPPING = Map.ofEntries(
            Map.entry(BasicType.BOOLEAN, ARROW_BASIC_BOOLEAN),
            Map.entry(BasicType.INTEGER, ARROW_BASIC_INTEGER),
            Map.entry(BasicType.FLOAT, ARROW_BASIC_FLOAT),
            Map.entry(BasicType.DECIMAL, ARROW_BASIC_DECIMAL),
            Map.entry(BasicType.STRING, ARROW_BASIC_STRING),
            Map.entry(BasicType.DATE, ARROW_BASIC_DATE),
            Map.entry(BasicType.DATETIME, ARROW_BASIC_DATETIME),
            Map.entry(BasicType.ARRAY, ARROW_LIST),
            Map.entry(BasicType.MAP, ARROW_MAP),
            Map.entry(BasicType.STRUCT, ARROW_STRUCT));

    private static final Map<ArrowType.ArrowTypeID, BasicType> ARROW_TRAC_TYPE_MAPPING = Map.ofEntries(
            Map.entry(ArrowType.ArrowTypeID.Bool, BasicType.BOOLEAN),
            Map.entry(ArrowType.ArrowTypeID.Int, BasicType.INTEGER),
            Map.entry(ArrowType.ArrowTypeID.FloatingPoint, BasicType.FLOAT),
            Map.entry(ArrowType.ArrowTypeID.Decimal, BasicType.DECIMAL),
            Map.entry(ArrowType.ArrowTypeID.Utf8, BasicType.STRING),
            Map.entry(ArrowType.ArrowTypeID.Date, BasicType.DATE),
            Map.entry(ArrowType.ArrowTypeID.Timestamp, BasicType.DATETIME));

    public static final FieldSchema DEFAULT_MAP_KEY_FIELD = FieldSchema.newBuilder()
            .setFieldName("key")  // Match Arrow's MapVector.KEY_NAME
            .setFieldType(BasicType.STRING)
            .setNotNull(true)
            .build();

    private final SchemaDefinition tracSchema;
    private final AtomicInteger nextDictionaryId;

    public static ArrowVsrSchema tracToArrow(SchemaDefinition tracSchema) {

        var mapping = new SchemaMapping(tracSchema);
        return mapping.tracToArrowSchema(tracSchema);
    }

    private SchemaMapping(SchemaDefinition tracSchema) {
        this.tracSchema = tracSchema;
        this.nextDictionaryId = new AtomicInteger(0);
    }

    private ArrowVsrSchema tracToArrowSchema(SchemaDefinition tracSchema) {

        var tracFields = tracSchema.getSchemaType() == SchemaType.TABLE_SCHEMA && tracSchema.getFieldsCount() == 0
                ? tracSchema.getTable().getFieldsList()
                : tracSchema.getFieldsList();

        var logicalFields = new ArrayList<Field>(tracFields.size());
        var physicalFields = new ArrayList<Field>(tracFields.size());

        for (int i = 0; i < tracSchema.getNamedEnumsCount(); i++) {
            nextDictionaryId.incrementAndGet();
        }

        for (var tracField : tracFields) {

            var logicalField = tracToArrowField(tracField);
            var physicalField = tracToArrowPhysicalField(tracField, logicalField);

            logicalFields.add(logicalField);
            physicalFields.add(physicalField);
        }

        return new ArrowVsrSchema(new Schema(physicalFields), new Schema(logicalFields));
    }

    private Field tracToArrowField(FieldSchema tracField) {

        return tracToArrowField(tracField, tracField.getFieldName());
    }

    private Field tracToArrowField(FieldSchema tracField, String fieldName) {

        var arrowType = TRAC_ARROW_TYPE_MAPPING.get(tracField.getFieldType());
        var notNull = tracField.getBusinessKey() || tracField.getNotNull();
        var nullable = !notNull;

        // Unexpected error - All TRAC primitive types are mapped
        if (arrowType == null)
            throw new EUnexpected();

        var arrowFieldType = new FieldType(nullable, arrowType, /* dictionary = */ null);

        var children = tracField.hasChildren() || (tracField.getFieldType() == BasicType.STRUCT && tracField.hasNamedType())
                ? tracToArrowChildren(tracField)
                : null;

        return new Field(fieldName, arrowFieldType, children);
    }

    private List<Field> tracToArrowChildren(FieldSchema tracField) {

        var tracChildren = tracField.getChildren();

        if (tracField.getFieldType() == BasicType.ARRAY) {

            var tracItems = tracChildren.getArrayItems();
            var arrowItems = tracToArrowField(tracItems, "$data$");

            return List.of(arrowItems);
        }

        if (tracField.getFieldType() == BasicType.MAP) {

            var tracKeys = tracChildren.hasMapKeys()
                    ? tracChildren.getMapKeys()
                    : DEFAULT_MAP_KEY_FIELD;

            var tracValues = tracChildren.getMapValues();
            var arrowKeys = tracToArrowField(tracKeys, "key");
            var arrowValues = tracToArrowField(tracValues, "value");

            // Arrow maps are a list of entries, where the entry has keys and values as children
            var entriesType = new FieldType(false, new ArrowType.Struct(), null);
            var entriesField = new Field("entries", entriesType, List.of(arrowKeys, arrowValues));

            return List.of(entriesField);
        }

        if (tracField.getFieldType() == BasicType.STRUCT) {

            if (tracField.hasNamedType()) {

                if (!tracSchema.containsNamedTypes(tracField.getNamedType())) {

                    // TODO: Error
                    var error = String.format("Named type is missing in the schema: [%s]",  tracField.getNamedType());
                    throw new EValidationGap(error);
                }

                var namedType = tracSchema.getNamedTypesOrThrow(tracField.getNamedType());

                return namedType.getFieldsList().stream()
                        .map(this::tracToArrowField)
                        .collect(Collectors.toList());
            }

            if (tracChildren.getStructFieldsCount() > 0) {

                return tracChildren.getStructFieldsList().stream()
                        .map(this::tracToArrowField)
                        .collect(Collectors.toList());
            }
        }

        throw new EUnexpected();
    }


    private Field tracToArrowPhysicalField(FieldSchema tracField, Field logicalField) {

        if (logicalField.getChildren() != null && !logicalField.getChildren().isEmpty()) {

            if (tracField.getChildren().hasArrayItems()) {

                var physicalChild = tracToArrowPhysicalField(
                        tracField.getChildren().getArrayItems(),
                        logicalField.getChildren().get(0));

                return new Field(
                        logicalField.getName(),
                        logicalField.getFieldType(),
                        List.of(physicalChild));
            }

            if (tracField.getChildren().hasMapValues()) {

                var logicalEntries = logicalField.getChildren().get(0);
                var logicalKey = logicalEntries.getChildren().get(0);
                var logicalValue = logicalEntries.getChildren().get(1);

                var physicalKey = tracToArrowPhysicalField(tracField.getChildren().getMapKeys(), logicalKey);
                var physicalValue = tracToArrowPhysicalField(tracField.getChildren().getMapValues(), logicalValue);

                var physicalEntries = new Field(
                        logicalEntries.getName(),
                        logicalEntries.getFieldType(),
                        List.of(physicalKey, physicalValue));

                return new Field(
                        logicalField.getName(),
                        logicalField.getFieldType(),
                        List.of(physicalEntries));
            }

            if (tracField.hasNamedType() || tracField.getChildren().getStructFieldsCount() > 0) {

                var tracChildren = tracField.hasNamedType()
                        ? tracSchema.getNamedTypesOrThrow(tracField.getNamedType()).getFieldsList()
                        : tracField.getChildren().getStructFieldsList();

                var physicalChildren = new ArrayList<Field>(tracField.getChildren().getStructFieldsCount());

                for (int i = 0; i < logicalField.getChildren().size(); i++) {

                    var tracChild = tracChildren.get(i);
                    var logicalChild = logicalField.getChildren().get(i);
                    var physicalChild = tracToArrowPhysicalField(tracChild, logicalChild);

                    physicalChildren.add(physicalChild);
                }

                return new Field(
                        logicalField.getName(),
                        logicalField.getFieldType(),
                        physicalChildren);
            }

            // Children exist but no mapping logic available
            throw new EUnexpected();
        }

        if (tracField.getCategorical()) {

            var indexType = new ArrowType.Int(32, true);
            var encoding = new DictionaryEncoding(nextDictionaryId.getAndIncrement(), false, indexType);
            var notNull = tracField.getBusinessKey() || tracField.getNotNull();
            var nullable = !notNull;

            var metadata = new HashMap<String, String>();

            if (tracField.hasNamedEnum())
                metadata.put("trac.namedEnum",  tracField.getNamedEnum());

            return new Field(
                    tracField.getFieldName(),
                    new FieldType(nullable, indexType, encoding, metadata),
                    /* children = */ null);
        }
        else {

            return logicalField;
        }
    }


    private Map<String, Long> addNamedEnumDictionaries(DictionaryProvider.MapDictionaryProvider dictionaries, SchemaDefinition tracSchema) {

        var namedEnumMap = new HashMap<String, Long>();

        long nextId = dictionaries.getDictionaryIds().size();

        for (var entry : tracSchema.getNamedEnumsMap().entrySet()) {

            var name = entry.getKey();
            var enum_ = entry.getValue();

            var dictionaryVector = new VarCharVector(name, allocator);
            dictionaryVector.allocateNew(enum_.getValuesCount());

            for (int i = 0; i < enum_.getValuesCount(); i++) {
                dictionaryVector.setSafe(i, enum_.getValues(i).getStringValue().getBytes(StandardCharsets.UTF_8));
            }

            dictionaryVector.setValueCount(enum_.getValuesCount());

            var encoding = new DictionaryEncoding(nextId++, false, null);
            var dictionary = new Dictionary(dictionaryVector, encoding);

            namedEnumMap.put(name, encoding.getId());
            dictionaries.put(dictionary);
        }

        return namedEnumMap;
    }
}
