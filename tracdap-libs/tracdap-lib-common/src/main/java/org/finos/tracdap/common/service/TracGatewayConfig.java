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

package org.finos.tracdap.common.service;

import org.finos.tracdap.common.config.ConfigHelpers;
import org.finos.tracdap.common.config.ConfigKeys;
import org.finos.tracdap.common.config.ConfigDefaults;
import org.finos.tracdap.common.middleware.CommonConcerns;
import org.finos.tracdap.common.middleware.CommonNettyConcerns;
import org.finos.tracdap.common.middleware.NettyConcern;
import org.finos.tracdap.common.middleware.SupportedProtocol;
import org.finos.tracdap.common.netty.ConnectionId;
import org.finos.tracdap.config.ServiceConfig;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerKeepAliveHandler;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.TimeUnit;


public class TracGatewayConfig {

    public static final String TRAC_GATEWAY_CONCERNS = "trac_gateway_concerns";
    public static final String TRAC_IDLE_STATE = "trac_idle_state";

    public static CommonConcerns<NettyConcern> emptyConfig() {
        return new CommonNettyConcerns(TRAC_GATEWAY_CONCERNS);
    }

    public static CommonConcerns<NettyConcern> coreConcerns(String serviceName, ServiceConfig serviceConfig) {

        return emptyConfig()
                .addLast(new IdleHandling(serviceName, serviceConfig));
    }

    public static class IdleHandling implements NettyConcern {

        private static final String IDLE_STATE_HANDLER = "idle_state_handler";
        private static final String IDLE_TIMEOUT_HANDLER = "idle_timeout_handler";
        private static final String HTTP_1_KEEPALIVE = "http_1_keepalive";

        private final int idleTimeout;

        public IdleHandling(String serviceName, ServiceConfig serviceConfig) {
            this.idleTimeout = getIdleTimeout(serviceName, serviceConfig);
        }

        private static int getIdleTimeout(String serviceName, ServiceConfig serviceConfig) {

            var serviceProperties = new Properties();
            serviceProperties.putAll(serviceConfig.getPropertiesMap());

            return ConfigHelpers.readInt(
                    serviceName, serviceProperties,
                    ConfigKeys.NETWORK_IDLE_TIMEOUT,
                    ConfigDefaults.NETWORK_IDLE_TIMEOUT);
        }

        @Override
        public String concernName() {
            return TRAC_IDLE_STATE;
        }

        @Override
        public void configureInboundChannel(ChannelPipeline pipeline, SupportedProtocol protocol) {

            String priorStage = null;

            if (protocol == SupportedProtocol.HTTP) {

                // For HTTP/1 connections, keep-alive will always be present and is the last protocol stage
                var httpKeepalive = pipeline.context(HttpServerKeepAliveHandler.class);
                priorStage = httpKeepalive.name();
            }
            else if (protocol == SupportedProtocol.WEB_SOCKETS) {

                // For Websockets, the WS protocol handler is the last protocol stage
                var wsCodec = pipeline.context(WebSocketServerProtocolHandler.class);
                priorStage = wsCodec.name();
            }

            if (priorStage != null) {

                // For connections that are kept alive, we need to handle timeouts
                // This idle state handler will trigger idle events after the configured timeout
                // The CoreRouter class is responsible for handling the idle events
                var idleHandler = new IdleStateHandler(idleTimeout, idleTimeout, idleTimeout, TimeUnit.SECONDS);
                var idleEnforcer = new IdleTimeoutEnforcer();
                pipeline.addAfter(priorStage, IDLE_STATE_HANDLER, idleHandler);
                pipeline.addAfter(IDLE_STATE_HANDLER, IDLE_TIMEOUT_HANDLER, idleEnforcer);
            }
        }
    }

    private static class IdleTimeoutEnforcer extends ChannelInboundHandlerAdapter {

        private static final Logger log = LoggerFactory.getLogger(IdleTimeoutEnforcer.class);

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {

            if (evt instanceof IdleStateEvent) {

                if (log.isDebugEnabled()) {
                    var connId = ConnectionId.get(ctx.channel());
                    log.info("IDLE TIMEOUT: conn = {}", connId);
                }

                ctx.close();
            }
            else
                ctx.fireUserEventTriggered(evt);
        }
    }

}
