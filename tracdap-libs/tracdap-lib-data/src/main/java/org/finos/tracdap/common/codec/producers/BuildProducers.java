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

package org.finos.tracdap.common.codec.producers;

import org.apache.arrow.vector.*;
import org.apache.arrow.vector.complex.*;
import org.apache.arrow.vector.dictionary.DictionaryProvider;

import java.util.List;
import java.util.stream.Collectors;


public class BuildProducers {

    public static List<IJsonProducer<?>> createProducers(List<FieldVector> vectors, DictionaryProvider dictionaries) {

        return vectors.stream()
                .map(v -> createProducer(v, v.getField().isNullable(), dictionaries))
                .collect(Collectors.toList());
    }

    public static IJsonProducer<?>
    createProducer(FieldVector vector, boolean nullable, DictionaryProvider dictionaries) {

        // Wrap nullable fields with an outer producer to handle nulls
        if (nullable && vector.getField().isNullable()) {
            var innerProducer = createProducer(vector, /* nullable = */ false, dictionaries);
            return new JsonNullableProducer<>(innerProducer);
        }

        // Wrap dictionary encoded fields with a producer that decodes dictionary values
        // No text format (currently) supports dictionaries, so they are always decoded
        if (vector.getField().getDictionary() != null) {

            if (dictionaries == null) {
                var message = "Field references a dictionary but no dictionaries were provided: %s";
                var error = String.format(message, vector.getField().getName());
                throw new IllegalArgumentException(error);
            }

            var dictionary = dictionaries.lookup(vector.getField().getDictionary().getId());

            if (dictionary == null) {
                var message = "Field references a dictionary that does not exist: %s, dictionary ID = %d";
                var error = String.format(message, vector.getField().getName(), vector.getField().getDictionary().getId());
                throw new IllegalArgumentException(error);
            }

            var innerNullable = vector.getField().isNullable();
            var innerProducer = createProducer(dictionary.getVector(), innerNullable, /* dictionaries = */ null);
            return new JsonDictionaryProducer((BaseIntVector) vector, innerProducer);
        }

        // Create a producer for the specific vector type
        switch (vector.getMinorType()) {

            // Null type

            case NULL:
                return new JsonNullProducer((NullVector) vector);

            // Numeric types

            case BIT:
                return new JsonBitProducer((BitVector) vector);
            case TINYINT:
                return new JsonTinyIntProducer((TinyIntVector) vector);
            case SMALLINT:
                return new JsonSmallIntProducer((SmallIntVector) vector);
            case INT:
                return new JsonIntProducer((IntVector) vector);
            case BIGINT:
                return new JsonBigIntProducer((BigIntVector) vector);
            case UINT1:
                return new JsonUInt1Producer((UInt1Vector) vector);
            case UINT2:
                return new JsonUInt2Producer((UInt2Vector) vector);
            case UINT4:
                return new JsonUInt4Producer((UInt4Vector) vector);
            case UINT8:
                return new JsonUInt8Producer((UInt8Vector) vector);
            case FLOAT2:
                return new JsonFloat2Producer((Float2Vector) vector);
            case FLOAT4:
                return new JsonFloat4Producer((Float4Vector) vector);
            case FLOAT8:
                return new JsonFloat8Producer((Float8Vector) vector);
            case DECIMAL:
                return new JsonDecimalProducer((DecimalVector) vector);
            case DECIMAL256:
                return new JsonDecimal256Producer((Decimal256Vector) vector);

            // Text / binary types

            case VARCHAR:
                return new JsonVarCharProducer((VarCharVector) vector);

            // Temporal types

            case DATEDAY:
                return new JsonDateDayProducer((DateDayVector) vector);
            case TIMESTAMPMILLI:
                return new JsonTimestampMilliProducer((TimeStampMilliVector) vector);

            // Composite types

            case LIST:
                var listVector = (ListVector) vector;
                var itemVector = listVector.getDataVector();
                var itemProducer = createProducer(itemVector, itemVector.getField().isNullable(), dictionaries);
                return new JsonListProducer(listVector, itemProducer);

            case FIXED_SIZE_LIST:
                var fixedListVector = (FixedSizeListVector) vector;
                var fixedItemVector = fixedListVector.getDataVector();
                var fixedItemProducer = createProducer(fixedItemVector, fixedItemVector.getField().isNullable(), dictionaries);
                return new JsonFixedSizeListProducer(fixedListVector, fixedItemProducer);
//
//            case MAP:
//                MapVector mapVector = (MapVector) vector;
//                StructVector entryVector = (StructVector) mapVector.getDataVector();
//                Types.MinorType keyType = entryVector.getChildrenFromFields().get(0).getMinorType();
//                if (keyType != Types.MinorType.VARCHAR) {
//                    throw new IllegalArgumentException("MAP key type must be VARCHAR for Avro encoding");
//                }
//                VarCharVector keyVector = (VarCharVector) entryVector.getChildrenFromFields().get(0);
//                FieldVector valueVector = entryVector.getChildrenFromFields().get(1);
//                Producer<?> keyProducer = new AvroStringProducer(keyVector);
//                Producer<?> valueProducer =
//                        createProducer(valueVector, valueVector.getField().isNullable(), dictionaries);
//                Producer<?> entryProducer =
//                        new AvroStructProducer(entryVector, new Producer<?>[] {keyProducer, valueProducer});
//                return new AvroMapProducer(mapVector, entryProducer);
//
//            case STRUCT:
//                StructVector structVector = (StructVector) vector;
//                List<FieldVector> childVectors = structVector.getChildrenFromFields();
//                Producer<?>[] childProducers = new Producer<?>[childVectors.size()];
//                for (int i = 0; i < childVectors.size(); i++) {
//                    FieldVector childVector = childVectors.get(i);
//                    childProducers[i] =
//                            createProducer(childVector, childVector.getField().isNullable(), dictionaries);
//                }
//                return new AvroStructProducer(structVector, childProducers);

            // Support for UNION and DENSEUNION is not currently available
            // This is pending fixes in the implementation of the union vectors themselves
            // https://github.com/apache/arrow-java/issues/108

            default:

                // Not all Arrow types are supported for encoding (yet)!
                var error = String.format(
                        "Encoding Arrow type %s is not currently supported",
                        vector.getMinorType().name());

                throw new UnsupportedOperationException(error);
        }
    }
}
