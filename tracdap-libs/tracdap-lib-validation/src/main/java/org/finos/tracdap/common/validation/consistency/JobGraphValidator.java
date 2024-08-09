///*
// * Copyright 2024 Accenture Global Solutions Limited
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package org.finos.tracdap.common.validation.jobs;
//
//import org.finos.tracdap.api.ErrorItem;
//import org.finos.tracdap.common.exception.EUnexpected;
//import org.finos.tracdap.common.metadata.MetadataUtil;
//import org.finos.tracdap.common.validation.core.ValidationContext;
//import org.finos.tracdap.metadata.*;
//
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Map;
//
//public class JobGraphValidator {
//
//
//
//
//    public void validateJobGraph(RunFlowJob job, FlowDefinition flow, ValidationContext ctx) {
//
//
//
//        for (var node : flow.getNodesMap().entrySet()) {
//
//            var nodeName = node.getKey();
//            var nodeType = node.getValue().getNodeType();
//
//            switch (nodeType) {
//
//                case INPUT_NODE:
////a
//                    if (!job.containsInputs(nodeName)) {
//
//                        var error = ErrorItem.newBuilder()
//                                .setLocation(ctx.key().displayName())
//                                .setClassification(ctx.fieldName())
//                                .setItem(nodeName)
//                                .build();
//
//                        ctx.error()
//                    }
//
//                    var jobInput = job.getInputsOrDefault(nodeName, null);
//
//                    if (jobInput == null) {}
//
//
//            }
//
//
//        }
//
//
//
//        flow.getNodesMap();
//
//        job.getInputsMap()
//
//
//        flow.getInputsMap()
//    }
//
//
//
//
//    public void validateDataSchemas(RunFlowJob job, FlowDefinition flow, Map<String, ObjectDefinition> resources) {
//
//        var remainingNodes = new HashSet<>(flow.getNodesMap().keySet());
//        var reachableNodes = new HashSet<String>();
//å
//        var edgesByTarget = new HashMap<String, List<FlowEdge>>();
//        var noEdges = List.<FlowEdge>of();
//
//
//        for (var priorOutput : job.getPriorOutputsMap().entrySet()) {
//
//            var nodeName = priorOutput.getKey();
//
//        }
//
//        for (var output : job.getOutputsMap().entrySet()) {
//
//            var nodeName = output.getKey();
//
//        }
//
//        for (var model : job.getModelsMap().entrySet()) {
//
//            var nodeName = model.getKey();
//            var edges = edgesByTarget.getOrDefault(nodeName, noEdges);
//
//            var modelKey = MetadataUtil.objectKey(model.getValue());
//            var modelDef = resources.get(modelKey).getModel();
//
//            for (var input : modelDef.getInputsMap().entrySet()) {
//
//                var targetSchema = input.getValue().getSchema();
//
//                var socket = input.getKey();
//                var edge = edges.stream().filter(e -> socket.equals(e.getTarget().getSocket())).findFirst();
//
//                if (edge.isEmpty() || !flow.containsNodes(edge.get().getSource().getNode()))
//                    throw new EUnexpected();
//
//                var sourceNodeName = edge.get().getSource().getNode();
//                var sourceNode = flow.getNodesOrThrow(sourceNodeName);
//
//                if (sourceNode.getNodeType() == FlowNodeType.INPUT_NODE) {
//
//                    if (!job.containsInputs(sourceNodeName))
//                        throw new EUnexpected();
//
//                    var sourceSelector = job.getInputsOrThrow(sourceNodeName);
//                    var sourceKey = MetadataUtil.objectKey(sourceSelector);
//                    var sourceDef = resources.get(sourceKey).getData();
//                    var sourceSchema = sourceDef.getSchema();  // TODO: External schemas
//
//                    validateSchemaCompatibility(sourceSchema, targetSchema);
//                }
//                else if (sourceNode.getNodeType() == FlowNodeType.MODEL_NODE) {
//
//
//                }
//                else
//                    throw new EUnexpected();
//
//
//            }
//
//
//
//        }
//
//    }
//
//    private void validateSchemaCompatibility(SchemaDefinition sourceSchema, SchemaDefinition targetSchema) {
//
//        for (var field : targetSchema.getTable().getFieldsList()) {
//
//
//
//
//        }
//
//
//
//    }
//
//
//}
