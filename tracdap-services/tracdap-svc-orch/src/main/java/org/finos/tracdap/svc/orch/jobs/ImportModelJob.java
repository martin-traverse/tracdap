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

package org.finos.tracdap.svc.orch.jobs;

import org.finos.tracdap.api.MetadataWriteRequest;
import org.finos.tracdap.api.internal.RuntimeJobResult;
import org.finos.tracdap.api.internal.RuntimeJobResultAttrs;
import org.finos.tracdap.common.config.IDynamicResources;
import org.finos.tracdap.common.exception.EExecutorValidation;
import org.finos.tracdap.common.exception.EUnexpected;
import org.finos.tracdap.common.metadata.MetadataBundle;
import org.finos.tracdap.common.metadata.MetadataUtil;
import org.finos.tracdap.config.JobConfig;
import org.finos.tracdap.metadata.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.finos.tracdap.common.metadata.MetadataCodec.encodeValue;
import static org.finos.tracdap.common.metadata.MetadataConstants.*;


public class ImportModelJob implements IJobLogic {

    @Override
    public JobDefinition applyTransform(JobDefinition job, MetadataBundle metadata, IDynamicResources resources) {

        // Fill in package and packageGroup properties for models using Git repos

        // TODO: This is very specialized logic
        // The intent is to set TRAC_MODEL_PACKAGE and TRAC_MODEL_PACKAGE_GROUP for models stored in Git
        // A more explicit solution would be better, e.g. explicit settings in the repo config

        if (!job.getImportModel().getPackage().isBlank())
            return job;

        // Validation on resources is already performed by the job consistency validator
        // getStrictResource() will throw if there is an unexpected error (e..g. config sync issues)
        var repoKey = job.getImportModel().getRepository();
        var repoConfig = resources.getStrictEntry(repoKey, ResourceType.MODEL_REPOSITORY);

        if (!repoConfig.getProtocol().equalsIgnoreCase("git"))
            return job;

        if (!repoConfig.containsProperties("repoUrl")){
            var message = String.format("Git configuration for [%s] is missing required property [repoUrl]", repoKey);
            throw new EExecutorValidation(message);
        }

        var repoUrlProp = repoConfig.getPropertiesOrThrow("repoUrl");
        var repoUrl = URI.create(repoUrlProp);
        var repoPathSegments = repoUrl.getPath().split("[/\\\\]");

        var importDef = job.getImportModel().toBuilder();

        if (repoPathSegments.length >= 1)
            importDef.setPackage(repoPathSegments[repoPathSegments.length - 1]);

        if (repoPathSegments.length >= 2)
            importDef.setPackageGroup(repoPathSegments[repoPathSegments.length - 2]);

        return job.toBuilder()
                .setImportModel(importDef)
                .build();
    }

    @Override
    public MetadataBundle applyMetadataTransform(JobDefinition job, MetadataBundle metadata, IDynamicResources resources) {

        return metadata;
    }

    @Override
    public List<TagSelector> requiredMetadata(JobDefinition job) {

        // No extra metadata needed for an import_model job

        return List.of();
    }

    @Override
    public Map<ObjectType, Integer> preallocateIds(JobDefinition job, MetadataBundle metadata) {

        // Allocate a single ID for the new model
        return Map.of(ObjectType.MODEL, 1);
    }

    @Override
    public Map<String, MetadataWriteRequest> newResultIds(
            String tenant, JobDefinition job,
            Map<String, ObjectDefinition> resources,
            Map<String, TagHeader> resourceMapping) {

        return Map.of();  // not currently used
    }

    @Override
    public Map<String, TagHeader> priorResultIds(
            JobDefinition job,
            Map<String, ObjectDefinition> resources,
            Map<String, TagHeader> resourceMapping) {

        // Model updates not supported yet

        return Map.of();
    }

    @Override
    public JobDefinition setResultIds(
            JobDefinition job,
            Map<String, TagHeader> resultMapping,
            Map<String, ObjectDefinition> resources,
            Map<String, TagHeader> resourceMapping) {

        return job;
    }

    @Override
    public RuntimeJobResult processResult(JobConfig jobConfig, RuntimeJobResult runtimeResult) {

        var modelIds = runtimeResult.getObjectIdsList().stream()
                .filter(objectId -> objectId.getObjectType() == ObjectType.MODEL)
                .collect(Collectors.toList());

        if (modelIds.size() != 1)
            throw new EUnexpected();  // TODO

        var modelId = modelIds.get(0);
        var modelKey = MetadataUtil.objectKey(modelId);

        if (!runtimeResult.containsObjects(modelKey))
            throw new EUnexpected();  // TODO

        var modelObj = runtimeResult.getObjectsOrThrow(modelKey);
        var modelAttrs = runtimeResult.getAttrsOrDefault(modelKey, RuntimeJobResultAttrs.getDefaultInstance()).getAttrsList();
        var modelDef = modelObj.getModel();

        // Add attrs defined in the model code
        // TODO: Is this needed if runtime can return attrs anyway?
        for (var staticAttr : modelDef.getStaticAttributesMap().entrySet()) {

            modelAttrs.add(TagUpdate.newBuilder()
                    .setOperation(TagOperation.CREATE_OR_REPLACE_ATTR)
                    .setAttrName(staticAttr.getKey())
                    .setValue(staticAttr.getValue())
                    .build());
        }

        // Add attrs defined in the job
        var suppliedAttrs = jobConfig.getJob().getImportModel().getModelAttrsList();
        modelAttrs.addAll(suppliedAttrs);

        // Add controlled attrs for models

        modelAttrs.add(TagUpdate.newBuilder()
                .setAttrName(TRAC_MODEL_LANGUAGE)
                .setValue(encodeValue(modelDef.getLanguage()))
                .build());

        modelAttrs.add(TagUpdate.newBuilder()
                .setAttrName(TRAC_MODEL_REPOSITORY)
                .setValue(encodeValue(modelDef.getRepository()))
                .build());

        if (modelDef.hasPackageGroup()) {

            modelAttrs.add(TagUpdate.newBuilder()
                    .setAttrName(TRAC_MODEL_PACKAGE_GROUP)
                    .setValue(encodeValue(modelDef.getPackageGroup()))
                    .build());
        }

        modelAttrs.add(TagUpdate.newBuilder()
                .setAttrName(TRAC_MODEL_PACKAGE)
                .setValue(encodeValue(modelDef.getPackage()))
                .build());

        modelAttrs.add(TagUpdate.newBuilder()
                .setAttrName(TRAC_MODEL_VERSION)
                .setValue(encodeValue(modelDef.getVersion()))
                .build());

        modelAttrs.add(TagUpdate.newBuilder()
                .setAttrName(TRAC_MODEL_ENTRY_POINT)
                .setValue(encodeValue(modelDef.getEntryPoint()))
                .build());

        if (modelDef.hasPath()) {

            modelAttrs.add(TagUpdate.newBuilder()
                    .setAttrName(TRAC_MODEL_PATH)
                    .setValue(encodeValue(modelDef.getPath()))
                    .build());
        }

        return RuntimeJobResult.newBuilder()
                .addObjectIds(modelId)
                .putObjects(modelKey, modelObj)
                .putAttrs(modelKey, RuntimeJobResultAttrs.newBuilder().addAllAttrs(modelAttrs).build())
                .build();
    }
}
