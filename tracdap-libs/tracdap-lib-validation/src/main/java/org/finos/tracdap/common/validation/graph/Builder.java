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
//package org.finos.tracdap.common.validation.graph;
//
//import org.finos.tracdap.common.exception.EUnexpected;
//import org.finos.tracdap.common.metadata.MetadataBundle;
//import org.finos.tracdap.metadata.*;
//
//import java.util.*;
//import java.util.function.Function;
//import java.util.stream.Collectors;
//
//public class Builder {
//
//    private static final Map<String, SocketId> NO_DEPENDENCIES = Map.of();
//    private static final List<String> NO_OUTPUTS = List.of();
//    private static final List<String> SINGLE_OUTPUT = List.of("");
//    private static final String SINGLE_INPUT = "";
//
//
//
//    private final Namespace namespace;
//
//    public Builder(Namespace namespace) {
//        this.namespace = namespace;
//    }
//
//    public Graph<FlowNode> buildGraph(JobDefinition job, MetadataBundle resources) {
//
//        switch (job.getJobType()) {
//            case RUN_MODEL: return buildRunModelGraph(job.getRunModel(), resources);
//            case RUN_FLOW: return buildRunFlowJob(job.getRunFlow(), resources);
//            case IMPORT_MODEL: return buildImportModelGraph(job, resources);
//            default:
//                throw new EUnexpected();
//        }
//    }
//
//    public Graph<FlowNode> buildImportModelGraph(JobDefinition job, MetadataBundle resources) {
//
//        var nodes = new HashMap<NodeId, Node<FlowNode>>();
//
//        var importId = new NodeId("trac_import_model", namespace);
//        var importNode = new Node<FlowNode>(importId, NO_DEPENDENCIES, NO_OUTPUTS, null);
//        nodes.put(importId, importNode);
//
//        var rootId = new NodeId("trac_root", namespace);
//        var rootNode = new Node<FlowNode>(rootId, NO_DEPENDENCIES, NO_OUTPUTS, null);
//        nodes.put(rootId, rootNode);
//
//        return new Graph<>(nodes, rootId);
//    }
//
//    public Graph<FlowNode> buildRunModelGraph(RunModelJob job, MetadataBundle resources) {
//
//        var modelObj = resources.getResource(job.getModel());
//
//        if (modelObj == null)
//            throw new EUnexpected();  // todo
//
//        if (modelObj.getObjectType() != ObjectType.MODEL)
//            throw new EUnexpected();
//
//        var nodes = new HashMap<NodeId, Node<FlowNode>>();
//
//        var modelDef = modelObj.getModel();
//        var modelDeps = new HashMap<String, SocketId>();
//
//        for (var param : modelDef.getParametersMap().entrySet()) {
//
//            var flowNode = FlowNode.newBuilder().setNodeType(FlowNodeType.PARAMETER_NODE).build();
//            var nodeId = new NodeId(param.getKey(), namespace);
//            var node = new Node<>(nodeId, NO_DEPENDENCIES, SINGLE_OUTPUT, flowNode);
//            nodes.put(nodeId, node);
//
//            var dependency = new SocketId(param.getKey(), SINGLE_INPUT, namespace);
//            modelDeps.put(param.getKey(), dependency);
//        }
//
//        for (var input : modelDef.getInputsMap().entrySet()) {
//
//            var flowNode = FlowNode.newBuilder().setNodeType(FlowNodeType.INPUT_NODE).build();
//            var nodeId = new NodeId(input.getKey(), namespace);
//            var node = new Node<>(nodeId, NO_DEPENDENCIES, SINGLE_OUTPUT, flowNode);
//            nodes.put(nodeId, node);
//
//            var dependency = new SocketId(input.getKey(), SINGLE_INPUT, namespace);
//            modelDeps.put(input.getKey(), dependency);
//        }
//
//        var modelFlowNode = FlowNode.newBuilder()
//                .setNodeType(FlowNodeType.MODEL_NODE)
//                .addAllParameters(modelDef.getParametersMap().keySet())
//                .addAllInputs(modelDef.getInputsMap().keySet())
//                .addAllOutputs(modelDef.getOutputsMap().keySet())
//                .build();
//
//        var modelId = new NodeId("trac_model", namespace);
//        var modelOutputs = List.copyOf(modelDef.getOutputsMap().keySet());
//        var modelNode = new Node<>(modelId, modelDeps, modelOutputs, modelFlowNode);
//
//        nodes.put(modelId, modelNode);
//
//        for (var output : modelDef.getOutputsMap().entrySet()) {
//
//            var dependency = new SocketId(modelId.name(), output.getKey(), namespace);
//            var dependencies = Map.of(SINGLE_INPUT, dependency);
//
//            var flowNode = FlowNode.newBuilder().setNodeType(FlowNodeType.OUTPUT_NODE).build();
//            var nodeId = new NodeId(output.getKey(), namespace);
//            var node = new Node<>(nodeId, dependencies, NO_OUTPUTS, flowNode);
//            nodes.put(nodeId, node);
//        }
//
//        var rootDeps = modelDef.getParametersMap().keySet().stream()
//                .map(key -> new SocketId(key, SINGLE_INPUT, namespace))
//                .collect(Collectors.toMap(SocketId::node, Function.identity()));
//
//        var rootId = new NodeId("trac_root", namespace);
//        var rootFlowNode = FlowNode.newBuilder().setNodeType(FlowNodeType.NODE_TYPE_NOT_SET).build();
//        var rootNode = new Node<>(rootId, rootDeps, NO_OUTPUTS, rootFlowNode);
//
//        nodes.put(rootId, rootNode);
//
//        return new Graph<>(Collections.unmodifiableMap(nodes), rootId);
//    }
//
//    public Graph<FlowNode> buildRunFlowJob(RunFlowJob job, MetadataBundle resources) {
//
//        throw new RuntimeException("Not implemented");
//    }
//}
