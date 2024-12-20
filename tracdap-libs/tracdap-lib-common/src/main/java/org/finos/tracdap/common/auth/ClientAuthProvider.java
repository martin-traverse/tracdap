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

import io.grpc.CallCredentials;
import io.grpc.CallOptions;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.AbstractStub;

import java.util.concurrent.Executor;


public class ClientAuthProvider extends CallCredentials {

    private final String token;

    public static <T extends AbstractStub<T>>  T applyIfAvailable(T client, String token) {

        if (token != null && !token.isBlank())
            return client.withCallCredentials((new ClientAuthProvider(token)));
        else
            return client;
    }

    public static CallOptions applyIfAvailable(CallOptions callOptions, String token) {

        if (token != null && !token.isBlank())
            return callOptions.withCallCredentials((new ClientAuthProvider(token)));
        else
            return callOptions;
    }

    private ClientAuthProvider(String token) {
        this.token = token;
    }

    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor, MetadataApplier applier) {

        if (token == null || token.isBlank()) {

            applier.fail(Status.UNAUTHENTICATED);
        }
        else {

            var authHeaders = new Metadata();
            authHeaders.put(AuthConstants.TRAC_AUTH_TOKEN_KEY, token);

            applier.apply(authHeaders);
        }
    }
}
