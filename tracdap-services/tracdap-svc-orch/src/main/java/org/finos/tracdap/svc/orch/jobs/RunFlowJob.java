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

import org.finos.tracdap.common.exception.EConsistencyValidation;
import org.finos.tracdap.common.exception.EUnexpected;
import org.finos.tracdap.common.graph.GraphBuilder;
import org.finos.tracdap.common.graph.NodeNamespace;
import org.finos.tracdap.common.metadata.MetadataBundle;
import org.finos.tracdap.common.metadata.MetadataUtil;
import org.finos.tracdap.config.JobConfig;
import org.finos.tracdap.config.JobResult;
import org.finos.tracdap.config.TenantConfig;
import org.finos.tracdap.metadata.*;

import java.util.*;
import java.util.stream.Collectors;


public class RunFlowJob extends RunModelOrFlow implements IJobLogic {

    private static final String UNMAPPED_RESOURCE = "";

    @Override
    public List<TagSelector> requiredMetadata(JobDefinition job) {

        if (job.getJobType() != JobType.RUN_FLOW)
            throw new EUnexpected();

        var runFlow = job.getRunFlow();

        var resources = new ArrayList<TagSelector>(runFlow.getInputsCount() + runFlow.getModelsCount() + 1);
        resources.add(runFlow.getFlow());
        resources.addAll(runFlow.getInputsMap().values());
        resources.addAll(runFlow.getModelsMap().values());
        resources.addAll(runFlow.getPriorOutputsMap().values());

        return resources;
    }

    @Override
    public List<String> requiredResources(JobDefinition job, MetadataBundle metadata, TenantConfig tenantConfig) {

        var resources = new HashSet<String>();

        // Storage requirements are the same for model / flow jobs
        addRequiredStorage(metadata, tenantConfig, resources);

        // Add resource requirements for all referenced models
        for (var obj : metadata.getObjects().values()) {
            if (obj.getObjectType() == ObjectType.MODEL) {

                var model = obj.getModel();

                // Model repo resource is always required
                resources.add(model.getRepository());

                // Add job resources explicitly defined by models
                for (var modelResource : model.getResourcesMap().keySet()) {
                    var mappedResource = job.getRunFlow().getResourcesOrDefault(modelResource, UNMAPPED_RESOURCE);
                    if (UNMAPPED_RESOURCE.equals(mappedResource)) {
                        // This should already be flagged in job consistency validation
                        throw new EConsistencyValidation(String.format("Resource [%s] not supplied in the job", mappedResource));
                    }
                    resources.add(mappedResource);
                }
            }
        }

        return new ArrayList<>(resources);
    }

    @Override
    public JobDefinition applyJobTransform(JobDefinition job, MetadataBundle metadata, TenantConfig tenantConfig) {

        var repeatability = determineRepeatability(job, metadata);

        return job.toBuilder()
                .setRepeatability(repeatability)
                .build();
    }

    private JobRepeatability determineRepeatability(JobDefinition job, MetadataBundle metadata) {

        var flowJob =  job.getRunFlow();

        var repeatableModels = new ArrayList<String>(flowJob.getModelsCount());

        for (var modelEntry : flowJob.getModelsMap().entrySet()) {

            var modelKey = modelEntry.getKey();
            var modelObj = metadata.getObjects().get(modelKey);
            var model = modelObj.getModel();

            if (model.getResourcesCount() == 0)
                repeatableModels.add(modelKey);
        }

        if (repeatableModels.size() == flowJob.getModelsCount())
            return JobRepeatability.FULLY_REPEATABLE;

        if (repeatableModels.isEmpty())
            return JobRepeatability.NOT_REPEATABLE;

        var flow = metadata.getObject(flowJob.getFlow()).getFlow();

        // Look for repeatable models where all the inputs come from job inputs
        // If all the repeatable models have a non-repeatable parent, the job is not repeatable
        for (var edge : flow.getEdgesList()) {

            if (!repeatableModels.contains(edge.getTarget().getNode()))
                continue;

            if (!flowJob.containsInputs(edge.getSource().getNode()))
                repeatableModels.remove(edge.getTarget().getNode());
        }

        if (repeatableModels.isEmpty())
            return JobRepeatability.NOT_REPEATABLE;
        else
            return JobRepeatability.PARTIALLY_REPEATABLE;
    }

    @Override
    public MetadataBundle applyMetadataTransform(JobDefinition job, MetadataBundle metadata, TenantConfig tenantConfig) {

        // Running the graph builder will apply any required auto-wiring and type inference to the flow
        // This creates a strictly consistent flow that can be sent to the runtime

        var flowSelector = job.getRunFlow().getFlow();

        var jobNamespace = NodeNamespace.ROOT;
        var builder = new GraphBuilder(jobNamespace, metadata);
        var graph = builder.buildJob(job);

        var strictFlow = builder.exportFlow(graph);

        var strictFlowObj = ObjectDefinition.newBuilder()
                .setObjectType(ObjectType.FLOW)
                .setFlow(strictFlow)
                .build();

        return metadata.withUpdate(flowSelector, strictFlowObj);
    }

    @Override
    public Map<ObjectType, Integer> expectedOutputs(JobDefinition job, MetadataBundle metadata) {

        var runFlowJob = job.getRunFlow();

        var flowObj = metadata.getObject(runFlowJob.getFlow());
        var flow = flowObj.getFlow();

        return expectedOutputs(flow.getOutputsMap(), runFlowJob.getPriorOutputsMap());
    }

    @Override
    public JobResult processResult(JobConfig jobConfig, JobResult jobResult, Map<String, TagHeader> resultIds) {

        var runFlow = jobConfig.getJob().getRunFlow();

        var flowKey = MetadataUtil.objectKey(runFlow.getFlow());
        var flowId = jobConfig.getObjectMappingMap().get(flowKey);
        var flowDef = jobConfig.getObjectsMap().get(MetadataUtil.objectKey(flowId)).getFlow();

        var perNodeAttrs = flowDef.getNodesMap().entrySet().stream()
                .filter(entry -> entry.getValue().getNodeType() == FlowNodeType.OUTPUT_NODE)
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getNodeAttrsList()));

        return processResult(
                jobResult,
                flowDef.getOutputsMap(),
                runFlow.getOutputAttrsList(),
                perNodeAttrs, resultIds);
    }
}
