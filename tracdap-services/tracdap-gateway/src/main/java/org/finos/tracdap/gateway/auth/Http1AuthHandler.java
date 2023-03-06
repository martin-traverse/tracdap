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

package org.finos.tracdap.gateway.auth;

import com.google.protobuf.ByteString;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import org.finos.tracdap.api.WebAuthRequest;
import org.finos.tracdap.api.WebAuthResponse;
import org.finos.tracdap.common.auth.external.*;
import org.finos.tracdap.common.auth.internal.JwtProcessor;
import org.finos.tracdap.common.auth.internal.SessionInfo;
import org.finos.tracdap.common.exception.EUnexpected;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;

import org.finos.tracdap.gateway.config.helpers.ApiRoutes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.Deque;


public class Http1AuthHandler extends ChannelDuplexHandler {

    private static final int PENDING_CONTENT_LIMIT = 64 * 1024;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final JwtProcessor jwtProcessor;
    private final IAuthProvider authProvider;
    private final int connId;

    private final Deque<RequestState> requests;

    private static class RequestState {

        AuthResult authResult = AuthResult.FAILED();
        SessionInfo session;
        String token;
        boolean wantCookies;
        boolean authSucceeded;
        boolean authUpdated;

        HttpRequest pendingRequest;
        CompositeByteBuf pendingContent;
        Deque<Object> pendingResponse;
    }

    public Http1AuthHandler(
            JwtProcessor jwtProcessor,
            IAuthProvider authProvider,
            int connId) {

        this.jwtProcessor = jwtProcessor;
        this.authProvider = authProvider;
        this.connId = connId;

        this.requests = new ArrayDeque<>();
    }

    @Override
    public void channelRead(@Nonnull ChannelHandlerContext ctx, @Nonnull Object msg) {

        Object authMsg = null;

        try {

            var state = msg instanceof HttpRequest ? newState() : requests.getLast();

            // Some auth mechanisms require content as well as headers
            // These mechanisms set the result NEED_CONTENT, to trigger aggregation
            // Aggregated messages are fed through the normal flow once they are ready

            authMsg = handleAggregateContent(state, msg);

            if (authMsg == null)
                return;

            // HTTP/1 auth works purely on the request object
            // Each new request will re-run the auth processing

            if ((authMsg instanceof HttpRequest)) {
                var request = (HttpRequest) authMsg;
                processAuthentication(ctx, request, state);
            }

            // If authorization failed a response has already been sent
            // Do not pass any further messages down the pipe

            if (state.authResult.getCode() != AuthResultCode.AUTHORIZED)
                return;

            // Authentication succeeded, allow messages to flow on the connection

            // Special handling for request objects, apply translation to the headers
            if (msg instanceof HttpRequest) {
                var request = (HttpRequest) msg;
                relayRequest(ctx, request);
            }

            // Everything else flows straight through
            else {
                ReferenceCountUtil.retain(msg);
                ctx.fireChannelRead(msg);
            }
        }
        finally {
            ReferenceCountUtil.release(msg);
            if (authMsg != msg)
                ReferenceCountUtil.release(authMsg);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {

        try {

            // Authentication has always succeeded by this point
            // Otherwise no request is made to the platform, so no response would be sent

            // This does not account for pipelining, which is disabled by default in modern browsers

            // Special handling for response objects, apply translation to the headers
            if (msg instanceof HttpResponse) {
                var response = (HttpResponse) msg;
                relayResponse(ctx, response, promise);
            }

            // Everything else flows straight through
            else {
                ReferenceCountUtil.retain(msg);
                ctx.write(msg, promise);
            }
        }
        finally {
            ReferenceCountUtil.release(msg);
        }
    }

    private RequestState newState() {

        var state = new RequestState();
        requests.push(state);

        return state;
    }

    private Object handleAggregateContent(RequestState state, Object msg) {

        if (msg instanceof HttpRequest) {

            var request = (HttpRequest) msg;

            if (ApiRoutes.isAuthApi(request.uri()) || ApiRoutes.isAuth(request.uri())) {
                state.pendingRequest = request;
                return null;
            }
            else
                return msg;
        }

        if (!(msg instanceof HttpContent))
            throw new EUnexpected();

        if (state.pendingRequest == null)
            return msg;

        var content = ((HttpContent) msg).content();

        if (state.pendingContent.readableBytes() + content.readableBytes() > PENDING_CONTENT_LIMIT)
            throw new EUnexpected();  // todo: error

        state.pendingContent.addComponent(content.retain());
        state.pendingContent.writerIndex(state.pendingContent.writerIndex() + content.writerIndex());

        if (msg instanceof LastHttpContent) {

            var aggregateMsg = new DefaultFullHttpRequest(
                    state.pendingRequest.protocolVersion(),
                    state.pendingRequest.method(),
                    state.pendingRequest.uri(),
                    state.pendingContent,
                    state.pendingRequest.headers(),
                    ((LastHttpContent) msg).trailingHeaders());

            state.pendingRequest = null;
            state.pendingContent = null;

            return aggregateMsg;
        }

        return null;
    }

    private void processAuthentication(ChannelHandlerContext ctx, HttpRequest request, RequestState state) {

        // Start the auth process by looking for the TRAC auth token
        // If there is already a valid session, this takes priority

        var headers = new Http1AuthHeaders(request.headers());

        // Decide whether to send the auth response as headers or cookies
        // Always send cookies for browser routes
        // For API routes the client can set a header to prefer cookies in the response

        var isApi = ApiRoutes.isApi(request.uri());
        state.wantCookies = !isApi || headers.contains(AuthLogic.TRAC_AUTH_COOKIES_HEADER);

        // Look for an existing session token in the request
        // If the token gives a valid session then authentication has succeeded

        state.token = AuthLogic.findTracAuthToken(headers, AuthLogic.SERVER_COOKIE);
        state.session = (state.token != null) ? jwtProcessor.decodeAndValidate(state.token) : null;

        if (state.session != null && state.session.isValid()) {

            // Check to see if the token needs refreshing
            var sessionUpdate = jwtProcessor.refreshSession(state.session);

            if (sessionUpdate != state.session) {
                state.token = jwtProcessor.encodeToken(sessionUpdate);
                state.session = sessionUpdate;
                state.authUpdated = true;
            }

            state.authResult = AuthResult.AUTHORIZED(state.session.getUserInfo());
            state.authSucceeded = true;
            return;
        }

        // If auth is added as a service API, allow access here

        if (isApi) {

            state.authResult = AuthResult.FAILED("Session expired or not available");
            return;
        }

        if (ApiRoutes.isAuth(request.uri()) || canAcceptHtml(request)) {

            var authRequest = WebAuthRequest.newBuilder()
                    .setMethod(request.method().name())
                    .setUri(request.uri());

            for (var header : request.headers())
                authRequest.putHeaders(header.getKey(), header.getValue());

            if (request instanceof FullHttpRequest)
                authRequest.setContent(ByteString.copyFrom(((FullHttpRequest) request).content().nioBuffer()));

            authProvider.webLogin(authRequest.build())
                    .thenAccept(response -> processAuthResponse(ctx, request, response))
                    .exceptionally(error -> processAuthError(ctx, request, error));

            return;
        }

        state.authResult = AuthResult.FAILED("Session expired or not available");
    }

    private boolean canAcceptHtml(HttpRequest request) {

        for (var acceptHeader : request.headers().getAll(HttpHeaderNames.ACCEPT))
            if (acceptHeader.contains("text/html"))
                return true;

        return false;
    }

    private void processAuthResponse(ChannelHandlerContext ctx, HttpRequest request, WebAuthResponse authResponse) {

        var responseCode = authResponse.getStatusMessage().isBlank()
                ? HttpResponseStatus.valueOf(authResponse.getStatusCode())
                : HttpResponseStatus.valueOf(authResponse.getStatusCode(), authResponse.getStatusMessage());

        var responseHeaders = new DefaultHttpHeaders();

        for (var header : authResponse.getHeadersMap().entrySet())
            responseHeaders.add(header.getKey(), header.getValue());

        var responseContent = Unpooled.wrappedBuffer(authResponse.getContent().asReadOnlyByteBuffer());

        var clientResponse = new DefaultFullHttpResponse(
                request.protocolVersion(),
                responseCode,
                responseContent,
                responseHeaders,
                EmptyHttpHeaders.INSTANCE);

        ctx.write(clientResponse);
        ctx.flush();
    }

    private Void processAuthError(ChannelHandlerContext ctx, HttpRequest request, Throwable authError) {

        var responseCode = HttpResponseStatus.valueOf(
                HttpResponseStatus.BAD_GATEWAY.code(),
                "There was a problem during authentication");

        var clientResponse = new DefaultFullHttpResponse(
                request.protocolVersion(),
                responseCode);

        ctx.write(clientResponse);
        ctx.flush();
        ctx.close();

        return null;
    }

    private void relayRequest(ChannelHandlerContext ctx, HttpRequest request) {

        var headers = new Http1AuthHeaders(request.headers());
        var emptyHeaders = new Http1AuthHeaders();

        var relayHeaders = AuthLogic.setPlatformAuthHeaders(headers, emptyHeaders, token);

        if (request instanceof FullHttpRequest) {

            var relayContent = ((FullHttpRequest) request).content().retain();

            var relayRequest = new DefaultFullHttpRequest(
                    request.protocolVersion(),
                    request.method(),
                    request.uri(),
                    relayContent,
                    relayHeaders.headers(),
                    new DefaultHttpHeaders());

            ctx.fireChannelRead(relayRequest);
        }
        else {

            var relayRequest = new DefaultHttpRequest(
                    request.protocolVersion(),
                    request.method(),
                    request.uri(),
                    relayHeaders.headers());

            ctx.fireChannelRead(relayRequest);
        }
    }

    private void relayResponse(ChannelHandlerContext ctx, HttpResponse response, ChannelPromise promise) {

        var headers = new Http1AuthHeaders(response.headers());
        var emptyHeaders = new Http1AuthHeaders();

        var relayHeaders = AuthLogic.setClientAuthHeaders(headers, emptyHeaders, token, session, wantCookies);

        if (response instanceof FullHttpResponse) {

            var relayContent = ((FullHttpResponse) response).content().retain();

            var relayResponse = new DefaultFullHttpResponse(
                    response.protocolVersion(),
                    response.status(),
                    relayContent,
                    relayHeaders.headers(),
                    new DefaultHttpHeaders());

            ctx.write(relayResponse, promise);
        }
        else {

            var relayResponse = new DefaultHttpResponse(
                    response.protocolVersion(),
                    response.status(),
                    relayHeaders.headers());

            ctx.write(relayResponse, promise);
        }
    }

    private void sendResponse(Object msg) {

    }

    private FullHttpResponse buildAuthResponse(HttpRequest request, AuthResponse responseDetails) {

        var responseCode = HttpResponseStatus.valueOf(
                responseDetails.getStatusCode(),
                responseDetails.getStatusMessage());

        var responseHeaders = new DefaultHttpHeaders();
        for (var header : responseDetails.getHeaders())
            responseHeaders.add(header.getKey(), header.getValue());

        return new DefaultFullHttpResponse(
                request.protocolVersion(), responseCode,
                responseDetails.getContent(), responseHeaders,
                new DefaultHttpHeaders());
    }

    private FullHttpResponse buildFailedResponse(HttpRequest request, AuthResult authResult) {

        var responseCode = HttpResponseStatus.valueOf(
                HttpResponseStatus.UNAUTHORIZED.code(),
                authResult.getMessage());

        return new DefaultFullHttpResponse(
                request.protocolVersion(), responseCode,
                Unpooled.EMPTY_BUFFER,
                new DefaultHttpHeaders(),
                new DefaultHttpHeaders());
    }
}
