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
//package org.finos.tracdap.common.validation.consistency;
//
//import org.finos.tracdap.common.exception.EUnexpected;
//import org.finos.tracdap.common.validation.graph.Node;
//import org.finos.tracdap.common.metadata.MetadataBundle;
//import org.finos.tracdap.metadata.*;
//
//
//public class NodeTypeLookup {
//
//    private final JobDefinition job;
//    private final MetadataBundle resources;
//
//    private final SelectorLookup selectorLookup;
//
//
//    public NodeTypeLookup(JobDefinition job, MetadataBundle resources) {
//        this.job = job;
//        this.resources = resources;
//        this.selectorLookup = selectorLookupForJob(job);
//    }
//
//    public NodeTypeInfo lookup(Node<FlowNode> node) {
//
//        var flowNode = node.payload();
//
//        if (flowNode == null)
//            throw new EUnexpected();  // TODO
//
//        switch (flowNode.getNodeType()) {
//
//            case PARAMETER_NODE: return lookupParameterNode(node);
//            case INPUT_NODE: return lookupInputNode(node);
//            case OUTPUT_NODE: return lookupOutputNode(node);
//            case MODEL_NODE: return lookupModelNode(node);
//            default:
//                throw new EUnexpected();
//        }
//
//        // Either of these can be null
//        var selector = selectorForFlowNode(node.nodeId().name(), node.payload());
//        var resource = selector != null ? resources.getResource(selector) : null;
//
//        if (resource != null)
//            return new NodeTypeInfo(resource);
//
//        throw new EUnexpected();  // TODO
//    }
//
//    public NodeTypeInfo lookupParameterNode(Node<FlowNode> node) {
//        var paramName = node.nodeId().name();
//        var modelParam = job.getRunModel().getModel();
//
//    }
//
//    public NodeTypeInfo lookupInputNode(Node<FlowNode> node) {
//
//    }
//
//    public NodeTypeInfo lookupOutputNode(Node<FlowNode> node) {
//
//    }
//
//    public NodeTypeInfo lookupModelNode(Node<FlowNode> node) {
//
//    }
//
//
//    private TagSelector selectorForFlowNode(String nodeName, FlowNode flowNode) {
//
//        if (flowNode == null)
//            return null;
//
//        switch (flowNode.getNodeType()) {
//            case MODEL_NODE: return selectorLookup.lookupModel(nodeName);
//            case INPUT_NODE: return selectorLookup.lookupInput(nodeName);  // TODO: Namespaces?
//            case OUTPUT_NODE: return selectorLookup.lookupPriorOutput(nodeName);  // TODO: Namespaces?
//            default:
//                return null;
//        }
//    }
//
//    private SelectorLookup selectorLookupForJob(JobDefinition job) {
//
//        switch (job.getJobType()) {
//            case RUN_MODEL: return new RunModelLookup(job.getRunModel());
//            case RUN_FLOW: return new RunFlowLookup(job.getRunFlow());
//            default:
//                throw new EUnexpected();
//        }
//
//    }
//
//    private interface SelectorLookup {
//
//        TagSelector lookupModel(String modelName);
//        TagSelector lookupInput(String inputName);
//        TagSelector lookupPriorOutput(String outputName);
//    }
//
//    private static class RunModelLookup implements SelectorLookup {
//
//        private final RunModelJob job;
//
//        RunModelLookup(RunModelJob job) {
//            this.job = job;
//        }
//
//        @Override
//        public TagSelector lookupModel(String modelName) {
//            // TODO: check node name as expected for run_model
//            return job.getModel();
//        }
//
//        @Override
//        public TagSelector lookupInput(String inputName) {
//            return job.getInputsOrThrow(inputName);
//        }
//
//        @Override
//        public TagSelector lookupPriorOutput(String outputName) {
//            return job.getPriorOutputsOrDefault(outputName, null);
//        }
//    }
//
//    private static class RunFlowLookup implements SelectorLookup {
//
//        private final RunFlowJob job;
//
//        public RunFlowLookup(RunFlowJob job) {
//            this.job = job;
//        }
//
//        @Override
//        public TagSelector lookupModel(String modelName) {
//            return job.getModelsOrThrow(modelName);
//        }
//
//        @Override
//        public TagSelector lookupInput(String inputName) {
//            return job.getModelsOrThrow(inputName);
//        }
//
//        @Override
//        public TagSelector lookupPriorOutput(String outputName) {
//            return job.getPriorOutputsOrDefault(outputName, null);
//        }
//    }
//
//}
