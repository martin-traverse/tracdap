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

import org.apache.arrow.vector.*;
import org.apache.avro.io.Encoder;
import org.finos.tracdap.common.exception.EDataTypeNotSupported;
import org.finos.tracdap.common.metadata.MetadataCodec;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;


public class AvroValues {

    public static void getAndGenerate(FieldVector vector, int row, Encoder encoder) throws IOException {

        boolean isNull = vector.isNull(row);

        if (isNull) {
            encoder.writeNull();
            return;
        }

        var minorType = vector.getMinorType();

        switch (minorType) {

            case BIT:

                BitVector boolVec = (BitVector) vector;
                int boolVal = boolVec.get(row);

                encoder.writeBoolean(boolVal != 0);

                break;

            case BIGINT:

                BigIntVector int64Vec = (BigIntVector) vector;
                long int64Val = int64Vec.get(row);

                encoder.writeLong(int64Val);

                break;

            case INT:

                IntVector int32Vec = (IntVector) vector;
                int int32Val = int32Vec.get(row);

                encoder.writeInt(int32Val);

                break;

            case SMALLINT:

                SmallIntVector int16Vec = (SmallIntVector) vector;
                short int16Val = int16Vec.get(row);

                generator.writeNumber(int16Val);

                break;

            case TINYINT:

                TinyIntVector int8Vec = (TinyIntVector) vector;
                byte int8Val = int8Vec.get(row);

                generator.writeNumber(int8Val);

                break;

            case FLOAT8:

                Float8Vector doubleVec = (Float8Vector) vector;
                double doubleVal = doubleVec.get(row);

                encoder.writeDouble(doubleVal);

                break;

            case FLOAT4:

                Float4Vector floatVec = (Float4Vector) vector;
                float floatVal = floatVec.get(row);

                encoder.writeFloat(floatVal);

                break;

            case DECIMAL:

                DecimalVector decimal128Vec = (DecimalVector) vector;
                BigDecimal decimal128Val = decimal128Vec.getObject(row);

                // This will render zeroes as "0" when the scale is large, preferable to 0e-12
                // For small scales use the default rendering, particularly currency with scale == 2

                if (decimal128Vec.getScale() > 3 && BigDecimal.ZERO.compareTo(decimal128Val) == 0)
                    generator.writeString(BigDecimal.ZERO.toString());
                else
                    generator.writeString(decimal128Val.toString());

                break;

            case DECIMAL256:

                Decimal256Vector decimal256Vec = (Decimal256Vector) vector;
                BigDecimal decimal256Val = decimal256Vec.getObject(row);

                if (decimal256Vec.getScale() > 3 && BigDecimal.ZERO.compareTo(decimal256Val) == 0)
                    generator.writeString(BigDecimal.ZERO.toString());
                else
                    generator.writeString(decimal256Val.toString());

                break;

            case VARCHAR:

                VarCharVector varcharVec = (VarCharVector) vector;
                String varcharVal = new String(varcharVec.get(row), StandardCharsets.UTF_8);

                encoder.writeString(varcharVal);

                break;

            case DATEDAY:

                DateDayVector dateVec = (DateDayVector) vector;
                int unixEpochDay = dateVec.get(row);
                LocalDate dateVal = LocalDate.ofEpochDay(unixEpochDay);
                String dateStr = dateVal.format(MetadataCodec.ISO_DATE_FORMAT);

                generator.writeString(dateStr);

                break;

            case TIMESTAMPMILLI:

                TimeStampMilliVector timeStampMVec = (TimeStampMilliVector) vector;

                long epochMillis = timeStampMVec.get(row);
                long epochSeconds = epochMillis / 1000;
                int nanos = (int) (epochMillis % 1000) * 1000000;

                if (epochSeconds < 0 && nanos != 0) {
                    --epochSeconds;
                    nanos = nanos + 1000000000;
                }

                LocalDateTime localDatetimeVal = LocalDateTime.ofEpochSecond(epochSeconds, nanos, ZoneOffset.UTC);
                OffsetDateTime offsetDatetimeVal = localDatetimeVal.atOffset(ZoneOffset.UTC);
                String datetimeStr = MetadataCodec.ISO_DATETIME_NO_ZONE_FORMAT.format(offsetDatetimeVal);

                generator.writeString(datetimeStr);

                break;

            // For handling TZ type:
            // 1. ArrowType.Timestamp mtzType = (ArrowType.Timestamp) field.getType();
            // 2. ZoneOffset mtzOffset = ZoneOffset.of(mtzType.getTimezone());

            default:

                // This error does not relate to the data, only to the target column type
                // So, do not include parse location in the error message

                var field = vector.getField();

                var err = String.format(
                        "Data type not supported for field: [%s] %s (%s)",
                        field.getName(), field.getType(), vector.getMinorType());

                log.error(err);
                throw new EDataTypeNotSupported(err);
        }
    }
}
