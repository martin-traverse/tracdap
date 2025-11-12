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

package org.finos.tracdap.common.storage;

import org.finos.tracdap.metadata.*;

public class StorageLayoutItem {

    private final TagHeader dataId;
    private final DataDefinition data;
    private final SchemaDefinition schema;
    private final StorageDefinition storage;

    private final PartKey part;
    private final int snap;
    private final int delta;

    public StorageLayoutItem(
            TagHeader dataId, DataDefinition data, SchemaDefinition schema,
            PartKey part, int snap, int delta) {

        this.dataId = dataId;

        this.data = data;
        this.schema = schema;
        this.storage = null;

        this.part = part;
        this.snap = snap;
        this.delta = delta;
    }

    public StorageLayoutItem(DataDefinition data, SchemaDefinition schema, StorageDefinition storage) {

        this.dataId = null;

        this.data = data;
        this.schema = schema;
        this.storage = storage;

        this.part = null;
        this.snap = 0;
        this.delta = 0;
    }

    public TagHeader dataId() {
        return dataId;
    }

    public DataDefinition data() {
        return data;
    }

    public SchemaDefinition schema() {
        return schema;
    }

    public StorageDefinition storage() {
        return storage;
    }

    public PartKey part() {
        return part;
    }

    public int snap() {
        return snap;
    }

    public int delta() {
        return delta;
    }
}
