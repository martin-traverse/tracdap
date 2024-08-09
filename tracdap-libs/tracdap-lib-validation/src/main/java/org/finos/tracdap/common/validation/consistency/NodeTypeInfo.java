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
//import org.finos.tracdap.metadata.*;
//
//
//public class NodeTypeInfo {
//
//    public enum Typing {
//        TRAC_VALUE,
//        TRAC_OBJECT
//    }
//
//    private final Typing typing;
//    private final Value value;
//    private final ObjectDefinition definition;
//
//    private final ModelParameter parameter;
//    private final ModelInputSchema inputSchema;
//    private final ModelOutputSchema outputSchema;
//
//    public NodeTypeInfo(Value value) {
//        this.typing = Typing.TRAC_VALUE;
//        this.value = value;
//        this.definition = null;
//    }
//
//    public NodeTypeInfo(ObjectDefinition definition) {
//        this.typing = Typing.TRAC_OBJECT;
//        this.value = null;
//        this.definition = definition;
//    }
//
//    public Typing typing() {
//        return typing;
//    }
//
//    public Value value() {
//        return value;
//    }
//
//    public ObjectDefinition definition() {
//        return definition;
//    }
//
//    public ModelParameter parameter() {
//        return parameter;
//    }
//
//    public ModelInputSchema inputSchema() {
//        return inputSchema;
//    }
//
//    public ModelOutputSchema outputSchema() {
//        return outputSchema;
//    }
//}
