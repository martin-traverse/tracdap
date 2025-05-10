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
import org.finos.tracdap.common.exception.EConsistencyValidation;
import org.finos.tracdap.common.exception.EUnexpected;
import org.finos.tracdap.metadata.*;
import org.finos.tracdap.common.metadata.MetadataCodec;
import org.finos.tracdap.common.metadata.MetadataConstants;
import org.finos.tracdap.common.metadata.MetadataUtil;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


public abstract class RunModelOrFlow {


    protected Map<ObjectType, Integer> preallocateIds(
            Map<String, ModelOutputSchema> outputs,
            Map<String, TagSelector> priorOutputs) {

        var requiredIds = new HashMap<ObjectType, Integer>();

        for (var output : outputs.entrySet()) {

            if (priorOutputs.containsKey(output.getKey()))
                continue;

            var outputType = output.getValue().getObjectType();

            if (outputType == ObjectType.DATA) {
                requiredIds.compute(ObjectType.DATA, (key, value) -> value == null ? 1 : value + 1);
                requiredIds.compute(ObjectType.STORAGE, (key, value) -> value == null ? 1 : value + 1);
            }
            else if (outputType == ObjectType.FILE) {
                requiredIds.compute(ObjectType.FILE, (key, value) -> value == null ? 1 : value + 1);
                requiredIds.compute(ObjectType.STORAGE, (key, value) -> value == null ? 1 : value + 1);
            }
            else {
                throw new EUnexpected();  // TODO
            }
        }

        return requiredIds;
    }

    public Map<String, MetadataWriteRequest> newResultIds(
            String tenant, Map<String, ModelOutputSchema> outputRequirements,
            Map<String, TagSelector> priorOutputsMap) {

        var resultMapping = new HashMap<String, MetadataWriteRequest>();

        for (var output : outputRequirements.entrySet()) {

            if (priorOutputsMap.containsKey(output.getKey()))
                continue;

            var outputKey = output.getKey();
            var storageKey = String.format("%s:%s", outputKey, ObjectType.STORAGE);

            var outputReq = MetadataWriteRequest.newBuilder()
                    .setTenant(tenant)
                    .setObjectType(output.getValue().getObjectType())
                    .build();

            var storageReq = MetadataWriteRequest.newBuilder()
                    .setTenant(tenant)
                    .setObjectType(ObjectType.STORAGE)
                    .build();

            resultMapping.put(outputKey, outputReq);
            resultMapping.put(storageKey, storageReq);
        }

        return resultMapping;
    }

    public Map<String, TagHeader> priorResultIds(
            Set<String> outputKeys, Map<String, TagSelector> priorOutputsMap,
            Map<String, ObjectDefinition> resources, Map<String, TagHeader> resourceMapping) {

        var resultMapping = new HashMap<String, TagHeader>();

        for (var outputKey : outputKeys) {

            var priorOutput = priorOutputsMap.getOrDefault(outputKey, null);

            if (priorOutput == null)
                continue;

            var priorOutputKey = MetadataUtil.objectKey(priorOutput);
            var priorOutputId = resourceMapping.get(priorOutputKey);
            var priorOutputDef = resources.get(MetadataUtil.objectKey(priorOutputId));

            TagSelector priorStorageSelector;

            if (priorOutputDef.getObjectType() == ObjectType.DATA)
                priorStorageSelector = priorOutputDef.getData().getStorageId();
            else if (priorOutputDef.getObjectType() == ObjectType.FILE)
                priorStorageSelector = priorOutputDef.getFile().getStorageId();
            else
                throw new EUnexpected();

            var priorStorageKey = MetadataUtil.objectKey(priorStorageSelector);
            var priorStorageId = resourceMapping.get(priorStorageKey);

            var storageKey = String.format("%s:%s", outputKey, ObjectType.STORAGE);

            resultMapping.put(outputKey, priorOutputId);
            resultMapping.put(storageKey, priorStorageId);
        }

        return resultMapping;
    }

    public Map<String, TagSelector> setResultIds(
            Set<String> outputKeys,
            Map<String, TagHeader> resultMapping) {

        var outputSelectors = new HashMap<String, TagSelector>();

        for (var outputKey : outputKeys) {

            var outputId = resultMapping.get(outputKey);
            var outputSelector = MetadataUtil.selectorFor(outputId);
            outputSelectors.put(outputKey, outputSelector);
        }

        return outputSelectors;
    }


    protected RuntimeJobResult processResult(
            RuntimeJobResult jobResult, Map<String, ModelOutputSchema> expectedOutputs,
            List<TagUpdate> jobAttrs, Map<String, List<TagUpdate>> perNodeAttrs) {

        var resultBuilder = RuntimeJobResult.newBuilder();

        var objectLookup = jobResult.getObjectIdsList().stream()
                .collect(Collectors.toMap(TagHeader::getObjectId, Function.identity(), (id1, id2) -> id1));

        if (objectLookup.size() != jobResult.getObjectIdsCount())
            // TODO Check message
            throw new EConsistencyValidation("Result contains multiple entries for the same object");

        for (var output: expectedOutputs.entrySet()) {

            var outputName = output.getKey();
            var modelOutput = output.getValue();

            // Ignore optional outputs that were not produced
            if (modelOutput.getOptional() & !jobResult.getResult().containsOutputs(outputName))
                continue;

            processOutput(
                    outputName, jobResult, objectLookup,
                    jobAttrs, perNodeAttrs,
                    resultBuilder);
        }

        return resultBuilder.build();
    }

    private void processOutput(
            String outputName, RuntimeJobResult jobResult, Map<String, TagHeader> objectLookup,
            List<TagUpdate> jobAttrs, Map<String, List<TagUpdate>> perNodeAttrs,
            RuntimeJobResult.Builder resultBuilder) {

        var result = jobResult.getResult();

        if (!result.containsOutputs(outputName))
            throw new EConsistencyValidation(String.format("Missing required output [%s]", outputName));

        var outputSelector = result.getOutputsOrThrow(outputName);
        var outputId = objectLookup.get(outputName);
        var outputKey = MetadataUtil.objectKey(outputSelector);

        if (!jobResult.containsObjects(outputKey)) {
            throw new EConsistencyValidation(String.format("Missing definition for required output [%s]", outputName));
        }

        var outputDef = jobResult.getObjectsOrThrow(outputKey);
        var outputAttrs = jobResult.getAttrsOrDefault(outputKey, RuntimeJobResultAttrs.getDefaultInstance()).getAttrsList();

        var storageSelector = getStorageKey(outputDef);
        var storageId = objectLookup.get(storageSelector.getObjectId());
        var storageKey = MetadataUtil.objectKey(storageId);

        if (!jobResult.containsObjects(storageKey)) {
            throw new EConsistencyValidation(String.format("Missing storage definition for required output [%s]", outputName));
        }

        var storageDef = jobResult.getObjectsOrThrow(storageKey);
        var storageAttrs = jobResult.getAttrsOrDefault(outputKey, RuntimeJobResultAttrs.getDefaultInstance()).getAttrsList();


        outputAttrs.addAll(jobAttrs);

        if (perNodeAttrs.containsKey(outputKey))
            outputAttrs.addAll(perNodeAttrs.get(outputKey));

        // Controlled attrs

        outputAttrs.add(TagUpdate.newBuilder()
                .setAttrName("trac_job_output")
                .setValue(MetadataCodec.encodeValue(outputName))
                .build());

        storageAttrs.add(TagUpdate.newBuilder()
                .setAttrName(MetadataConstants.TRAC_STORAGE_OBJECT_ATTR)
                .setValue(MetadataCodec.encodeValue(outputKey))
                .build());

        resultBuilder.addObjectIds(outputId);
        resultBuilder.putObjects(outputKey, outputDef);
        resultBuilder.putAttrs(outputKey, RuntimeJobResultAttrs.newBuilder().addAllAttrs(outputAttrs).build());

        resultBuilder.addObjectIds(storageId);
        resultBuilder.putObjects(storageKey, storageDef);
        resultBuilder.putAttrs(outputKey, RuntimeJobResultAttrs.newBuilder().addAllAttrs(storageAttrs).build());
    }

    private TagSelector getStorageKey(ObjectDefinition outputDef) {

        if (outputDef.getObjectType() == ObjectType.DATA)
            return outputDef.getData().getStorageId();

        if (outputDef.getObjectType() == ObjectType.FILE)
            return outputDef.getFile().getStorageId();

        throw new EUnexpected();  // TODO
    }

    /*

    public List<MetadataWriteRequest> buildResultMetadata(
            String tenant, JobConfig jobConfig, RuntimeJobResult jobResult,
            Map<String, ModelOutputSchema> expectedOutputs,
            Map<String, TagSelector> outputs, Map<String, TagSelector> priorOutputs,
            List<TagUpdate> outputAttrs, Map<String, List<TagUpdate>> perNodeOutputAttrs) {

        var result = jobResult.getResult();
        var updates = new ArrayList<MetadataWriteRequest>();

        for (var output: expectedOutputs.entrySet()) {

            var outputName = output.getKey();
            var outputDef = output.getValue();

            if (!result.containsOutputs(outputName)) {
                if (outputDef.getOptional())
                    continue;
                else
                    // TODO Missing output
                    throw new EConsistencyValidation(String.format("Missing required output [%s]", outputName));
            }

            var outputSelector = result.getOutputsOrThrow(outputName);
            var outputKey = MetadataUtil.objectKey(outputSelector);

            if (!jobResult.containsObjects(outputKey)) {
                // TODO: Error
                throw new EConsistencyValidation(String.format("Missing required output [%s]", outputName));
            }

            var definition = jobResult.getObjectsOrThrow(outputKey);



            // TODO: Preallocated IDs for storage outputs

            var storageMapping = outputName + ":STORAGE";
            var storageId = jobConfig.getResultMappingOrThrow(storageMapping);
            var storageKey = MetadataUtil.objectKey(storageId);

            var outputObj = jobResult.getResultsOrThrow(outputKey);
            var storageObj = jobResult.getResultsOrThrow(storageKey);

            var priorOutputSelector = priorOutputs.containsKey(outputName)
                    ? priorOutputs.get(outputName)
                    : MetadataUtil.preallocated(outputs.get(outputName));

            var priorStorageSelector = priorOutputs.containsKey(outputName)
                    ? priorStorageSelector(priorOutputSelector, jobConfig)
                    : preallocatedStorageSelector(outputObj);

            var controlledAttrs = List.of(
                    TagUpdate.newBuilder()
                    .setAttrName("trac_job_output")
                    .setValue(MetadataCodec.encodeValue(outputName))
                    .build());

            var nodeOutputAttrs = perNodeOutputAttrs.get(outputName);
            if (nodeOutputAttrs == null) {
                nodeOutputAttrs = List.of();
            }

            var outputUpdate = MetadataWriteRequest.newBuilder()
                    .setTenant(tenant)
                    .setObjectType(outputObj.getObjectType())
                    .setPriorVersion(priorOutputSelector)
                    .setDefinition(outputObj)
                    .addAllTagUpdates(controlledAttrs)
                    .addAllTagUpdates(outputAttrs)
                    .addAllTagUpdates(nodeOutputAttrs)
                    .build();

            updates.add(outputUpdate);

            var storageAttrs = List.of(
                    TagUpdate.newBuilder()
                    .setAttrName(MetadataConstants.TRAC_STORAGE_OBJECT_ATTR)
                    .setValue(MetadataCodec.encodeValue(outputKey))
                    .build());

            var storageUpdate = MetadataWriteRequest.newBuilder()
                    .setTenant(tenant)
                    .setObjectType(storageObj.getObjectType())
                    .setPriorVersion(priorStorageSelector)
                    .setDefinition(storageObj)
                    .addAllTagUpdates(storageAttrs)
                    .build();

            updates.add(storageUpdate);
        }

        return updates;
    }

    private TagSelector priorStorageSelector(TagSelector priorOutputSelector, JobConfig jobConfig) {

        var mappedOutputKey = MetadataUtil.objectKey(priorOutputSelector);
        String outputKey;

        if (jobConfig.containsObjectMapping(mappedOutputKey)) {
            var outputId = jobConfig.getObjectMappingOrDefault(mappedOutputKey, null);
            var outputSelector = MetadataUtil.selectorFor(outputId);
            outputKey = MetadataUtil.objectKey(outputSelector);
        }
        else
            outputKey = mappedOutputKey;

        var outputObj = jobConfig.getObjectsOrThrow(outputKey);

        TagSelector storageSelector;

        if (outputObj.getObjectType() == ObjectType.DATA)
            storageSelector = outputObj.getData().getStorageId();
        else if (outputObj.getObjectType() == ObjectType.FILE)
            storageSelector = outputObj.getFile().getStorageId();
        else
            throw new EUnexpected();

        var storageKey = MetadataUtil.objectKey(storageSelector);

        if (jobConfig.containsObjectMapping(storageKey)) {

            var storageId = jobConfig.getObjectMappingOrDefault(storageKey, null);
            return MetadataUtil.selectorFor(storageId);
        }
        else
            return storageSelector;
    }

    private TagSelector preallocatedStorageSelector(ObjectDefinition outputObj) {

        if (outputObj.getObjectType() == ObjectType.DATA)
            return MetadataUtil.preallocated(outputObj.getData().getStorageId());

        else if (outputObj.getObjectType() == ObjectType.FILE)
            return  MetadataUtil.preallocated(outputObj.getFile().getStorageId());

        else
            throw new EUnexpected();
    }
     */
}
