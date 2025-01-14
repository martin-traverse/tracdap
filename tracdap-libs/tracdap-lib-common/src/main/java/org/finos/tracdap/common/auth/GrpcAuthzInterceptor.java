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

package org.finos.tracdap.common.auth;

import org.finos.tracdap.api.Authorization;
import org.finos.tracdap.api.AuthorizationOptions;
import org.finos.tracdap.common.exception.EAuthorization;
import org.finos.tracdap.common.exception.ETracInternal;
import org.finos.tracdap.common.grpc.DelayedExecutionInterceptor;
import org.finos.tracdap.common.grpc.GrpcErrorMapping;
import org.finos.tracdap.common.grpc.GrpcServiceRegister;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import io.grpc.*;
import org.finos.tracdap.config.AuthenticationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class GrpcAuthzInterceptor extends DelayedExecutionInterceptor {

    // Validation requires delayed execution, because it has to wait for the first onMessage() call
    // Use delayed interceptor as a base, which has the logic to manage the request sequence

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final AuthenticationConfig authConfig;
    private final GrpcServiceRegister serviceRegister;
    private final boolean loggingEnabled;

    public GrpcAuthzInterceptor(AuthenticationConfig authConfig, GrpcServiceRegister serviceRegister) {
        this(authConfig, serviceRegister, true);
    }

    public GrpcAuthzInterceptor(AuthenticationConfig authConfig, GrpcServiceRegister serviceRegister, boolean loggingEnabled) {
        this.authConfig = authConfig;
        this.serviceRegister = serviceRegister;
        this.loggingEnabled = loggingEnabled;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT>
    interceptCall(
            ServerCall<ReqT, RespT> serverCall, Metadata metadata,
            ServerCallHandler<ReqT, RespT> nextHandler) {

        var authorizationCall = new DelayedExecutionCall<>(serverCall);

        return new AuthorizationListener<>(authorizationCall, metadata, nextHandler, loggingEnabled);
    }

    private class AuthorizationListener<ReqT, RespT> extends DelayedExecutionListener<ReqT, RespT> {

        private final ServerCall<ReqT, RespT> serverCall;
        private final boolean loggingEnabled;

        private final Descriptors.MethodDescriptor methodDescriptor;
        private final AuthorizationOptions authzOptions;
        private boolean authorized = false;

        public AuthorizationListener(
                ServerCall<ReqT, RespT> serverCall,
                Metadata metadata,
                ServerCallHandler<ReqT, RespT> nextHandler,
                boolean loggingEnabled) {

            super(serverCall, metadata, nextHandler);

            this.serverCall = serverCall;
            this.loggingEnabled = loggingEnabled;

            // Look up the descriptor for this call, to use for validation
            var grpcDescriptor = serverCall.getMethodDescriptor();
            var grpcMethodName = grpcDescriptor.getFullMethodName();

            this.methodDescriptor = serviceRegister.getMethodDescriptor(grpcMethodName);

            if (!methodDescriptor.getOptions().hasExtension(Authorization.authorization)) {
                var error = String.format("Authorization requirements not defined for method [%s]", grpcMethodName);
                log.error(error);
                throw new ETracInternal(error);
            }

            this.authzOptions = this.methodDescriptor.getOptions().getExtension(Authorization.authorization);
        }

        @Override
        public void onMessage(ReqT req) {

            if (authConfig.getDisableAuth()) {
                log.warn("AUTHORIZE {}(): {}", methodDescriptor.getName(), "Authorization disabled in config");
                delegate().onMessage(req);
                return;
            }

            if (authorized) {
                delegate().onMessage(req);
                return;
            }

            var message = (Message) req;

            var role = authzOptions.hasSemantics()
                    ? applySemantics(message)
                    : authzOptions.getRole();

            var authorization = checkRole(role);

            if (authorization) {

                log.info("AUTHORIZE {}(): {}", methodDescriptor.getName(), role);

                this.authorized = true;

                // Allow delayed interceptor to start the normal flow of events
                startCall();
                delegate().onMessage(req);
            }
            else {

                var authError = new EAuthorization("Authorization failed for method [" + methodDescriptor.getName() + "]");
                var mappedError = GrpcErrorMapping.processError(authError);
                var status = mappedError.getStatus();

                // Allow logging to be turned on / off to avoid duplicate logs
                // E.g. if this interceptor is behind the logging interceptor
                if (loggingEnabled) {

                    log.error("AUTHORIZATION FAILED: {}() {}",
                            methodDescriptor.getName(),
                            status.getDescription(),
                            status.getCause());
                }

                serverCall.close(status, mappedError.getTrailers());
            }
        }

        private String applySemantics(Message message) {

            return this.authzOptions.getRole();
        }

        private boolean checkRole(String role, Message message) {

            var tenantField = message.getDescriptorForType().findFieldByName("tenant");
            var tenant = message.getField(tenantField);

            var systemUser = GrpcAuthHelpers.currentUser();
            var authUser = GrpcAuthHelpers.currentAuthUser();

            return true;
        }
    }
}
