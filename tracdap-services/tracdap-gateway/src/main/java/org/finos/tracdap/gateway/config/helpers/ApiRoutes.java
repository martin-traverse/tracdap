/*
 * Copyright 2023 Accenture Global Solutions Limited
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

package org.finos.tracdap.gateway.config.helpers;

import io.grpc.ServiceDescriptor;
import org.finos.tracdap.api.TracAuthApiGrpc;
import org.finos.tracdap.api.TracDataApiGrpc;
import org.finos.tracdap.api.TracMetadataApiGrpc;
import org.finos.tracdap.api.TracOrchestratorApiGrpc;

import java.util.List;

public class ApiRoutes {

    public static final List<String> API_ROUTES = List.of(
            grpcRoutePrefix(TracMetadataApiGrpc.getServiceDescriptor()),
            grpcRoutePrefix(TracDataApiGrpc.getServiceDescriptor()),
            grpcRoutePrefix(TracOrchestratorApiGrpc.getServiceDescriptor()),
            grpcRoutePrefix(TracAuthApiGrpc.getServiceDescriptor()),
            "/tracdap-meta/api/",
            "/tracdap-data/api/",
            "/tracdap-orch/api/",
            "/tracdap-auth/api/");

    public static final List<String> AUTH_API_ROUTES = List.of(
            grpcRoutePrefix(TracAuthApiGrpc.getServiceDescriptor()),
            "/tracdap-auth/api/");

    public static final String AUTH_ROUTES = "/trac-auth/";

    public static boolean isApi(String url) {

        return API_ROUTES.stream().anyMatch(url::startsWith);
    }

    public static boolean isAuthApi(String url) {

        return AUTH_API_ROUTES.stream().anyMatch(url::startsWith);
    }

    public static boolean isAuth(String url) {

        return url.startsWith(AUTH_ROUTES);
    }

    private static String grpcRoutePrefix(ServiceDescriptor service) {

        return "/" + service.getName() + "/";
    }
}
