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

package org.finos.tracdap.common.validation.api;

import org.finos.tracdap.api.*;
import org.finos.tracdap.api.internal.InternalMetadataProto;
import org.finos.tracdap.api.internal.InternalMetadataApiGrpc;
import org.finos.tracdap.common.validation.core.ValidationContext;
import org.finos.tracdap.common.validation.core.ValidationType;
import org.finos.tracdap.common.validation.core.Validator;


@Validator(type = ValidationType.STATIC, serviceFile = InternalMetadataProto.class, serviceName = InternalMetadataApiGrpc.SERVICE_NAME)
public class InternalMetadataApiValidator {

    // Let's not introduce validation differences between the public and internal metadata API
    // Ideally, they should be made into the same API, with differences managed by permissions
    // Or at the very least, two instances of the same API with different settings
    // Currently, the check for what can be done via the public/internal API is in the service implementation
    // So, validation only needs to worry about what is a semantically valid request
    // This avoids mixing semantics with authorisation

    // Based on this thinking, internal API validator is just a wrapper on public API validator

    @Validator(method = "createObject")
    public static ValidationContext createObject(MetadataWriteRequest msg, ValidationContext ctx) {
        return MetadataApiValidator.createObject(msg, ctx, MetadataApiValidator.INTERNAL_API, null);
    }

    @Validator(method = "updateObject")
    public static ValidationContext updateObject(MetadataWriteRequest msg, ValidationContext ctx) {
        return MetadataApiValidator.updateObject(msg, ctx, MetadataApiValidator.INTERNAL_API, null);
    }

    @Validator(method = "updateTag")
    public static ValidationContext updateTag(MetadataWriteRequest msg, ValidationContext ctx) {
        return MetadataApiValidator.updateTag(msg, ctx, MetadataApiValidator.INTERNAL_API, null);
    }

    @Validator(method = "preallocateId")
    public static ValidationContext preallocateId(MetadataWriteRequest msg, ValidationContext ctx) {
        return MetadataApiValidator.preallocateId(msg, ctx);  // always an internal call
    }

    @Validator(method = "createPreallocatedObject")
    public static ValidationContext createPreallocatedObject(MetadataWriteRequest msg, ValidationContext ctx) {
        return MetadataApiValidator.createPreallocatedObject(msg, ctx);  // always an internal call
    }

    @Validator(method = "writeBatch")
    public static ValidationContext writeBatch(MetadataWriteBatchRequest msg, ValidationContext ctx) {
        return MetadataApiValidator.writeBatch(msg, ctx, MetadataApiValidator.INTERNAL_API);
    }

    @Validator(method = "readObject")
    public static ValidationContext readObject(MetadataReadRequest msg, ValidationContext ctx) {
        return MetadataApiValidator.readObject(msg, ctx);
    }

    @Validator(method = "search")
    public static ValidationContext search(MetadataSearchRequest msg, ValidationContext ctx) {
        return MetadataApiValidator.search(msg, ctx);
    }

    @Validator(method = "readBatch")
    public static ValidationContext readBatch(MetadataBatchRequest msg, ValidationContext ctx) {
        return MetadataApiValidator.readBatch(msg, ctx);
    }

    @Validator(method = "platformInfo")
    public static ValidationContext platformInfo(PlatformInfoRequest msg, ValidationContext ctx) {
        return MetadataApiValidator.platformInfo(msg, ctx);
    }

    @Validator(method = "listTenants")
    public static ValidationContext listTenants(ListTenantsRequest msg, ValidationContext ctx) {
        return MetadataApiValidator.listTenants(msg, ctx);
    }

    // Internal config API is a mirror of the API on admin service

    @Validator(method = "createConfigObject")
    public static ValidationContext createConfigObject(ConfigWriteRequest msg, ValidationContext ctx) {
        return AdminApiValidator.createConfigObject(msg, ctx);
    }

    @Validator(method = "updateConfigObject")
    public static ValidationContext updateConfigObject(ConfigWriteRequest msg, ValidationContext ctx) {
        return AdminApiValidator.updateConfigObject(msg, ctx);
    }

    @Validator(method = "deleteConfigObject")
    public static ValidationContext deleteConfigObject(ConfigWriteRequest msg, ValidationContext ctx) {
        return AdminApiValidator.deleteConfigObject(msg, ctx);
    }

    @Validator(method = "readConfigEntry")
    public static ValidationContext readConfigEntry(ConfigReadRequest msg, ValidationContext ctx) {
        return AdminApiValidator.readConfigObject(msg, ctx);
    }

    @Validator(method = "readConfigBatch")
    public static ValidationContext readConfigBatch(ConfigReadBatchRequest msg, ValidationContext ctx) {
        return AdminApiValidator.readConfigBatch(msg, ctx);
    }

    @Validator(method = "listConfigEntries")
    public static ValidationContext listConfigEntries(ConfigListRequest msg, ValidationContext ctx) {
        return AdminApiValidator.listConfigEntries(msg, ctx);
    }
}
