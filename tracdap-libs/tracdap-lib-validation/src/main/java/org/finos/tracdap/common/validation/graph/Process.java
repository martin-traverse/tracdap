/*
 * Copyright 2024 Accenture Global Solutions Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.finos.tracdap.common.validation.graph;

import org.finos.tracdap.common.exception.EUnexpected;

import java.util.HashMap;
import java.util.function.Function;

public class Process {

    public interface Walker<TPayload> {

        <TResult> TResult walk(Graph<TPayload> graph, TResult identity, Processor<TPayload, TResult> processor);

    }

    public interface Processor<TPayload, TResult> {

        TResult apply(Graph<TPayload> graph, TResult accumulator, Node<TPayload> node);
    }

    public interface Transformer<T1, T2> extends Processor<T1, Graph<T2>> {

    }

    public static class OneToOneTransformer<T1, T2> implements Transformer<T1, T2> {

        private final Function<Node<T1>, T2> transform;

        public OneToOneTransformer(Function<Node<T1>, T2> transform) {
            this.transform = transform;
        }

        @Override
        public Graph<T2> apply(Graph<T1> graph, Graph<T2> accumulator, Node<T1> node) {
            var payload = transform.apply(node);
            var node_ = new Node<>(node.nodeId(), node.inputs(), node.outputs(), payload);
            accumulator.nodes().put(node.nodeId(), node_);
            return accumulator;
        }
    }

    public static <T1, T2> Graph<T2> translate(Function<Node<T1>, T2> transformFunc, Graph<T1> graph) {

        var transformer = new OneToOneTransformer<>(transformFunc);
        var walker = new RandomVisitor<T1>();
        var result = new Graph<T2>(new HashMap<>(), graph.root());

        return walker.walk(graph, result, transformer);
    }


    public static class RandomVisitor<TPayload> implements Walker<TPayload> {

        @Override
        public <TResult> TResult walk(Graph<TPayload> graph, TResult identity, Processor<TPayload, TResult> processor) {

//            return graph.nodes().values().stream().reduce(identity,
//                    (acc, n) -> processor.apply(graph, acc, n),
//                    (g1, g2) -> { throw new EUnexpected(); });

            var accumulator = identity;

            for(var node : graph.nodes().values()) {
                accumulator = processor.apply(graph, accumulator, node);
            }

            return accumulator;
        }
    }

    public static class DepthFirstWalker<TPayload> implements Walker<TPayload> {

        @Override
        public <TResult> TResult walk(Graph<TPayload> graph, TResult identity, Processor<TPayload, TResult> processor) {
            return null;
        }
    }
}
