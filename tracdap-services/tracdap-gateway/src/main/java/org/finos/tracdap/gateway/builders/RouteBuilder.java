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

package org.finos.tracdap.gateway.builders;

import io.netty.handler.codec.http.HttpMethod;
import org.finos.tracdap.common.util.RoutingUtils;
import org.finos.tracdap.config.*;
import org.finos.tracdap.gateway.exec.IRouteMatcher;
import org.finos.tracdap.gateway.exec.Route;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


public class RouteBuilder {

    public static final String HEALTH_CHECK_NAME = "Health Check";
    public static final String HEALTH_CHECK_KEY = "healthz";
    public static final String HEALTH_CHECK_PATH = "/healthz";

    private static final Logger log = LoggerFactory.getLogger(RouteBuilder.class);

    private static final ClassLoader API_CLASSLOADER = RouteBuilder.class.getClassLoader();
    private static final String HTTP_SCHEME = "http";

    private int nextRouteIndex;

    public RouteBuilder() {
        nextRouteIndex = 0;
    }

    public List<Route> buildRoutes(PlatformConfig platformConfig) {

        log.info("Building the route table...");

        var services = ServiceInfo.buildServiceInfo(platformConfig);
        var customRoutes = platformConfig.getGateway().getRoutesList();

        var nRoutes = services.size() * 2 + customRoutes.size();
        var routes = new ArrayList<Route>(nRoutes);

        var customHealth = customRoutes.stream().anyMatch(route -> route.getRouteKey().equals(HEALTH_CHECK_KEY));

        if (!customHealth) {
            var healthCheckRoute = buildHealthCheckRoute();
            routes.add(healthCheckRoute);
        }

        for (var serviceInfo : services) {
            if (serviceInfo.hasGrpc()) {
                var grpcRoute = buildGrpcServiceRoute(platformConfig, serviceInfo);
                routes.add(grpcRoute);
            }
        }

        for (var serviceInfo : services) {
            if (serviceInfo.hasRest()) {
                var restApiRoute = buildRestServiceRoute(platformConfig, serviceInfo);
                routes.add(restApiRoute);
            }
        }

        for (var serviceInfo : services) {
            if (serviceInfo.hasHttp()) {
                var restApiRoute = buildHttpServiceRoute(platformConfig, serviceInfo);
                routes.add(restApiRoute);
            }
        }

        for (var routeConfig : customRoutes) {
            var route = buildCustomRoute(routeConfig);
            routes.add(route);
        }

        for (var route : routes) {

            log.info("[{}] {} -> {} ({})",
                    route.getIndex(),
                    route.getConfig().getMatch().getPath(),
                    route.getConfig().getRouteName(),
                    route.getConfig().getRouteType());
        }

        return routes;
    }

    private Route buildHealthCheckRoute() {

        var routeIndex =nextRouteIndex++;

        var routeConfig = RouteConfig.newBuilder()
                .setRouteKey(HEALTH_CHECK_KEY)
                .setRouteName(HEALTH_CHECK_NAME)
                .setRouteType(RoutingProtocol.INTERNAL)
                .addProtocols(RoutingProtocol.HTTP)
                .setMatch(RoutingMatch.newBuilder()
                        .setPath(HEALTH_CHECK_PATH))
                .setTarget(RoutingTarget.newBuilder()
                        .setScheme(HEALTH_CHECK_KEY))
                .build();

        var matcher = (IRouteMatcher) (method, url) ->
                url.getPath().equals(HEALTH_CHECK_PATH) &&
                (method == HttpMethod.HEAD || method == HttpMethod.GET);

        return new Route(routeIndex, routeConfig, matcher);
    }

    private Route buildGrpcServiceRoute(PlatformConfig platformConfig, ServiceInfo serviceInfo) {

        var routeIndex = nextRouteIndex++;
        var routeName = serviceInfo.serviceName();
        var routeType = RoutingProtocol.GRPC;

        var grpcPath = '/' + serviceInfo.descriptor().getFullName() + "/";
        var matcher = (IRouteMatcher) (method, url) -> url.getPath().startsWith(grpcPath);
        var protocols = List.of(RoutingProtocol.GRPC, RoutingProtocol.GRPC_WEB);
        var routing = RoutingUtils.serviceTarget(platformConfig, serviceInfo.serviceKey());

        var match = RoutingMatch.newBuilder()
                .setPath(grpcPath);

        var target = RoutingTarget.newBuilder()
                .mergeFrom(routing)
                .setScheme(HTTP_SCHEME)
                .setPath(grpcPath);

        var routeConfig = RouteConfig.newBuilder()
                .setRouteName(routeName)
                .setRouteType(routeType)
                .addAllProtocols(protocols)
                .setMatch(match)
                .setTarget(target)
                .build();

        return new Route(routeIndex, routeConfig, matcher);
    }

    private Route buildRestServiceRoute(PlatformConfig platformConfig, ServiceInfo serviceInfo) {

        var routeIndex = nextRouteIndex++;
        var routeName = serviceInfo.serviceName();
        var routeType = RoutingProtocol.REST;

        var restPath = serviceInfo.restPrefix();
        var matcher = (IRouteMatcher) (method, url) -> url.getPath().startsWith(restPath);
        var protocols = List.of(RoutingProtocol.REST);
        var routing = RoutingUtils.serviceTarget(platformConfig, serviceInfo.serviceKey());

        var restMethodPrefix = restPath.endsWith("/") ? restPath.substring(0, restPath.length() - 1) : restPath;
        var restMethods = RestApiBuilder.buildAllMethods(serviceInfo.descriptor(), restMethodPrefix, API_CLASSLOADER);

        var match = RoutingMatch.newBuilder()
                .setPath(restPath);

        var target = RoutingTarget.newBuilder()
                .mergeFrom(routing)
                .setScheme(HTTP_SCHEME)
                .setPath(restPath);

        var routeConfig = RouteConfig.newBuilder()
                .setRouteName(routeName)
                .setRouteType(routeType)
                .addAllProtocols(protocols)
                .setMatch(match)
                .setTarget(target)
                .build();

        return new Route(routeIndex, routeConfig, matcher, restMethods);
    }

    private Route buildHttpServiceRoute(PlatformConfig platformConfig, ServiceInfo serviceInfo) {

        var routeIndex = nextRouteIndex++;
        var routeName = serviceInfo.serviceName();
        var routeType = RoutingProtocol.HTTP;

        var httpPath = serviceInfo.httpPrefix();
        var matcher = (IRouteMatcher) (method, url) -> url.getPath().startsWith(httpPath);
        var protocols = List.of(RoutingProtocol.HTTP);
        var routing = RoutingUtils.serviceTarget(platformConfig, serviceInfo.serviceKey());

        var match = RoutingMatch.newBuilder()
                .setPath(httpPath);

        var target = RoutingTarget.newBuilder()
                .mergeFrom(routing)
                .setScheme(HTTP_SCHEME)
                .setPath("/");

        var routeConfig = RouteConfig.newBuilder()
                .setRouteName(routeName)
                .setRouteType(routeType)
                .addAllProtocols(protocols)
                .setMatch(match)
                .setTarget(target)
                .build();

        return new Route(routeIndex, routeConfig, matcher);
    }

    private Route buildCustomRoute(RouteConfig routeConfig) {

        var routeIndex = nextRouteIndex++;

        var customPath = routeConfig.getMatch().getPath();
        var matcher = (IRouteMatcher) (method, uri) -> uri.getPath().startsWith(customPath);

        return new Route(routeIndex, routeConfig, matcher);
    }
}
