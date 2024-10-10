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

package org.finos.tracdap.common.grpc;

import io.grpc.*;

import java.util.function.Consumer;


public class ValidationServerInterceptor implements ServerInterceptor {

    @FunctionalInterface
    public interface ValidatorFactory {

        <T, S> Consumer<T> createValidator(MethodDescriptor<T, S> methodDescriptor);
    }

    private final ValidatorFactory validatorFactory;

    public ValidationServerInterceptor(ValidatorFactory validatorFactory) {

        this.validatorFactory = validatorFactory;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata metadata,
            ServerCallHandler<ReqT, RespT> next) {

        var methodDescriptor = call.getMethodDescriptor();
        var validator = validatorFactory.createValidator(methodDescriptor);

        var nextListener = next.startCall(call, metadata);

        return new ValidationListener<>(validator, nextListener);
    }

    private static class ValidationListener<ReqT> extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {

        private final Consumer<ReqT> validator;

        public ValidationListener(Consumer<ReqT> validator, ServerCall.Listener<ReqT> delegate) {
            super(delegate);
            this.validator = validator;
        }

        @Override
        public void onMessage(ReqT message) {
            validator.accept(message);
            delegate().onMessage(message);
        }
    }
}
