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

import org.finos.tracdap.common.exception.ETracInternal;
import org.finos.tracdap.common.storage.IStorageLayout;
import org.finos.tracdap.metadata.*;

import java.util.Random;
import java.util.UUID;

public class ObjectIdLayout implements IStorageLayout {

    public static final String LAYOUT_NAME = "object_id";

    private final Random random;

    private static final String FILE_STORAGE_PATH_TEMPLATE = "file/%s/version-%d%s/%s";
    private static final String FILE_STORAGE_PATH_SUFFIX_TEMPLATE = "-x%06x";

    private static final String DATA_STORAGE_PATH_TEMPLATE = "data/%s/%s/%s/snap-%d/delta-%d-x%06x";

    public ObjectIdLayout() {
        this.random = new Random(System.nanoTime());
    }

    @Override
    public String fileLayout(TagHeader objectId, FileDefinition fileDef) {

        var fileId = UUID.fromString(objectId.getObjectId());
        var fileVersion = objectId.getObjectVersion();
        var fileName = fileDef.getName();

        var storageSuffixBytes = random.nextInt(1 << 24);
        var storageSuffix = String.format(FILE_STORAGE_PATH_SUFFIX_TEMPLATE, storageSuffixBytes);

        return String.format(FILE_STORAGE_PATH_TEMPLATE, fileId, fileVersion, storageSuffix, fileName);
    }

    @Override
    public String dataLayout(
            TagHeader objectId, DataDefinition dataDef, SchemaDefinition schemaDef,
            PartKey partKey, int snap, int delta) {

        SchemaDefinition schema;

        if (dataDef.hasSchema())
            schema = dataDef.getSchema();
        else if (dataDef.hasSchemaId() && schemaDef != null)
            schema = schemaDef;
        else
            throw new ETracInternal("Schema definition not available");

        var dataType = schema.getSchemaType().name().toLowerCase();
        var dataId = objectId.getObjectId();
        var partId = partKey.getOpaqueKey();
        var suffixBytes = random.nextInt(1 << 24);

        return String.format(DATA_STORAGE_PATH_TEMPLATE,
                dataType, dataId,
                partId, snap, delta,
                suffixBytes);
    }
}
