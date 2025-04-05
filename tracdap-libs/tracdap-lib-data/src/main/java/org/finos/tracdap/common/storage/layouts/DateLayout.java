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

package org.finos.tracdap.common.storage.layouts;

import org.finos.tracdap.common.metadata.MetadataCodec;
import org.finos.tracdap.common.storage.IStorageLayout;
import org.finos.tracdap.metadata.*;

import java.util.Random;

public class DateLayout implements IStorageLayout {

    private final Random random;

    private static final String FILE_STORAGE_PATH_TEMPLATE = "%d/%s/%s-%s/version-%d%s/%s";
    private static final String DATA_STORAGE_PATH_TEMPLATE = "%d/%s/%s-%s/%s/snap-%d/delta-%d-%s";

    private static final String STORAGE_SUFFIX_TEMPLATE = "-x%06x";

    public DateLayout() {
        this.random = new Random(System.nanoTime());
    }

    @Override
    public String fileLayout(TagHeader objectId, FileDefinition fileDef) {

        var timestamp = MetadataCodec.decodeDatetime(objectId.getObjectTimestamp());
        var localDate = timestamp.toLocalDate();
        var isoDate = localDate.format(MetadataCodec.ISO_DATE_FORMAT);
        var year = localDate.getYear();

        var objectType = objectId.getObjectType().name();
        var objectUuid = objectId.getObjectId();
        var objectVersion = objectId.getObjectVersion();

        var storageSuffixBytes = random.nextInt(1 << 24);
        var storageSuffix = String.format(STORAGE_SUFFIX_TEMPLATE, storageSuffixBytes);

        return String.format(
                FILE_STORAGE_PATH_TEMPLATE,
                year, isoDate, objectType, objectUuid,
                objectVersion, storageSuffix, fileDef.getName());
    }

    @Override
    public String dataLayout(
            TagHeader objectId, DataDefinition dataDef, SchemaDefinition schemaDef,
            PartKey partKey, int snap, int delta) {

    }

    private String newDataLocation(
            TagHeader objectId, DataDefinition dataDef,
            PartKey partKey, int snap, int delta) {

        var timestamp = MetadataCodec.decodeDatetime(objectId.getObjectTimestamp());
        var localDate = timestamp.toLocalDate();
        var isoDate = localDate.format(MetadataCodec.ISO_DATE_FORMAT);
        var year = localDate.getYear();

        var objectType = objectId.getObjectType().name();
        var objectUuid = objectId.getObjectId();
        var partId = partKey.getOpaqueKey();

        var storageSuffixBytes = random.nextInt(1 << 24);
        var storageSuffix = String.format(STORAGE_SUFFIX_TEMPLATE, storageSuffixBytes);

        return String.format(
                DATA_STORAGE_PATH_TEMPLATE,
                year, isoDate, objectType, objectUuid,
                partId, snap, delta, storageSuffix);
    }
}
