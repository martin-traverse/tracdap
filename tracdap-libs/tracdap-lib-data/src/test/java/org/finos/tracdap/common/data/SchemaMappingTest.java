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

import org.finos.tracdap.test.data.SampleData;
import org.apache.arrow.vector.types.pojo.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.function.Function;
import java.util.stream.Collectors;


class SchemaMappingTest {

    @Test
    void testTableSchemaConversion() {

        // Get the BASIC_TABLE_SCHEMA from SampleData
        var tracSchema = SampleData.BASIC_TABLE_SCHEMA;

        // Convert TRAC schema to Arrow schema
        var arrowSchema = SchemaMapping.tracToArrow(tracSchema);

        // Verify the schema is not null
        Assertions.assertNotNull(arrowSchema, "Arrow schema should not be null");

        // Get the logical and physical fields for verification
        var logicalFields = arrowSchema.decoded()
                .getFields().stream()
                .collect(Collectors.toMap(
                        Field::getName,
                        Function.identity()));

        var physicalFields = arrowSchema.physical()
                .getFields().stream()
                .collect(Collectors.toMap(
                        Field::getName,
                        Function.identity()));

        // Verify basic field types in logical schema
        Assertions.assertEquals(ArrowType.Bool.INSTANCE, logicalFields.get("boolean_field").getType(),
                "boolean_field should be BOOLEAN");

        Assertions.assertEquals(SchemaMapping.ARROW_BASIC_INTEGER, logicalFields.get("integer_field").getType(),
                "integer_field should be INTEGER");

        Assertions.assertEquals(SchemaMapping.ARROW_BASIC_FLOAT, logicalFields.get("float_field").getType(),
                "float_field should be FLOAT");

        Assertions.assertEquals(SchemaMapping.ARROW_BASIC_DECIMAL, logicalFields.get("decimal_field").getType(),
                "decimal_field should be DECIMAL");

        Assertions.assertEquals(SchemaMapping.ARROW_BASIC_STRING, logicalFields.get("string_field").getType(),
                "string_field should be STRING");

        Assertions.assertEquals(SchemaMapping.ARROW_BASIC_DATE, logicalFields.get("date_field").getType(),
                "date_field should be DATE");

        Assertions.assertEquals(SchemaMapping.ARROW_BASIC_DATETIME, logicalFields.get("datetime_field").getType(),
                "datetime_field should be DATETIME");

        // Verify categorical field in both logical and physical schemas
        var logicalCategoricalField = logicalFields.get("categorical_field");
        var physicalCategoricalField = physicalFields.get("categorical_field");

        Assertions.assertNull(logicalCategoricalField.getDictionary(),
                "Logical categorical_field should not have a dictionary");

        Assertions.assertEquals(ArrowType.Utf8.INSTANCE, logicalCategoricalField.getType(),
                "Logical categorical_field should be STRING type");

        Assertions.assertNotNull(physicalCategoricalField.getDictionary(),
                "Physical categorical_field should have a dictionary");

        Assertions.assertInstanceOf(ArrowType.Int.class, physicalCategoricalField.getType(),
                "Physical categorical_field should be INT type");
    }

    @Test
    void testStructSchemaConversion() {

        var tracSchema = SampleData.BASIC_STRUCT_SCHEMA;
        var arrowSchema = SchemaMapping.tracToArrow(tracSchema);

        Assertions.assertNotNull(arrowSchema, "Arrow schema should not be null");

        var logicalFields = arrowSchema.decoded()
                .getFields().stream()
                .collect(Collectors.toMap(
                        Field::getName,
                        Function.identity()));

        var physicalFields = arrowSchema.physical()
                .getFields().stream()
                .collect(Collectors.toMap(
                        Field::getName,
                        Function.identity()));
        
        // Verify basic field types
        Assertions.assertEquals(ArrowType.Bool.INSTANCE, logicalFields.get("boolField").getType(), "boolField should be BOOLEAN");
        Assertions.assertEquals(SchemaMapping.ARROW_BASIC_INTEGER, logicalFields.get("intField").getType(), "intField should be INTEGER");
        Assertions.assertEquals(SchemaMapping.ARROW_BASIC_FLOAT, logicalFields.get("floatField").getType(), "floatField should be FLOAT");
        Assertions.assertEquals(SchemaMapping.ARROW_BASIC_DECIMAL, logicalFields.get("decimalField").getType(), "decimalField should be DECIMAL");
        Assertions.assertEquals(SchemaMapping.ARROW_BASIC_STRING, logicalFields.get("strField").getType(), "strField should be STRING");
        Assertions.assertEquals(SchemaMapping.ARROW_BASIC_DATE, logicalFields.get("dateField").getType(), "dateField should be DATE");
        Assertions.assertEquals(SchemaMapping.ARROW_BASIC_DATETIME, logicalFields.get("datetimeField").getType(), "datetimeField should be DATETIME");
        
        // Verify enum field is a string with dictionary encoding
        var logicalEnumField = logicalFields.get("enumField");
        var physicalEnumField = physicalFields.get("enumField");
        Assertions.assertNull(logicalEnumField.getDictionary(), "logical enumField should not have a dictionary");
        Assertions.assertEquals(ArrowType.Utf8.INSTANCE, logicalEnumField.getType(), "Logical enumField should be STRING type");
        Assertions.assertNotNull(physicalEnumField.getDictionary(), "logical enumField should have a dictionary");
        Assertions.assertInstanceOf(ArrowType.Int.class, physicalEnumField.getType(), "Physical enumField should be INT type");
        
        // Verify list field and its child schema
        var listField = logicalFields.get("listField");
        Assertions.assertInstanceOf(ArrowType.List.class, listField.getType(), "listField should be a LIST type");
        var listFieldChildren = listField.getChildren();
        Assertions.assertEquals(1, listFieldChildren.size(), "listField should have exactly one child field");
        Assertions.assertEquals(SchemaMapping.ARROW_BASIC_STRING, listFieldChildren.get(0).getType(), 
                "listField child should be STRING type");
        
        // Verify map field and its key/value schemas
        var mapField = logicalFields.get("dictField");
        Assertions.assertInstanceOf(ArrowType.Map.class, mapField.getType(), "dictField should be a MAP type");
        var mapFieldChildren = mapField.getChildren();
        Assertions.assertEquals(1, mapFieldChildren.size(), "mapField should have exactly one child field (entries)");
        
        var mapEntryField = mapFieldChildren.get(0);
        Assertions.assertEquals("entries", mapEntryField.getName(), "Map entries field should be named 'entries'");
        Assertions.assertEquals(2, mapEntryField.getChildren().size(), "Map entries should have key and value fields");
        
        var mapKeyField = mapEntryField.getChildren().get(0);
        var mapValueField = mapEntryField.getChildren().get(1);
        
        Assertions.assertEquals("key", mapKeyField.getName(), "Map key field should be named 'key'");
        Assertions.assertEquals(SchemaMapping.ARROW_BASIC_STRING, mapKeyField.getType(), 
                "Map key should be STRING type");
                
        Assertions.assertEquals("value", mapValueField.getName(), "Map value field should be named 'value'");
        Assertions.assertEquals(SchemaMapping.ARROW_BASIC_INTEGER, mapValueField.getType(), 
                "Map value should be INTEGER type");
        
        // Verify anonymous struct field and its fields
        var structField = logicalFields.get("anonymousStructField");
        Assertions.assertInstanceOf(ArrowType.Struct.class, structField.getType(), "anonymousStructField should be a STRUCT type");
        
        var structFields = structField.getChildren();
        Assertions.assertEquals(2, structFields.size(), "anonymousStructField should have 2 fields");
        
        var structField1 = structFields.get(0);
        var structField2 = structFields.get(1);
        
        Assertions.assertEquals("innerField1", structField1.getName(), "First struct field should be named 'innerField1'");
        Assertions.assertEquals(SchemaMapping.ARROW_BASIC_BOOLEAN, structField1.getType(), 
                "innerField1 should be BOOLEAN type");
                
        Assertions.assertEquals("innerField2", structField2.getName(), "Second struct field should be named 'innerField2'");
        Assertions.assertEquals(SchemaMapping.ARROW_BASIC_STRING, structField2.getType(), 
                "innerField2 should be STRING type");
        
        // Verify nested struct field (as map) and its key/value schemas
        var nestedStructField = logicalFields.get("nestedStructField");
        Assertions.assertInstanceOf(ArrowType.Map.class, nestedStructField.getType(), "nestedStructField should be a MAP type");
        
        var nestedStructChildren = nestedStructField.getChildren();
        Assertions.assertEquals(1, nestedStructChildren.size(), "nestedStructField should have exactly one child field (entries)");
        
        var nestedStructEntryField = nestedStructChildren.get(0);
        Assertions.assertEquals("entries", nestedStructEntryField.getName(), "Nested struct entries field should be named 'entries'");
        
        var nestedStructValueField = nestedStructEntryField.getChildren().get(1);
        Assertions.assertEquals("value", nestedStructValueField.getName(), "Nested struct value field should be named 'value'");
        
        // Verify the nested struct's fields
        var nestedStructFields = nestedStructValueField.getChildren();
        Assertions.assertEquals(2, nestedStructFields.size(), "Nested struct should have 2 fields");
        
        var nestedField1 = nestedStructFields.get(0);
        var nestedField2 = nestedStructFields.get(1);
        
        Assertions.assertEquals("nestedIntField", nestedField1.getName(), "First nested struct field should be named 'nestedIntField'");
        Assertions.assertEquals(SchemaMapping.ARROW_BASIC_INTEGER, nestedField1.getType(), 
                "nestedIntField should be INTEGER type");
                
        Assertions.assertEquals("nestedStringField", nestedField2.getName(), "Second nested struct field should be named 'nestedStringField'");
        Assertions.assertEquals(SchemaMapping.ARROW_BASIC_STRING, nestedField2.getType(), 
                "nestedStringField should be STRING type");
    }
}
