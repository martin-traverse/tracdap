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

package org.finos.tracdap.svc.orch.service;

import org.finos.tracdap.api.*;
import org.finos.tracdap.api.internal.InternalMetadataApiGrpc;
import org.finos.tracdap.api.internal.RuntimeJobResult;
import org.finos.tracdap.common.config.ConfigHelpers;
import org.finos.tracdap.common.config.ConfigKeys;
import org.finos.tracdap.common.config.ConfigManager;
import org.finos.tracdap.common.config.IDynamicResources;
import org.finos.tracdap.common.exception.EConsistencyValidation;
import org.finos.tracdap.common.exception.EUnexpected;
import org.finos.tracdap.common.metadata.MetadataBundle;
import org.finos.tracdap.common.metadata.MetadataCodec;
import org.finos.tracdap.common.metadata.MetadataConstants;
import org.finos.tracdap.common.metadata.MetadataUtil;
import org.finos.tracdap.common.middleware.GrpcConcern;
import org.finos.tracdap.common.plugin.PluginRegistry;
import org.finos.tracdap.config.*;
import org.finos.tracdap.metadata.*;
import org.finos.tracdap.svc.orch.jobs.JobLogic;

import io.grpc.stub.AbstractStub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.finos.tracdap.common.metadata.MetadataCodec.encodeValue;
import static org.finos.tracdap.common.metadata.MetadataConstants.*;


public class JobProcessorHelpers {

//    private static final String JOB_RESULT_KEY = "trac_job_result";
//    private static final String JOB_LOG_FILE_KEY = "trac_job_log_file";
//    private static final String JOB_LOG_STORAGE_KEY = "trac_job_log_file:STORAGE";

    private final Logger log = LoggerFactory.getLogger(JobProcessorHelpers.class);

    private final PlatformConfig platformConfig;
    private final IDynamicResources resources;
    private final GrpcConcern commonConcerns;

    private final InternalMetadataApiGrpc.InternalMetadataApiBlockingStub metaClient;
    private final ConfigManager configManager;


    public JobProcessorHelpers(
            PlatformConfig platformConfig,
            IDynamicResources resources,
            GrpcConcern commonConcerns,
            PluginRegistry registry) {

        this.platformConfig = platformConfig;
        this.resources = resources;
        this.commonConcerns = commonConcerns;

        this.metaClient = registry.getSingleton(InternalMetadataApiGrpc.InternalMetadataApiBlockingStub.class);
        this.configManager = registry.getSingleton(ConfigManager.class);
    }

    JobState loadMetadata(JobState jobState) {

        var jobLogic = JobLogic.forJobType(jobState.jobType);
        var selectors = jobLogic.requiredMetadata(jobState.definition);

        if (selectors.isEmpty()) {
            log.info("No additional metadata required");
            return jobState;
        }

        return loadMetadata(jobState, selectors);
    }

    private JobState loadMetadata(JobState jobState, List<TagSelector> selectors) {

        log.info("Loading additional required metadata...");

        var orderedKeys = new ArrayList<String>(selectors.size());
        var orderedSelectors = new ArrayList<TagSelector>(selectors.size());

        for (var selector : selectors) {
            orderedKeys.add(MetadataUtil.objectKey(selector));
            orderedSelectors.add(selector);
        }

        var batchRequest = MetadataBatchRequest.newBuilder()
                .setTenant(jobState.tenant)
                .addAllSelector(orderedSelectors)
                .build();

        var client = configureClient(metaClient, jobState);
        var batchResponse = client.readBatch(batchRequest);

        return loadMetadataResponse(jobState, orderedKeys, batchResponse);
    }

    private JobState loadMetadataResponse(
            JobState jobState, List<String> orderedKeys,
            MetadataBatchResponse batchResponse) {

        if (batchResponse.getTagCount() != orderedKeys.size())
            throw new EUnexpected();

        var objectMapping = new HashMap<String, TagHeader>(orderedKeys.size());
        var objects = new HashMap<String, ObjectDefinition>(orderedKeys.size());;
        var tags = new HashMap<String, Tag>(orderedKeys.size());

        for (var i = 0; i < orderedKeys.size(); i++) {

            var orderedKey = orderedKeys.get(i);
            var orderedTag = batchResponse.getTag(i);

            var objectKey = MetadataUtil.objectKey(orderedTag.getHeader());
            var object_ = orderedTag.getDefinition();
            var tag = orderedTag.toBuilder().clearDefinition().build();

            objectMapping.put(orderedKey, tag.getHeader());
            objects.put(objectKey, object_);
            tags.put(objectKey, tag);
        }

        jobState.objectMapping.putAll(objectMapping);
        jobState.objects.putAll(objects);
        jobState.tags.putAll(tags);

        var dependencies = dependantMetadata(batchResponse.getTagList());

        var missingDependencies = dependencies.stream()
                .filter(selector -> !jobState.objects.containsKey(MetadataUtil.objectKey(selector)))
                .filter(selector -> !jobState.objectMapping.containsKey(MetadataUtil.objectKey(selector)))
                .collect(Collectors.toList());

        if (!missingDependencies.isEmpty())
            return loadMetadata(jobState, missingDependencies);

        return jobState;
    }

    private List<TagSelector> dependantMetadata(List<Tag> tags) {

        var dependencies = new ArrayList<TagSelector>();

        for (var tag : tags) {

            var obj = tag.getDefinition();

            if (obj.getObjectType() == ObjectType.DATA) {

                var dataDef = obj.getData();
                dependencies.add(dataDef.getStorageId());

                if (dataDef.hasSchemaId())
                    dependencies.add(dataDef.getSchemaId());
            }

            else if (obj.getObjectType() == ObjectType.FILE) {

                var fileDef = obj.getFile();
                dependencies.add(fileDef.getStorageId());
            }
        }

        return dependencies;
    }

    JobState applyTransform(JobState jobState) {

        var jobLogic = JobLogic.forJobType(jobState.jobType);
        var metadata = new MetadataBundle(jobState.objectMapping, jobState.objects, jobState.tags);

        jobState.definition = jobLogic.applyTransform(jobState.definition, metadata, resources);

        var updatedMetadata = jobLogic.applyMetadataTransform(jobState.definition, metadata, resources);
        jobState.objects = updatedMetadata.getObjects();
        jobState.objectMapping = updatedMetadata.getObjectMapping();

        return jobState;
    }

    JobState saveJobDefinition(JobState jobState) {

        var jobObj = ObjectDefinition.newBuilder()
                .setObjectType(ObjectType.JOB)
                .setJob(jobState.definition)
                .build();

        var ctrlJobAttrs = List.of(
                TagUpdate.newBuilder()
                        .setAttrName(TRAC_JOB_TYPE_ATTR)
                        .setValue(MetadataCodec.encodeValue(jobState.jobType.toString()))
                        .build(),
                TagUpdate.newBuilder()
                        .setAttrName(TRAC_JOB_STATUS_ATTR)
                        .setValue(MetadataCodec.encodeValue(jobState.tracStatus.toString()))
                        .build());

        var freeJobAttrs = jobState.jobRequest.getJobAttrsList();

        var jobWriteReq = MetadataWriteRequest.newBuilder()
                .setTenant(jobState.tenant)
                .setObjectType(ObjectType.JOB)
                .setDefinition(jobObj)
                .addAllTagUpdates(ctrlJobAttrs)
                .addAllTagUpdates(freeJobAttrs)
                .build();

        var client = configureClient(metaClient, jobState);

        var jobId = client.createObject(jobWriteReq);

        jobState.jobId = jobId;

        jobState.jobConfig = jobState.jobConfig
                .toBuilder()
                .setJobId(jobId)
                .build();

        return jobState;
    }

    JobState preallocateObjectIds(JobState jobState) {

        var batchRequest = MetadataWriteBatchRequest.newBuilder().setTenant(jobState.tenant);

        // One ID required for the RESULT object
        batchRequest.addPreallocateIds(preallocate(ObjectType.RESULT));

        // IDs required for the job log file
        batchRequest.addPreallocateIds(preallocate(ObjectType.FILE));
        batchRequest.addPreallocateIds(preallocate(ObjectType.STORAGE));

        // Add required IDs based on the job type
        var jobLogic = JobLogic.forJobType(jobState.jobType);
        var metadata = new MetadataBundle(jobState.objectMapping, jobState.objects, jobState.tags);
        var requiredIds = jobLogic.preallocateIds(jobState.definition, metadata);

        for (var requirement : requiredIds.entrySet()) {

            var objectType = requirement.getKey();
            var count = requirement.getValue();

            for (var i = 0; i < count; i++) {
                var request = MetadataWriteRequest.newBuilder().setObjectType(objectType);
                batchRequest.addPreallocateIds(request);
            }
        }

        // Allocate all the IDs in a single batch
        var metadataClient = configureClient(metaClient, jobState);
        var batchResponse = metadataClient.writeBatch(batchRequest.build());

        jobState.resultId = batchResponse.getPreallocateIds(0);

        jobState.preallocatedIds = batchResponse
                .getPreallocateIdsList()
                .subList(1, batchResponse.getPreallocateIdsCount());

        return jobState;
    }

    private MetadataWriteRequest.Builder preallocate(ObjectType objectType) {
        return MetadataWriteRequest.newBuilder().setObjectType(objectType);
    }

    JobState buildJobConfig(JobState jobState) {

        jobState.jobConfig = JobConfig.newBuilder()
                .setJob(jobState.definition)
                .setJobId(jobState.jobId)
                .setResultId(jobState.resultId)
                .putAllObjectMapping(jobState.objectMapping)
                .putAllObjects(jobState.objects)
                .putAllTags(jobState.tags)
                .addAllPreallocatedIds(jobState.preallocatedIds)
                .build();

        var sysConfig = RuntimeConfig.newBuilder();
        var storageConfig = StorageConfig.newBuilder();

        var internalStorage = resources.getMatchingEntries(
                resource -> resource.getResourceType() == ResourceType.INTERNAL_STORAGE);

        for (var storageEntry : internalStorage.entrySet()) {
            var storageKey = storageEntry.getKey();
            var storage = translateResourceConfig(storageKey, storageEntry.getValue(), jobState);
            storageConfig.putBuckets(storageKey, storage);
        }

        var externalStorage = resources.getMatchingEntries(
                resource -> resource.getResourceType() == ResourceType.EXTERNAL_STORAGE);

        for (var storageEntry : externalStorage.entrySet()) {
            var storageKey = storageEntry.getKey();
            var storage = translateResourceConfig(storageKey, storageEntry.getValue(), jobState);
            storageConfig.putExternal(storageKey, storage);
        }

        // Default storage / format still on platform config file for now
        if (platformConfig.containsTenants(jobState.tenant)) {
            var tenantConfig = platformConfig.getTenantsOrThrow(jobState.tenant);
            storageConfig.setDefaultBucket(tenantConfig.getDefaultBucket());
            storageConfig.setDefaultFormat(tenantConfig.getDefaultFormat());
        }
        else {
            storageConfig.setDefaultBucket(platformConfig.getStorage().getDefaultBucket());
            storageConfig.setDefaultFormat(platformConfig.getStorage().getDefaultFormat());
        }

        sysConfig.setStorage(storageConfig);

        var repositories = resources.getMatchingEntries(
                resource -> resource.getResourceType() == ResourceType.MODEL_REPOSITORY);

        for (var repoEntry : repositories.entrySet()) {

            var repoKey = repoEntry.getKey();

            // Only translate repositories required for the job
            if (jobUsesRepository(repoKey, jobState)) {
                var repoConfig = translateResourceConfig(repoKey, repoEntry.getValue(), jobState);
                sysConfig.putRepositories(repoKey, repoConfig);
            }
        }

        jobState.sysConfig = sysConfig.build();

        return jobState;
    }

    private boolean jobUsesRepository(String repoKey, JobState jobState) {

        // This method filters repo resources for the currently known job types
        // TODO: Generic resource filtering in the IJobLogic interface

        // Import model jobs can refer to repositories directly
        if (jobState.jobType == JobType.IMPORT_MODEL) {
            var importModelJob = jobState.definition.getImportModel();
            if (importModelJob.getRepository().equals(repoKey)) {
                return true;
            }
        }

        // Check if any resources refer to the repo key
        for (var object : jobState.objects.values()) {
            if (object.getObjectType() == ObjectType.MODEL) {
                if (object.getModel().getRepository().equals(repoKey)) {
                    return true;
                }
            }
        }

        return false;
    }

    private PluginConfig translateResourceConfig(String resourceKey, ResourceDefinition resource, JobState jobState) {

        var pluginConfig = ConfigHelpers.resourceToPluginConfig(resource).toBuilder();

        var tenantScope = String.format("/%s/%s/", ConfigKeys.TENANT_SCOPE, jobState.tenant);

        for (var secretEntry : pluginConfig.getSecretsMap().entrySet()) {

            var propertyName = secretEntry.getKey();
            var secretAlias = secretEntry.getValue();

            // Secret loader will not load the secret if the alias is not valid
            // This handling provides more meaningful errors
            if (secretAlias.isBlank() || !secretAlias.startsWith(tenantScope)) {

                var message = String.format("Resource configuration for [%s] is not valid", resourceKey);
                var detail = String.format("Inconsistent secret alias for [%s]", propertyName);

                log.error("{}: {}", message, detail);
                throw new EConsistencyValidation(message);
            }

            var secret = configManager.loadPassword(secretAlias);

            pluginConfig.putProperties(propertyName, secret);
        }

        pluginConfig.clearSecrets();

        return pluginConfig.build();
    }


    void processJobResult(JobState jobState) {

        var runtimeResult = jobState.executorResult;

        if (runtimeResult.getResult().getStatusCode() != JobStatusCode.SUCCEEDED) {

            // TODO
            throw new EUnexpected();
        }

        var resultLookup = buildResultLookup(runtimeResult);

        var commonResult = processCommonResult(runtimeResult, resultLookup);

        var jobLogic = JobLogic.forJobType(jobState.jobType);
        var jobResult = jobLogic.processResult(jobState.jobConfig, runtimeResult);

        var finalResult =  RuntimeJobResult.newBuilder()
                .mergeFrom(commonResult)
                .mergeFrom(jobResult)
                .build();

        if (finalResult.getObjectIdsCount() < runtimeResult.getObjectIdsCount() ||
            finalResult.getObjectsCount() < runtimeResult.getObjectsCount() ||
            finalResult.getAttrsCount() < runtimeResult.getAttrsCount()) {

            // TODO
            throw new EUnexpected();
        }

        jobState.executorResult = finalResult;

        // TODO: Move to a separate call
        saveJobResult(jobState);
    }

    private RuntimeJobResult processCommonResult(RuntimeJobResult runtimeResult, Map<String, TagHeader> resultLookup) {

        var resultBuilder = RuntimeJobResult.newBuilder()
                .setResultId(runtimeResult.getResultId())
                .setResult(runtimeResult.getResult());

        var logFileSelector = runtimeResult.getResult().getLogFileId();
        var logFileId = resultLookup.get(logFileSelector.getObjectId());
        var logFileKey = logFileId != null ? MetadataUtil.objectKey(logFileId) : null;

        if (logFileKey != null && runtimeResult.containsObjects(logFileKey)) {

            var logFileDef = runtimeResult.getObjectsOrThrow(logFileKey);

            var logStorageSelector = logFileDef.getFile().getStorageId();
            var logStorageId = resultLookup.get(logStorageSelector.getObjectId());
            var logStorageKey = logStorageId != null ? MetadataUtil.objectKey(logStorageId) : null;

            if (logStorageKey != null && runtimeResult.containsObjects(logStorageKey)) {

                var logStorageDef = runtimeResult.getObjectsOrThrow(logStorageKey);

                resultBuilder.addObjectIds(logFileId);
                resultBuilder.putObjects(logFileKey, logFileDef);

                resultBuilder.addObjectIds(logStorageId);
                resultBuilder.putObjects(logStorageKey, logStorageDef);
            }
            else {

                // TODO: Error
                throw new EUnexpected();
            }
        }

//        commonResults.add(applyJobAttrs(jobState, logFileUpdate));
//        commonResults.add(applyJobAttrs(jobState, logStorageUpdate));

        return resultBuilder.build();
    }

    private Map<String, TagHeader> buildResultLookup(RuntimeJobResult runtimeResult) {

        var duplicates = new HashSet<String>();

        var resultLookup = runtimeResult
                .getObjectIdsList().stream()
                .collect(Collectors.toMap(TagHeader::getObjectId, Function.identity(),
                        (id1, id2) -> { duplicates.add(MetadataUtil.objectKey(id2)); return id1; }));

        if (!duplicates.isEmpty()) {

            // TODO: Error
        }

        return resultLookup;
    }

    void saveJobResult(JobState jobState) {

        var jobResult = jobState.executorResult;

        // Lookup for preallocated keys, used to decide which objects are new and which are pre-alloc
        var preallocatedKeys = jobState.jobConfig.getPreallocatedIdsList().stream()
                .map(MetadataUtil::objectKey)
                .collect(Collectors.toSet());

        // Collect metadata updates to send in a single batch
        var preallocated = new ArrayList<MetadataWriteRequest>();
        var newObjects = new ArrayList<MetadataWriteRequest>();
        var newVersions = new ArrayList<MetadataWriteRequest>();
        var newTags = new ArrayList<MetadataWriteRequest>();

        // Update tags on the original job object
        var jobAttrs = jobAttrs(jobResult.getResult());

        var jobWriteReq = MetadataWriteRequest.newBuilder()
                .setObjectType(ObjectType.JOB)
                .setPriorVersion(MetadataUtil.selectorFor(jobState.jobId))
                .addAllTagUpdates(jobAttrs)
                .build();

        newTags.add(jobWriteReq);

        // Create the result object
        var resultObj = ObjectDefinition.newBuilder()
                .setObjectType(ObjectType.RESULT)
                .setResult(jobResult.getResult());

        var resultWriteReq = MetadataWriteRequest.newBuilder()
                .setObjectType(ObjectType.RESULT)
                .setPriorVersion(MetadataUtil.preallocated(jobResult.getResultId()))
                .setDefinition(resultObj)
                .build();

        // RESULT is always preallocated
        preallocated.add(resultWriteReq);

        // Add the individual objects created by the job
        for (var objectId : jobResult.getObjectIdsList()) {

            var objectKey = MetadataUtil.objectKey(objectId);

            if (!jobResult.containsObjects(objectKey)) {
                // TODO: error
                throw new EUnexpected();
            }

            var definition = jobResult.getObjectsOrThrow(objectKey);

            var writeRequest = MetadataWriteRequest.newBuilder()
                    .setObjectType(objectId.getObjectType())
                    .setDefinition(definition);

            if (jobResult.containsAttrs(objectKey)) {
                var attrs = jobResult.getAttrsOrThrow(objectKey);
                writeRequest.addAllTagUpdates(attrs.getAttrsList());
            }

            if (preallocatedKeys.contains(objectKey)) {
                writeRequest.setPriorVersion(MetadataUtil.preallocated(objectId));
                preallocated.add(writeRequest.build());
            }
            else if (objectId.getObjectVersion() > MetadataConstants.OBJECT_FIRST_VERSION) {
                writeRequest.setPriorVersion(MetadataUtil.priorVersion(objectId));
                newVersions.add(writeRequest.build());
            }
            else {
                newObjects.add(writeRequest.build());
            }
        }

        // Send metadata updates as a single batch
        var batchRequest = MetadataWriteBatchRequest.newBuilder()
                .setTenant(jobState.tenant)
                .addAllCreatePreallocatedObjects(preallocated)
                .addAllCreateObjects(newObjects)
                .addAllUpdateObjects(newVersions)
                .addAllUpdateTags(newTags)
                .build();

        var metadataClient = configureClient(metaClient, jobState);
        var batchResponse = metadataClient.writeBatch(batchRequest);

        log.info("RESULT SAVED: {} object(s) created, {} object(s) updated, {} tag(s) updated",
                batchResponse.getCreateObjectsCount() + batchResponse.getCreatePreallocatedObjectsCount(),
                batchResponse.getUpdateObjectsCount(),
                batchResponse.getUpdateTagsCount());
    }

    private List<TagUpdate> jobAttrs(ResultDefinition result) {

        if (result.getStatusCode() == JobStatusCode.SUCCEEDED) {

            return List.of(
                    TagUpdate.newBuilder()
                            .setAttrName(TRAC_JOB_STATUS_ATTR)
                            .setValue(encodeValue(result.getStatusCode().toString()))
                            .build());
        }
        else {

            return List.of(
                    TagUpdate.newBuilder()
                            .setAttrName(TRAC_JOB_STATUS_ATTR)
                            .setValue(encodeValue(result.getStatusCode().toString()))
                            .build(),
                    TagUpdate.newBuilder()
                            .setAttrName(TRAC_JOB_ERROR_MESSAGE_ATTR)
                            .setValue(encodeValue(result.getStatusMessage()))
                            .build());
        }
    }

    private MetadataWriteRequest applyJobAttrs(JobState jobState, MetadataWriteRequest request) {

        if (!request.hasDefinition())
            return request;

        var builder = request.toBuilder();

        builder.addTagUpdates(TagUpdate.newBuilder()
                .setAttrName(TRAC_UPDATE_JOB)
                .setValue(MetadataCodec.encodeValue(jobState.jobKey)));

        if (!request.hasPriorVersion() || request.getPriorVersion().getObjectVersion() == 0) {

            builder.addTagUpdates(TagUpdate.newBuilder()
                    .setAttrName(TRAC_CREATE_JOB)
                    .setValue(MetadataCodec.encodeValue(jobState.jobKey)));
        }

        return builder.build();
    }

    <TStub extends AbstractStub<TStub>>
    TStub configureClient(TStub clientStub, JobState jobState) {

        if (jobState.clientConfig == null)
            jobState.clientConfig = jobState.clientState.restore(commonConcerns);

        return jobState.clientConfig.configureClient(clientStub);
    }
}
