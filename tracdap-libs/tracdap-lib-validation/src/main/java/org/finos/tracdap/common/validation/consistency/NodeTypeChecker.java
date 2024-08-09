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
//import org.finos.tracdap.common.metadata.TypeSystem;
//import org.finos.tracdap.common.validation.graph.Graph;
//import org.finos.tracdap.common.validation.graph.Node;
//import org.finos.tracdap.common.validation.graph.NodeId;
//import org.finos.tracdap.common.validation.graph.SocketId;
//import org.finos.tracdap.metadata.ModelInputSchema;
//import org.finos.tracdap.metadata.ModelParameter;
//import org.finos.tracdap.metadata.ObjectType;
//
//public class NodeTypeChecker {
//
//    private final Graph<NodeTypeInfo> graph;
//
//
//    public void check(Node<NodeTypeInfo> node) {
//
//        switch (node.payload().definition().getObjectType()) {
//
//            case
//        }
//
//    }
//
//    public Boolean checkParameterNode(Node<NodeTypeInfo> node) {
//
//    }
//
//    public Boolean checkInputNode(Node<NodeTypeInfo> node) {
//
//        if (!node.inputs().isEmpty() || node.outputs().size() != 1)
//            throw new EUnexpected();  // todo
//
//        var inputSchema = node.payload().inputSchema();
//        var inputData = node.payload().definition();
//
//        if (inputData == null)
//            return false;  // TODO: report failure
//
//        if (inputSchema == null)
//            return true;  // TODO: Should this be allowed?
//
//
//    }
//
//    public Boolean checkOutputNode(Node<NodeTypeInfo> node) {
//
//        if (node.inputs().size() != 1 || !node.outputs().isEmpty())
//            throw new EUnexpected();
//
//        var source = node.inputs().get(0);
//        var sourceNode = graph.nodes().get(source.nodeId());
//
//
//
//
//    }
//
//    public Boolean checkModelNode(Node<NodeTypeInfo> node) {
//
//        var def = node.payload().definition();
//
//        if (def.getObjectType() != ObjectType.MODEL)
//            throw new EUnexpected();
//
//        var modelDef = def.getModel();
//        var nDependencies = modelDef.getInputsCount() + modelDef.getOutputsCount();
//
//        if (node.inputs().size() != nDependencies)
//            throw new EUnexpected();
//
//        for (var input : node.inputs().entrySet()) {
//
//            if (modelDef.containsInputs(input.getKey())) {
//                var modelInput = modelDef.getInputsOrThrow(input.getKey());
//
//
//            }
//            else if (modelDef.containsParameters(input.getKey())) {
//                var modelParam = modelDef.getParametersOrThrow(input.getKey());
//
//            }
//            else
//                throw new EUnexpected();
//
//        }
//    }
//
//    private boolean checkModelInput(ModelInputSchema modelInput, SocketId socketId) {
//
//        var sourceNode = graph.nodes().get(socketId.nodeId());
//
//        if (!sourceNode.outputs().contains(socketId.socket()))
//            throw new EUnexpected();  // todo
//
//        if (sourceNode.nodeType() == NodeType.INPUT_NODE) {
//
//            var sourceInput = sourceNode.payload().inputSchema();
//            // T
//        }
//
//
//
//
//
//        sourceNode.payload().typing()
//
//        if (socketId.socket() != null) {
//            sourceNode.outputs()
//
//
//        }
//    }
//
//    private boolean checkModelParameter(ModelParameter modelParam, SocketId socketId) {
//
//        var sourceNodeId = new NodeId(socketId.node(), socketId.namespace());
//        var sourceNode = graph.nodes().get(sourceNodeId);
//
//        if (socketId.socket() != null)
//            throw new EUnexpected();
//
//        if (sourceNode.outputs().size() != 1)
//            throw new EUnexpected();
//
//        if (sourceNode.payload().typing() != NodeTypeInfo.Typing.TRAC_VALUE)
//            throw new RuntimeException(""); // todo
//
//        var value = sourceNode.payload().value();
//
//        MetadataCodec.
//
//
//    }
//}
