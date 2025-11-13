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

import org.finos.tracdap.common.exception.EDataCorruption;
import org.finos.tracdap.common.metadata.MetadataCodec;
import org.finos.tracdap.common.storage.IStorageLayout;
import org.finos.tracdap.common.storage.LayoutItem;

import java.time.OffsetDateTime;
import java.util.Random;


public class DateSnapLayout implements IStorageLayout {

    // YEAR / DATE / TIME - OBJECT ID / PART / SNAP / DELTAS & CHUNKS
    private static final String STORAGE_PATH_TEMPLATE = "%d/%d-%d-%d/%s-%s/%s/snap-%d/delta-%d-x%06x-chunk-%d.%s";

    // YEAR / DATE / TIME - OBJECT ID / VERSION / FILENAME
    private static final String FILE_STORAGE_PATH_TEMPLATE = "%d/%d-%d-%d/%s-%s/version-%d-x%06x/%s";

    private final Random random;

    public DateSnapLayout() {
        this.random = new Random();
    }

    @Override
    public String newFilePath(LayoutItem layoutItem) {

        var timestamp = MetadataCodec.decodeDatetime(layoutItem.header().getObjectTimestamp());
        return fileStoragePath(layoutItem, timestamp);
    }

    @Override
    public String updateFilePath(LayoutItem layoutItem, LayoutItem priorLayoutItem) {

        var timestamp = MetadataCodec.decodeDatetime(layoutItem.header().getObjectTimestamp());
        return fileStoragePath(layoutItem, timestamp);
    }

    private String fileStoragePath(LayoutItem layoutItem, OffsetDateTime timestamp) {

        var date = timestamp.toLocalDate();

        var objectId = layoutItem.header().getObjectId();
        var objectVersion = layoutItem.header().getObjectVersion();
        var versionSuffix = random.nextInt(1 << 24);
        var fileName = layoutItem.file().getName();

        return String.format(FILE_STORAGE_PATH_TEMPLATE,
                date.getYear(), date.getYear(), date.getMonthValue(), date.getDayOfMonth(),
                "TODO", objectId, objectVersion, versionSuffix, fileName);
    }

    @Override
    public String newDataPath(LayoutItem layoutItem) {

        var timestamp = MetadataCodec.decodeDatetime(layoutItem.header().getObjectTimestamp());

        return dataStoragePath(layoutItem, timestamp);
    }

    @Override
    public String updateDataPath(LayoutItem layoutItem, LayoutItem priorLayoutItem) {

        // The date snap layout is fully deterministic for a new snap
        if (layoutItem.delta() == 0)
            return newDataPath(layoutItem);

        var part = layoutItem.part().getOpaqueKey();
        var priorData = priorLayoutItem.data();

        if (!priorData.containsParts(part))
            throw new EDataCorruption("");  // TODO

        var priorPart = priorData.getPartsOrThrow(part);
        var priorSnap = priorPart.getSnap();

        if (priorSnap.getSnapIndex() != layoutItem.snap())
            throw new EDataCorruption("");  // TODO

        if (priorSnap.getDeltasCount() != layoutItem.delta())
            throw new EDataCorruption("");  // TODO

        var priorDelta = priorSnap.getDeltas(layoutItem.delta() - 1);

        if (!priorLayoutItem.storage().containsDataItems(priorDelta.getDataItem()))
            throw new EDataCorruption("");  // TODO

        var priorStorageItem = priorLayoutItem.storage().getDataItemsOrThrow(priorDelta.getDataItem());
        var priorIncarnation = priorStorageItem.getIncarnations(priorStorageItem.getIncarnationsCount() - 1);


        var timestamp = MetadataCodec.decodeDatetime(priorIncarnation.getIncarnationTimestamp());

        return dataStoragePath(layoutItem, timestamp);
    }

    private String dataStoragePath(LayoutItem layoutItem, OffsetDateTime timestamp) {

        var date = timestamp.toLocalDate();

        var objectId = layoutItem.header().getObjectId();
        var part = layoutItem.part().getOpaqueKey();
        var snap = layoutItem.snap();
        var delta = layoutItem.delta();
        var deltaSuffix = random.nextInt(1 << 24);
        var chunk = 0;
        var extension = "";

        return String.format(STORAGE_PATH_TEMPLATE,
                date.getYear(), date.getYear(), date.getMonthValue(), date.getDayOfMonth(),
                "TODO", objectId, part, snap, delta, deltaSuffix, chunk, extension);
    }
}
