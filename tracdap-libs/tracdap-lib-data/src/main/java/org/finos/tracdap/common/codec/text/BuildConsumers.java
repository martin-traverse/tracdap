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

package org.finos.tracdap.common.codec.text;

import org.apache.arrow.vector.*;
import org.finos.tracdap.common.codec.text.consumers.*;

import java.util.List;
import java.util.stream.Collectors;

public class BuildConsumers {

    public static List<IJsonConsumer<?>> createConsumers(List<FieldVector> vectors) {

        return vectors.stream()
                .map(BuildConsumers::createConsumer)
                .collect(Collectors.toList());
    }

    public static IJsonConsumer<?>
    createConsumer(FieldVector vector) {

        // Create a producer for the specific vector type
        switch (vector.getMinorType()) {

            // Null type

            case NULL:
                break;

            // Numeric types

            case BIT:
                return new JsonScalarConsumer<>(new JsonBitConsumer((BitVector) vector));
            case TINYINT:
                return new JsonScalarConsumer<>(new JsonTinyIntConsumer((TinyIntVector) vector));
            case SMALLINT:
                return new JsonScalarConsumer<>(new JsonSmallIntConsumer((SmallIntVector) vector));
            case INT:
                return new JsonScalarConsumer<>(new JsonIntConsumer((IntVector) vector));
            case BIGINT:
                return new JsonScalarConsumer<>(new JsonBigIntConsumer((BigIntVector) vector));
            case UINT1:
                return new JsonScalarConsumer<>(new JsonUInt1Consumer((UInt1Vector) vector));
            case UINT2:
                return new JsonScalarConsumer<>(new JsonUInt2Consumer((UInt2Vector) vector));
            case UINT4:
                return new JsonScalarConsumer<>(new JsonUInt4Consumer((UInt4Vector) vector));
            case UINT8:
                return new JsonScalarConsumer<>(new JsonUInt8Consumer((UInt8Vector) vector));
            case FLOAT2:
                return new JsonScalarConsumer<>(new JsonFloat2Consumer((Float2Vector) vector));
            case FLOAT4:
                return new JsonScalarConsumer<>(new JsonFloat4Consumer((Float4Vector) vector));
            case FLOAT8:
                return new JsonScalarConsumer<>(new JsonFloat8Consumer((Float8Vector) vector));
            case DECIMAL:
                return new JsonScalarConsumer<>(new JsonDecimalConsumer((DecimalVector) vector));
            case DECIMAL256:
                return new JsonScalarConsumer<>(new JsonDecimal256Consumer((Decimal256Vector) vector));

            // Text / binary types

            case VARCHAR:
                return new JsonScalarConsumer<>(new JsonVarCharConsumer((VarCharVector) vector));

            // Temporal types

            case DATEDAY:
                return new JsonScalarConsumer<>(new JsonDateDayConsumer((DateDayVector) vector));
            case TIMESTAMPMILLI:
                return new JsonScalarConsumer<>(new JsonTimestampMilliConsumer((TimeStampMilliVector) vector));

        }

        throw new RuntimeException("Unsupported vector type: " + vector.getMinorType());

    }
}
