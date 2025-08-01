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

(function(global, factory) { /* global define, require, module */

    if (typeof define === 'function' && define.amd) {

        /* AMD */

        define([$DEPENDENCY, 'grpc-web'], factory);
    }

    else if (typeof require === 'function' && typeof module === 'object' && module && module.exports) {

        /* CommonJS */

        $root = factory(require($DEPENDENCY), require('grpc-web'));

        // Allow recent NPM versions to auto-detect ES6-style exports
        module.exports = $root;
        module.exports.tracdap = $root.tracdap;
        module.exports.tracdap.api = $root.tracdap.api;
        module.exports.tracdap.metadata = $root.tracdap.metadata;
        module.exports.google = $root.google;
    }

})(this, function($protobuf, grpc) {

    "use strict";

    $OUTPUT;

    const tracdap = $root.tracdap;

    tracdap.api.TracMetadataApi._serviceName = "tracdap.api.TracMetadataApi";
    tracdap.api.TracDataApi._serviceName = "tracdap.api.TracDataApi";
    tracdap.api.TracOrchestratorApi._serviceName = "tracdap.api.TracOrchestratorApi";
    tracdap.api.TracAdminApi._serviceName = "tracdap.api.TracAdminApi";

    grpc.MethodType.CLIENT_STREAMING = "CLIENT_STREAMING";
    grpc.MethodType.BIDI_STREAMING = "BIDI_STREAMING";

    const METHOD_TYPE_MAP = $METHOD_TYPE_MAPPING;

    const DEFAULT_TRANSPORT = "google"

    const TRAC_ERROR_DETAILS_KEY = "trac-error-details-bin";


    // Streaming upload cancellation

    // ProtobufJS does not currently provide a cancel() function to abort streaming uploads
    // This is a work-around that can be used in client code until such a function is provided
    // Setting rpcImpl = null will prevent any further messages from being sent
    // If the EOS message has not been sent TRAC will make the call timeout, so it will not complete
    // An error will propagate back to client code because the connection was closed unexpectedly
    // To avoid delays, client code should report an error immediately after calling cancel

    // A more sophisticated implementation would send signals into rpcImpl
    // These signals could be used by the transport to terminate the connection
    // However, this would be better done as part of the protobufjs framework itself
    // I have raised a ticket for consideration
    // If it doesn't get into the framework we can add the signal mechanism here instead

    // https://github.com/protobufjs/protobuf.js/issues/1849


    /**
     * Cancel an existing stream.
     *
     * <p>A request can only be cancelled before the complete request has been sent.
     * Cancelling a request after it is fully sent has no effect.</p>
     *
     * <p>For unary requests and download streams (server streaming),
     * the entire request is sent in a single message so these request cannot be cancelled.
     * For upload (client streaming) and bidirectional streams,
     * the request can be any time before .end() is called.</p>
     *
     * @function cancel
     * @memberof tracdap.api.TracMetadataApi
     * @instance
     * @returns {tracdap.api.TracMetadataApi}
     */
    Object.defineProperty($root.tracdap.api.TracMetadataApi.prototype.cancel = function cancel() {

        if (this.rpcImpl) {
            this.rpcImpl = null;
        }
        return this;

    }, "name", { value: "cancel" });

    /**
     * Cancel an existing stream.
     *
     * <p>A request can only be cancelled before the complete request has been sent.
     * Cancelling a request after it is fully sent has no effect.</p>
     *
     * <p>For unary requests and download streams (server streaming),
     * the entire request is sent in a single message so these request cannot be cancelled.
     * For upload (client streaming) and bidirectional streams,
     * the request can be any time before .end() is called.</p>
     *
     * @function cancel
     * @memberof tracdap.api.TracDataApi
     * @instance
     * @returns {tracdap.api.TracDataApi}
     */
    Object.defineProperty($root.tracdap.api.TracDataApi.prototype.cancel = function cancel() {

        if (this.rpcImpl) {
            this.rpcImpl = null;
        }
        return this;

    }, "name", { value: "cancel" });

    /**
     * Cancel an existing stream.
     *
     * <p>A request can only be cancelled before the complete request has been sent.
     * Cancelling a request after it is fully sent has no effect.</p>
     *
     * <p>For unary requests and download streams (server streaming),
     * the entire request is sent in a single message so these request cannot be cancelled.
     * For upload (client streaming) and bidirectional streams,
     * the request can be any time before .end() is called.</p>
     *
     * @function cancel
     * @memberof tracdap.api.TracOrchestratorApi
     * @instance
     * @returns {tracdap.api.TracOrchestratorApi}
     */
    Object.defineProperty($root.tracdap.api.TracOrchestratorApi.prototype.cancel = function cancel() {

        if (this.rpcImpl) {
            this.rpcImpl = null;
        }
        return this;

    }, "name", { value: "cancel" });


    /**
     * Cancel an existing stream.
     *
     * <p>A request can only be cancelled before the complete request has been sent.
     * Cancelling a request after it is fully sent has no effect.</p>
     *
     * <p>For unary requests and download streams (server streaming),
     * the entire request is sent in a single message so these request cannot be cancelled.
     * For upload (client streaming) and bidirectional streams,
     * the request can be any time before .end() is called.</p>
     *
     * @function cancel
     * @memberof tracdap.api.TracAdminApi
     * @instance
     * @returns {tracdap.api.TracAdminApi}
     */
    Object.defineProperty($root.tracdap.api.TracAdminApi.prototype.cancel = function cancel() {

        if (this.rpcImpl) {
            this.rpcImpl = null;
        }
        return this;

    }, "name", { value: "cancel" });


    const GoogleTransport = (function() {

        function GoogleTransport(serviceName, protocol, host, port, options) {

            this.serviceName = serviceName;
            this.options = options;

            this.hostAddress = protocol + "://" + host + ":" + port;
            this.urlPrefix = options["browser"] ? "" : this.hostAddress;

            this.rpcMetadata = {
                "trac-auth-cookies": "true"  // request the auth response is sent back in cookies
            }

            this.grpcWeb = new grpc.GrpcWebClientBase({format: 'binary'});

            this.options.debug && console.log(`GoogleTransport created, host address = [${this.hostAddress}]`);
        }

        GoogleTransport.prototype.rpcImpl = function(method, request, callback) {

            try {

                const methodUrl = `${this.urlPrefix}/${this.serviceName}/${method.name}`;

                const methodType = (method.name in METHOD_TYPE_MAP)
                    ? METHOD_TYPE_MAP[method.name]
                    : grpc.MethodType.UNARY;

                const methodDescriptor = new grpc.MethodDescriptor(
                    methodUrl, methodType,
                    Uint8Array, Uint8Array,
                    request => request,
                    response => response);

                if (methodType === grpc.MethodType.SERVER_STREAMING)
                    this._grpcWebServerStreaming(method, methodDescriptor, request, callback);

                else
                    this._grpcWebUnary(method, methodDescriptor, request, callback);
            }
            catch (error) {
                console.log(JSON.stringify(error));
                callback(error, null);
            }
        }

        GoogleTransport.prototype._grpcWebUnary = function(method, descriptor, request, callback) {

            this.grpcWeb.rpcCall(method.name, request, this.rpcMetadata, descriptor, callback);

            // Currently no need to process the "metadata" and "status" events
            // Anyway "status" is handled by the grpc-web framework
            // Auth headers come back in cookies and are handled by the browser
        }

        GoogleTransport.prototype._grpcWebServerStreaming = function(method, descriptor, request, callback) {

            const stream = this.grpcWeb.serverStreaming(method.name, request, this.rpcMetadata, descriptor);

            stream.on("data", msg => callback(null, msg));
            stream.on("end", () => callback(null, null));
            stream.on("error", err => callback(err, null));

            // Currently no need to process the "metadata" and "status" events
            // Anyway "status" is handled by the grpc-web framework
            // Auth headers come back in cookies and are handled by the browser
        }

        return GoogleTransport;

    })();


    const TracTransport = (function() {

        const GRPC_STATUS_HEADER = "grpc-status";
        const GRPC_MESSAGE_HEADER = "grpc-message";

        const LPM_PREFIX_LENGTH = 5;
        const CLOSE_DELAY_GRACE_PERIOD = 1000;

        const STANDARD_REQUEST_HEADERS = {
            "content-type": "application/grpc-web+proto",
            "accept": "application/grpc-web+proto",
            "x-grpc-web": 1,
            "x-user-agent": "trac-web-transport",  // TODO: version
            "trac-auth-cookies": "true"  // request the auth response is sent back in cookies
        }

        const FILTER_RESPONSE_HEADERS = ["cookie", "set-cookie", "authorization"]

        const MAX_WS_FRAME_SIZE = 64 * 1024;
        const COMPRESSION_THRESHOLD = 64 * 1024;


        function TracTransport(serviceName, protocol, host, port, options) {

            this.serviceName = serviceName;
            this.protocol = protocol;
            this.wsProtocol = protocol.replace("http", "ws");
            this.host = host;
            this.port = port;
            this.options = options;

            this.hostAddress = `${protocol}://${host}:${port}`;
            this.urlPrefix = options["browser"] ? "" : this.hostAddress;

            this.options.debug && console.log(`TracTransport created, host address = [${this.hostAddress}]`);

            // Store method type of the first requested call
            // A unary call may be followed by more unary calls on the same transport
            // For streaming calls, the transport can only be used once
            this.methodType = null;

            // Internal state related to a single streaming call
            this.ws = null;
            this.sendQueue = [];
            this.sendDone = false;
            this.rcvQueue = [];
            this.rcvMsgQueue = [];
            this.rcvFlag = 0;
            this.rcvLength = -1;
            this.rcvDone = false;
            this.closeDelay = false;
            this.finished = false;

            // Response state for a single streaming call
            // Responses are  held until the final status is seen and the connection closed
            this.requestMetadata = {};
            this.responseMetadata = {};
            this.response = null;

            if (options.compress) {

                try {

                    new CompressionStream("gzip");
                    new DecompressionStream("gzip");

                    this.compressionEnabled = true;
                    this.requestMetadata["grpc-encoding"] = "gzip"
                    this.requestMetadata["grpc-accept-encoding"] = "gzip"
                }
                catch (e) {
                    console.log("Fast compression is not available, API compression has been disabled");
                    this.compressionEnabled = false;
                }
            }
        }


        // -------------------------------------------------------------------------------------------------------------
        // PROTOBUF.JS API
        // -------------------------------------------------------------------------------------------------------------


        TracTransport.prototype.rpcImpl = function(method, request, callback) {

            this.options.debug && console.log("TracTransport rpcImpl")

            let firstMessage = false;

            if (this.methodType === null) {

                firstMessage = true;

                if (method === null || method.name === null) {
                    throw new Error("TRAC Web API: Internal error (method not supplied");
                }
                else {
                    this._setMethodInfo(method, callback)
                }
            }

            if (this.methodType === grpc.MethodType.UNARY) {

                if (method === null || method.name === null) {
                    throw new Error("TRAC Web API: Internal error (method not supplied");
                }

                this._fetchCall(method, request, callback);
            }
            else {

                // For streaming calls, try to raise errors if client code uses the same stream twice
                // Harder to do for client streaming, because multiple messages are expected

                if (this.methodType === grpc.MethodType.SERVER_STREAMING && !firstMessage) {
                    throw new Error("TRAC Web API: Streaming calls cannot be reused (create a new stream with tracdap.setup.newStream()");
                }

                if (this.methodType === grpc.MethodType.CLIENT_STREAMING || this.methodType === grpc.MethodType.BIDI_STREAMING) {
                    if (method !== null && method.name !== this.methodName) {
                        throw new Error("TRAC Web API: Streaming calls cannot be reused (create a new stream with tracdap.setup.newStream()");
                    }
                }

                this._wsCall(method, request);
            }
        }

        TracTransport.prototype._setMethodInfo = function(method, callback) {

            this.options.debug && console.log("TracTransport _setMethodInfo")

            this.methodType = (method.name in METHOD_TYPE_MAP)
                ? METHOD_TYPE_MAP[method.name]
                : grpc.MethodType.UNARY;

            this.serverStreaming =
                this.methodType === grpc.MethodType.SERVER_STREAMING ||
                this.methodType === grpc.MethodType.BIDI_STREAMING;

            this.clientStreaming =
                this.methodType === grpc.MethodType.CLIENT_STREAMING ||
                this.methodType === grpc.MethodType.BIDI_STREAMING;

            const methodProtocol = this.methodType === grpc.MethodType.UNARY
                ? this.protocol
                : this.wsProtocol;

            // For streaming calls, hold extra information about the method
            // (needed for processing subsequent messages in the stream)

            if (this.methodType !== grpc.MethodType.UNARY) {
                this.methodName = method.name;
                this.methodUrl = `${methodProtocol}://${this.host}:${this.port}/${this.serviceName}/${this.methodName}`;
                this.callback = callback;
            }
        }


        // -------------------------------------------------------------------------------------------------------------
        // FETCH API
        // -------------------------------------------------------------------------------------------------------------


        TracTransport.prototype._fetchCall = function(method, request, callback) {

            this.options.debug && console.log(`TracTransport _fetchCall, method = [${method.name}]`)

            const methodUrl = `${this.urlPrefix}/${this.serviceName}/${method.name}`;

            const headers = {}
            Object.assign(headers, STANDARD_REQUEST_HEADERS);
            Object.assign(headers, this.requestMetadata);

            this.options.debug && console.log("TracTransport gRPC request headers")
            this.options.debug && console.log(JSON.stringify(headers));

            this._wrapLpm(request, /* wsProtocol = */ false).then(body =>  {

                const params = {
                    method: "POST",
                    headers: headers,
                    body: body
                }

                fetch(methodUrl, params)
                    .then(response => this._fetchResponse(method, response, callback))
                    .catch(error => this._fetchError(method, error, callback));
            })
        }

        TracTransport.prototype._fetchResponse = function(method, response, callback) {

            this.options.debug && console.log(`TracTransport _fetchResponse, method = [${method.name}]`)

            // First check for HTTP / network errors

            if (!response.ok) {
                const responseStatus = grpc.StatusCode.UNKNOWN;
                const responseMessage = `Network error (${response.status} ${response.statusText})`
                const error = {code: responseStatus, message: responseMessage};
                callback(error, null);
                return;
            }

            // For unary calls, grpc-status code can sometimes be in the HTTP headers
            // If status is non-zero return an error straight away, the response will not have a body
            if (response.headers.has('grpc-status')) {
                const grpcStatus = Number.parseInt(response.headers.get("grpc-status"));
                if (grpcStatus !== grpc.StatusCode.OK) {
                    const grpcMessage = response.headers.get("grpc-message");
                    const metadata = this._metadataFromHttpHeaders(response.headers);
                    const error = {code: grpcStatus, message: grpcMessage, metadata: metadata};
                    callback(error, null);
                    return;
                }
            }

            // Response headers look ok, go on to reading the response body

            return response.arrayBuffer()
                .then(body => this._fetchDecode(response, body))
                .then(body => this._fetchComplete(method, body, callback));
        }

        TracTransport.prototype._fetchDecode = function(response, body) {

            this.options.debug && console.log(`TracTransport _fetchDecode`)

            // Body should contain one message for content and one for trailers

            const bodyData = new Uint8Array(body);
            const lpmQueue = []

            this._pushLpmQueue(bodyData, lpmQueue);

            const grpcContent = this._pollLpmQueue(lpmQueue).then(lpm => lpm.message);
            const grpcTrailers = this._pollLpmQueue(lpmQueue).then(lpm => lpm.message);

            // The entire body should be consumed, with one message of content and one of trailers
            if (lpmQueue.length !== 0 || grpcContent === null || !grpcTrailers === null) {
                throw new Error(`Network error (response contains unexpected data)`);
            }

            return Promise.all([grpcContent, grpcTrailers]).then(result => {

                const content = result[0];
                const trailers = result[1];

                // Copy both HTTP headers and LPM trailers into one metadata map
                const metadata = {};
                Object.assign(metadata, this._metadataFromHttpHeaders(response.headers));
                Object.assign(metadata,  this._metadataFromLpmTrailers(trailers));

                // Return headers and content
                return { metadata: metadata, content: content};
            });
        }

        TracTransport.prototype._fetchComplete = function(method, response, callback) {

            this.options.debug && console.log(`TracTransport _fetchComplete, method = [${method.name}]`);

            const metadata = response.metadata;
            const content = response.content;

            // After a full response, grpc-status should always be present

            if (!("grpc-status" in metadata)) {
                // Metadata appears invalid, do not include metadata in error response
                const responseStatus = grpc.StatusCode.UNKNOWN;
                const responseMessage = "Network error (response status not available)";
                const error = { code: responseStatus, message: responseMessage };
                callback(error, null);
                return;
            }

            // If grpc-status != 0, that is always an error

            const grpcStatus = Number.parseInt(metadata["grpc-status"]);
            const grpcMessage = metadata["grpc-message"];

            if (grpcStatus !== grpc.StatusCode.OK) {
                const error = {code: grpcStatus, message: grpcMessage, metadata: metadata};
                callback(error, null);
                return;
            }

            // This is a hook for processing metadata after a successful call

            this._processResponseMetadata(metadata);

            // Everything checks out, we can accept the result

            callback(null, content);
        }

        TracTransport.prototype._fetchError = function(method, error, callback) {

            this.options.debug && console.log(`TracTransport _fetchError, method = [${method.name}]`)

            // An exception occurred processing the call
            // Use UNKNOWN status code with the exception error message

            const errorResponse = {code: grpc.StatusCode.UNKNOWN, message: error.toString()};
            callback(errorResponse, null);
        }

        // -------------------------------------------------------------------------------------------------------------
        // WEB SOCKETS API
        // -------------------------------------------------------------------------------------------------------------


        TracTransport.prototype._wsCall = function (method, request) {

            this.options.debug && console.log("TracTransport _wsCall")

            if (this.ws === null) {
                this._wsConnect();
            }

            if (request === null)
                this._wsSendEos();
            else if (this.clientStreaming)
                this._wsSendMessage(request);
            else {
                this._wsSendMessage(request);
                this._wsSendEos();
            }
        }

        TracTransport.prototype._wsConnect = function() {

            this.options.debug && console.log("TracTransport _wsConnect")

            const ws = new WebSocket(this.methodUrl, "grpc-websockets");
            ws.binaryType = "arraybuffer";

            ws.onopen = this._wsHandleOpen.bind(this);
            ws.onclose = this._wsHandleClose.bind(this);
            ws.onmessage = this._wsHandleMessage.bind(this);
            ws.onerror = this._wsHandlerError.bind(this);

            this.ws = ws;
        }

        TracTransport.prototype._wsHandleOpen = function () {

            this.options.debug && console.log("TracTransport _wsHandleOpen")

            // This is to match the protocol used by improbable eng
            // In their implementation, header frame is not wrapped as an LPM

            const headers = {...STANDARD_REQUEST_HEADERS, ...this.requestMetadata};

            this.options.debug && console.log("TracTransport gRPC request headers")
            this.options.debug && console.log(JSON.stringify(headers));

            const headerMsg = this._encodeHeaders(headers);
            this.ws.send(headerMsg);

            this._wsFlushSendQueue();
        }

        TracTransport.prototype._wsHandleClose = function(event) {

            this.options.debug && console.log("TracTransport _wsHandleClose")

            // This request is already finished, don't call a handler that would call back to client code
            // If there is a problem, put it in the console instead

            if (this.finished) {
                if (this.options.debug && !event.wasClean)
                    console.log(`Connection did not close cleanly: ${event.reason} (websockets code ${event.code})`)
            }

            // When the stream closes, there is sometimes a delay before the messages are processed
            // This happens e.g. for compressed LPMs, where decompression is async
            // Give the processing time to complete before calling the finishing logic
            // If processing does not complete, client will be notified of an error

            else if (this.rcvMsgQueue.length > 0 && !this.closeDelay) {
                this.closeDelay = true;
                setTimeout(() => this._wsHandleClose(event), CLOSE_DELAY_GRACE_PERIOD);
            }

            // Clean shutdown - complete right away

            else if (event.wasClean)
                this._handleComplete();

            // Sometimes there is a response from gRPC even when the connection did not close cleanly
            // This happens if gRPC interrupts a stream to send an error response
            // The response from gRPC is usually more helpful than a generic "connection closed early"

            else if (GRPC_STATUS_HEADER in this.responseMetadata)
                this._handleComplete();

            else {

                const status = grpc.StatusCode.UNKNOWN;
                const message = `Connection did not close cleanly: ${event.reason} (websockets code ${event.code})`
                this._handlerError(status, message);
            }
        }

        TracTransport.prototype._wsHandleMessage = function(event) {

            this.options.debug && console.log("TracTransport _wsHandleMessage")

            this._receiveFrame(event.data)
        }

        TracTransport.prototype._wsHandlerError = function(event) {

            this.options.debug && console.log("TracTransport _wsHandlerError")
            this.options.debug && console.log(`${event.error}`);

            if (this.ws.readyState !== WebSocket.CLOSED)
                this.ws.close(1002, "close on error");

            const status = grpc.StatusCode.UNKNOWN;
            const message = "Network error (WebSockets)";
            this._handlerError(status, message);
        }


        // -------------------------------------------------------------------------------------------------------------
        // MESSAGE SENDING
        // -------------------------------------------------------------------------------------------------------------


        TracTransport.prototype._wsSendMessage = function(msg) {

            this.options.debug && console.log("TracTransport _wsSendMessage")

            const holder = {lpm: null, eos: false};
            this.sendQueue.push(holder);

            this._wrapLpm(msg, /* wsProtocol = */ true).then(lpm => {

                holder.lpm = lpm;

                if (this.ws != null && this.sendQueue.length > 0)
                    this._wsFlushSendQueue();
            })
        }

        TracTransport.prototype._wsSendEos = function() {

            this.options.debug && console.log("TracTransport _wsSendEos")

            this.sendQueue.push({lpm: new Uint8Array([1]), eos: true});

            if (this.ws != null && this.sendQueue.length > 0)
                this._wsFlushSendQueue();
        }

        TracTransport.prototype._wsFlushSendQueue = function () {

            this.options.debug && console.log("TracTransport _wsFlushSendQueue")

            if (this.ws.readyState === WebSocket.CONNECTING)
                return;

            if (this.rcvDone || this.ws.readyState === WebSocket.CLOSED || this.ws.readyState === WebSocket.CLOSING) {
                this.sendQueue = [];
                this.sendDone = true;
                return;
            }

            while (this.sendQueue.length > 0 && this.sendQueue[0].lpm !== null) {

                const holder = this.sendQueue.shift();
                const lpm = holder.lpm;

                this.options.debug && console.log("TracTransport _wsFlushSendQueue: frame size " + lpm.byteLength)

                if (lpm.byteLength <= MAX_WS_FRAME_SIZE) {
                    this.ws.send(lpm);
                }
                else {

                    let offset = 0;

                    while (offset < lpm.byteLength) {

                        const chunkSize = Math.min(lpm.byteLength - offset, MAX_WS_FRAME_SIZE);
                        const chunk = new Uint8Array(lpm.buffer, offset, chunkSize);
                        this.ws.send(chunk);
                        offset += chunkSize;
                    }
                }

                if (holder.eos === true)
                    this.sendDone = true;
            }
        }

        TracTransport.prototype._encodeHeaders = function(headers) {

            let headerText = "";

            Object.keys(headers).forEach(key => {
                const value = headers[key];
                headerText += `${key}: ${value}\r\n`;
            });

            headerText += '\r\n';

            const encoder = new TextEncoder()
            return encoder.encode(headerText);
        }


        // -------------------------------------------------------------------------------------------------------------
        // MESSAGE RECEIVING
        // -------------------------------------------------------------------------------------------------------------


        TracTransport.prototype._receiveFrame = function(frame) {

            this.options.debug && console.log(`TracTransport _receiveFrame, bytes = [${frame.byteLength}]`)

            this._pushLpmQueue(new Uint8Array(frame), this.rcvQueue);

            let lpm = this._pollLpmQueue(this.rcvQueue);

            while (lpm !== null) {

                const holder = {lpm: null}
                this.rcvMsgQueue.push(holder);

                lpm.then(lpm_ => { holder.lpm = lpm_; this._receiveLpm() });

                lpm = this._pollLpmQueue(this.rcvQueue);
            }
        }

        TracTransport.prototype._receiveLpm = function() {

            this.options.debug && console.log(`TracTransport _receiveLpm, rcv msg queue length = [${this.rcvMsgQueue.length}]`)

            while (this.rcvMsgQueue.length > 0 &&
                   this.rcvMsgQueue[0].lpm !== null ) {

                const holder = this.rcvMsgQueue.shift();
                const lpm = holder.lpm;

                if (lpm.trailers)
                    this._receiveTrailers(lpm.message)
                else
                    this._receiveMessage(lpm.message);
            }
        }

        TracTransport.prototype._receiveTrailers = function (msg) {

            this.options.debug && console.log("TracTransport _receiveHeaders")

            const metadata = this._metadataFromLpmTrailers(msg);
            Object.assign(this.responseMetadata, metadata);

            // The message exchange is always complete when the grpc-status header / trailer is received
            if (GRPC_STATUS_HEADER in this.responseMetadata) {

                this.rcvDone = true;

                // Always shut down the channel before processing _handleComplete
                // But, also avoid duplicate close() requests, if it's already down that's fine

                if (this.ws.readyState === WebSocket.CLOSED)
                    this._handleComplete()
                else
                    this.ws.close(1000, "clean shutdown");
            }
        }

        TracTransport.prototype._receiveMessage = function(msg) {

            this.options.debug && console.log(`TracTransport _receiveMessage, bytes = [${msg.byteLength}]`)

            if (this.serverStreaming)
                this.callback(null, msg);
            else {
                this.response = msg;
            }
        }


        // -------------------------------------------------------------------------------------------------------------
        // RESULT HANDLING
        // -------------------------------------------------------------------------------------------------------------


        TracTransport.prototype._metadataFromHttpHeaders = function(headers) {

            this.options.debug && console.log("TracTransport _metadataFromHttpHeaders");

            if (headers === null)
                return {};

            const metadata = {};
            const keys = headers.keys();

            for (let key = keys.next(); key.value; key = keys.next()) {

                // Do not include HTTP/2 special headers (e.g. :status etc.)
                if (key.value.startsWith(":"))
                    continue;

                // Filter out some HTTP headers that should definitely not be included as metadata
                if (FILTER_RESPONSE_HEADERS.includes(key.value.toLowerCase()))
                    continue;

                metadata[key.value] = headers.get(key.value);
            }

            return metadata;
        }

        TracTransport.prototype._metadataFromLpmTrailers = function(message) {

            this.options.debug && console.log("TracTransport _metadataFromLpmTrailers");

            const metadata = {};

            const trailers = new TextDecoder()
                .decode(message)
                .trim()
                .split(/\r\n/);

            for (let i = 0; i < trailers.length; i++) {
                const trailer = trailers[i];
                const sep = trailer.indexOf(":");
                const key = trailer.substring(0, sep);
                const value = trailer.substring(sep + 1).trim();
                metadata[key] = decodeURI(value);
            }

            return metadata;
        }

        TracTransport.prototype._processResponseMetadata = function(metadata) {

            // This is a hook for post-processing after a successful call
            // E.g. to set cookies based on the response metadata

            // Currently no processing is required, all required cookies are set by the server
            // Seeing the metadata might be useful during debugging

            this.options.debug && console.log("TracTransport gRPC response headers")
            this.options.debug && console.log(JSON.stringify(metadata));
        }

        TracTransport.prototype._handleComplete = function() {

            this.options.debug && console.log("TracTransport _handleComplete")

            if (this.finished) {
                this.options.debug && console.log("_handleComplete called after the method already finished");
                return;
            }

            // Sometimes there is a response from gRPC even when the connection did not close cleanly
            // This happens if gRPC interrupts a stream to send an error response
            // The response from gRPC is usually more helpful than a generic "connection closed early"

            const grpcStatus = GRPC_STATUS_HEADER in this.responseMetadata ? this.responseMetadata[GRPC_STATUS_HEADER] : null;
            const grpcMessage = GRPC_MESSAGE_HEADER in this.responseMetadata ? this.responseMetadata[GRPC_MESSAGE_HEADER] : null;

            if (!this.sendDone || !this.rcvDone) {

                const code = grpcStatus || grpc.StatusCode.UNKNOWN;
                const message = grpcMessage || "The connection was closed before communication finished";
                const error = {code: code, message: message, metadata: this.responseMetadata};
                this.callback(error, null);
            }
            else if (grpcStatus === null) {

                const code = grpc.StatusCode.UNKNOWN;
                const message = "The connection was closed before a response was received";
                const error = {code: code, message: message, metadata: this.responseMetadata};
                this.callback(error, null);
            }
            else {

                const grpcStatus = Number.parseInt(this.responseMetadata[GRPC_STATUS_HEADER]);
                const grpcMessage = this.responseMetadata[GRPC_MESSAGE_HEADER];

                if (grpcStatus !== grpc.StatusCode.OK) {
                    const error = {code: grpcStatus, message: grpcMessage, metadata: this.responseMetadata}
                    this.callback(error, null);
                }

                else if (this.response === null && !this.serverStreaming) {
                    const code = grpc.StatusCode.UNKNOWN;
                    const message = "The reply from th server had no content and no error message";
                    const error = {code: code, message: message, metadata: this.responseMetadata}
                    this.callback(error, null);
                }

                // At this point everything checks out
                else {

                    // This is a hook for processing metadata after a successful call
                    this._processResponseMetadata(this.responseMetadata);

                    // Send the final result message
                    if (this.serverStreaming)
                        this.callback(null, null);
                    else
                        this.callback(null, this.response);
                }
            }

            this.finished = true;
        }

        TracTransport.prototype._handlerError = function (code, message) {

            this.options.debug && console.log("TracTransport _handlerError", code, message)

            if (this.finished) {
                this.options.debug && console.log("_handlerError called after the method already finished");
                return;
            }

            // Send a WS close, in case it has not already been done

            if (this.ws.readyState !== WebSocket.CLOSED)
                this.ws.close(1002, "close on error");

            // In an error condition make sure not to keep a reference to any data

            this.finished = true;
            this.sendQueue = []
            this.rcvQueue = []

            const error = { code: code, message: message }

            this.callback(error, null);
        }


        // -------------------------------------------------------------------------------------------------------------
        // LPM ENCODE / DECODE
        // -------------------------------------------------------------------------------------------------------------


        TracTransport.prototype._wrapLpm = function(msg, wsProtocol = false) {

            if (this.compressionEnabled && msg.byteLength > COMPRESSION_THRESHOLD) {

                const cs = new CompressionStream("gzip");
                const writer = cs.writable.getWriter();
                writer.write(msg);
                writer.close();
                const buffer = new Response(cs.readable).arrayBuffer();
                return buffer.then(buf => {
                    return this._wrapLpmInner(new Uint8Array(buf), true, wsProtocol)
                });
            }
            else {
                const lpm = this._wrapLpmInner(msg, false, wsProtocol);
                return Promise.resolve(lpm);
            }
        }

        TracTransport.prototype._wrapLpmInner = function(msg, compress, wsProtocol = false) {

            const wsEos = 0;
            const wsPrefix = wsProtocol ? 1 : 0;
            const flag = compress ? 1 : 0;
            const length = msg.byteLength;

            this.options.debug && console.log(`TracTransport _wrapLpm: ws = ${wsProtocol}, eos = ${wsEos}, compress = ${flag}, length = ${length}`)

            const lpm = new Uint8Array(msg.byteLength + LPM_PREFIX_LENGTH + wsPrefix)
            const lpmView = new DataView(lpm.buffer, 0, LPM_PREFIX_LENGTH + wsPrefix);

            if (wsProtocol)
                lpmView.setUint8(0, wsEos);

            lpmView.setUint8(0 + wsPrefix, flag);
            lpmView.setUint32(1 + wsPrefix, length, false);
            lpm.set(msg, LPM_PREFIX_LENGTH + wsPrefix)

            return lpm
        }

        TracTransport.prototype._pushLpmQueue = function(frame, queue) {

            this.options.debug && console.log(`TracTransport _pushLpmQueue, bytes = [${frame.byteLength}], queue length = [${queue.length}]`)

            queue.push(frame);
        }

        TracTransport.prototype._pollLpmQueue = function(queue) {

            this.options.debug && console.log(`TracTransport _pollLpmQueue, queue length = [${queue.length}]`)

            if (queue.length === 0)
                return null;

            const queueSize = queue
                .map(buf => buf.byteLength)
                .reduce((acc, x) => acc + x, 0);

            if (queueSize < LPM_PREFIX_LENGTH)
                return null;

            // Here we pull out the underlying buffer, so make sure to use the byte offset
            // Otherwise we'll get whatever is at index 0 in the physical buffer, which can be anything

            const prefix = this._pollByteQueue(LPM_PREFIX_LENGTH, queue);
            const prefixView = new DataView(prefix.buffer, prefix.byteOffset, prefix.byteLength);

            const lpmFlag = prefixView.getUint8(0);
            const lpmLength = prefixView.getUint32(1, false);

            // Whole LPM is not available yet, push the prefix back into the head of the queue
            if (queueSize - LPM_PREFIX_LENGTH < lpmLength) {
                queue.unshift(prefix);
                return null;
            }

            const msg = this._pollByteQueue(lpmLength, queue);

            const compress = lpmFlag & 1;
            const trailers = lpmFlag & (1 << 7);

            if (!compress) {
                return Promise.resolve(this._pollLpmQueueResult(msg, lpmLength, compress, trailers));
            }
            else if (this.compressionEnabled) {
                const ds = new DecompressionStream("gzip");
                const writer = ds.writable.getWriter();
                writer.write(msg);
                writer.close();
                const buffer = new Response(ds.readable).arrayBuffer();
                return buffer.then(buf => this._pollLpmQueueResult(new Uint8Array(buf), lpmLength, compress, trailers));
            }
            else {
                const err = "Invalid response from server (compressed message on an uncompressed channel";
                console.log(err);
                return Promise.reject(err);
            }
        }

        TracTransport.prototype._pollLpmQueueResult = function(message, lpmSize, compress, trailers) {

            this.options.debug && console.log(
                `TracTransport _pollLpmQueueResult, LPM length = [${lpmSize}], ` +
                `uncompressed = [${message.byteLength}] compress = [${compress}], trailers = [${trailers}]`)

            // Got a valid LPM - we need to return a structure to carry some information about it

            const lpmInfo = {};
            lpmInfo.length = message.byteLength;
            lpmInfo.trailers = trailers;
            lpmInfo.message = message;

            return lpmInfo;
        }

        TracTransport.prototype._pollMsgQueue = function() {


        }

        TracTransport.prototype._pollByteQueue = function(nBytes, queue) {

            this.options.debug && console.log(`TracTransport _pollByteQueue, nBytes = [${nBytes}], queue length = [${queue.length}]`)

            let frame0 = this._pollByteQueueUpto(nBytes, queue);

            if (frame0.byteLength === nBytes) {
                return frame0;
            }

            const buffer = new Uint8Array(nBytes);
            let offset = frame0.byteLength;
            buffer.set(frame0);

            while (offset < nBytes) {

                let frame = this._pollByteQueueUpto(nBytes - offset, queue);
                buffer.set(frame, offset);
                offset += frame.byteLength;
            }

            return buffer;
        }

        TracTransport.prototype._pollByteQueueUpto = function(nBytes, queue) {

            this.options.debug && console.log(`TracTransport _pollByteQueueUpto, nBytes = [${nBytes}], queue length = [${queue.length}]`)

            let frame = queue.shift();

            if (frame.byteLength <= nBytes) {
                return frame;
            }

            if (frame.byteLength > nBytes) {

                // Avoid physical copy on the underlying buffers
                // Now we have to be careful to use the byteOffset anywhere we reference the buffer directly

                const consumed = new Uint8Array(frame.buffer, frame.byteOffset, nBytes);
                const remaining = new Uint8Array(frame.buffer, frame.byteOffset + nBytes);

                queue.unshift(remaining);

                return consumed;
            }
        }


        return TracTransport;

    })();


    function createRpcImpl(serviceName, protocol, host, port, options) {

        const transport = options.transport || DEFAULT_TRANSPORT;
        let transportImpl;

        if (transport === "google") transportImpl = new GoogleTransport(serviceName, protocol, host, port, options);
        else if (transport === "trac") transportImpl = new TracTransport(serviceName, protocol, host, port, options);
        else throw new Error(`Unsupported option for transport: [${transport}]`);

        const rpcImpl = transportImpl.rpcImpl.bind(transportImpl);
        rpcImpl._transport = transportImpl;

        return rpcImpl;
    }


    $root.tracdap.setup = (function() {

        /**
         * Namespace setup.
         * @memberof tracdap
         * @namespace
         */
        const setup = {};


        // We need the following typedef for rpcImpl methods:
        // @typedef {typeof $protobuf.rpc.Service} tracdap.setup.ServiceType

        // The JSDoc generator for .d.ts files cannot handle type names with spaces
        // So, use a placeholder instead, the real type is substituted in by api_builder.js

        /**
         * Type declaration for gRPC service classes
         * @typedef {$SERVICE_TYPE} tracdap.setup.ServiceType
         */

        /**
         * Create an rpcImpl that connects to a specific target
         *
         * <p>Deprecated since version 0.5.6, use transportForTarget() instead</p>
         *
         * @function rpcImplForTarget
         * @memberof tracdap.setup
         *
         * @deprecated Since version 0.5.6, use transportForTarget() instead
         *
         * @param {ServiceType} serviceClass The service class to create an rpcImpl for
         * @param {string} protocol The protocol to use for connection (either "http" or "https")
         * @param {string} host The host to connect to
         * @param {number} port The port to connect to
         * @param {object=} options Options to control the behaviour of the transport
         * @param {("google"|"trac")} options.transport Controls which transport implementation to use for gRPC-Web
         * @param {boolean} options.debug Turn on debug logging
         * @return {$protobuf.RPCImpl} An rpcImpl function that can be used with the specified service class
         */
        setup.rpcImplForTarget = function(serviceClass, protocol, host, port, options = {}) {

            return setup.transportForTarget(serviceClass, protocol, host, port, options);
        }

        /**
         * Create an rpcImpl for use in a web browser, requests will be sent to the page origin server
         *
         * <p>Deprecated since version 0.5.6, use transportForBrowser() instead</p>
         *
         * @function rpcImplForBrowser
         * @memberof tracdap.setup
         *
         * @deprecated Since version 0.5.6, use transportForBrowser() instead<
         *
         * @param {ServiceType} serviceClass The service class to create an rpcImpl for
         * @param {object=} options Options to control the behaviour of the transport
         * @param {("google"|"trac")} options.transport Controls which transport implementation to use for gRPC-Web
         * @param {boolean} options.debug Turn on debug logging
         * @return {$protobuf.RPCImpl} An rpcImpl function that can be used with the specified service class
         */
        setup.rpcImplForBrowser = function(serviceClass, options = {}) {

            return setup.transportForBrowser(serviceClass, options);
        }

        /**
         * Create an rpcImpl that connects to a specific target
         *
         * @function transportForTarget
         * @memberof tracdap.setup
         *
         * @param {ServiceType} serviceClass The service class to create an rpcImpl for
         * @param {string} protocol The protocol to use for connection (either "http" or "https")
         * @param {string} host The host to connect to
         * @param {number} port The port to connect to
         * @param {object=} options Options to control the behaviour of the transport
         * @param {("google"|"trac")} options.transport Controls which transport implementation to use for gRPC-Web
         * @param {boolean} options.debug Turn on debug logging
         * @return {$protobuf.RPCImpl} An rpcImpl function that can be used with the specified service class
         */
        setup.transportForTarget = function(serviceClass, protocol, host, port, options = {}) {

            if (!serviceClass.hasOwnProperty("_serviceName"))
                throw new Error("Service class must specify gRPC service in _serviceName (this is a bug)")

            return createRpcImpl(serviceClass._serviceName, protocol, host, port, options);
        }

        /**
         * Create an rpcImpl for use in a web browser, requests will be sent to the page origin server
         *
         * @function transportForBrowser
         * @memberof tracdap.setup
         *
         * @param {ServiceType} serviceClass The service class to create an rpcImpl for
         * @param {object=} options Options to control the behaviour of the transport
         * @param {("google"|"trac")} options.transport Controls which transport implementation to use for gRPC-Web
         * @param {boolean} options.debug Turn on debug logging
         * @return {$protobuf.RPCImpl} An rpcImpl function that can be used with the specified service class
         */
        setup.transportForBrowser = function(serviceClass, options = {}) {

            if (!serviceClass.hasOwnProperty("_serviceName"))
                throw new Error("Service class must specify gRPC service in _serviceName (this is a bug)")

            const protocol = window.location.protocol.replace(":", "");  // Remove trailing colon
            let host, port;

            const hostAndPort = window.location.host;
            const separator = hostAndPort.indexOf(":");

            if (separator < 0) {
                host = hostAndPort;
                port = protocol === "https" ? 443 : 80;
            }
            else {
                host = hostAndPort.substring(0, separator);
                port = hostAndPort.substring(separator + 1);
            }

            const browserOptions = {}
            browserOptions["browser"] = true;
            Object.assign(browserOptions, options);

            return createRpcImpl(serviceClass._serviceName, protocol, host, port, browserOptions);
        }

        /**
         * Construct a streaming client suitable for transferring large datasets or files
         *
         * For ordinary RPC calls you only need one client per service and you can call it as
         * many times as you like. The same is not true for streaming -to use a stream you
         * must create a unique client instance for each call. This is to stop stream events
         * from different calls getting mixed up on the same stream.
         *
         * You can use this method to get a stream instance from an existing client.
         *
         * @function newStream
         * @memberOf tracdap.setup
         *
         * @template TService extends $protobuf.rpc.Service
         * @param {TService} service An existing client that will be used to construct the stream
         * @return {TService} A streaming client instance, ready to call
         */
        setup.newStream = function(service) {

            const serviceClass = service.constructor;

            const originalTransport = service.rpcImpl._transport;
            const protocol = originalTransport.protocol;
            const host = originalTransport.host;
            const port = originalTransport.port;
            const options = originalTransport.options;

            const rpcImpl = createRpcImpl(serviceClass._serviceName, protocol, host, port, options);

            return new serviceClass(rpcImpl);
        }

        return setup;

    })();

    $root.tracdap.utils = (function() {

        /**
         * Namespace utils.
         * @memberof tracdap
         * @namespace
         */
        const utils = {};

        /**
         * Create an event source from a JavaScript ReadableStream object
         *
         * @function streamToEmitter
         * @memberof tracdap.utils
         *
         * @param {ReadableStream} stream A JavaScript ReadableStream object
         * @return {$protobuf.util.EventEmitter} An event emitter for the supplied stream
         */
        utils.streamToEmitter = function(stream) {

            const emitter = new $protobuf.util.EventEmitter();

            const writer = new WritableStream({

                write(chunk) {
                    emitter.emit("data", chunk);
                },

                close() {
                    emitter.emit("end");
                    emitter.emit("close");
                },

                abort(error) {
                    emitter.emit("error", error);
                    emitter.emit("close");
                }
            });

            stream.pipeTo(writer)
                .then(_ => writer.close())
                .catch(err => writer.abort(err));

            return emitter;
        }

        /**
         * Aggregate the content from a list of messages returned by a streaming data or file download
         *
         * @function aggregateStreamContent
         * @memberof tracdap.utils
         *
         * @template TMessage extends $protobuf.Message
         * @param {TMessage[]} messages A list of the messages with content to be aggregated
         * @return {TMessage} A single aggregated message, with the content of the entire download stream
         */
        utils.aggregateStreamContent = function (messages) {

            if (messages === null || messages.length === 0)
                throw new Error("No response was received")

            let size = 0;

            for (let i = 1; i < messages.length; i++)
                size += messages[i].content.byteLength;

            const content = new Uint8Array(size);
            let offset = 0;

            for (let i = 1; i < messages.length; i++) {
                const msg = messages[i];
                content.set(msg.content, offset);
                offset += msg.content.byteLength;
            }

            const response = messages[0];
            response.content = content;

            return response;
        }

        /**
         * Get TRAC error details after an error in a TRAC API call
         *
         * If error details have been sent back from the platform, those details will be returned.
         * Otherwise, a basic set of details will be created for the error.
         *
         * @function getErrorDetails
         * @memberof tracdap.utils
         *
         * @param {Error} error An error returned from a TRAC API call
         * @returns {tracdap.api.TracErrorDetails} Detailed information about the given error
         */
        utils.getErrorDetails = function(error) {

            if (error.hasOwnProperty("metadata")) {
                if (error.metadata !== null && TRAC_ERROR_DETAILS_KEY in error.metadata) {

                    try {
                        const detailsBase64 = error.metadata[TRAC_ERROR_DETAILS_KEY];
                        const detailsProto = _decodeBase64(detailsBase64);

                        return tracdap.api.TracErrorDetails.decode(detailsProto)
                    }
                    catch (e) {
                        console.warn("Error details could not be decoded, only basic details will be available");
                        console.warn(e);
                    }
                }
            }

            if (error.hasOwnProperty("code"))
                return tracdap.api.TracErrorDetails.create({code: error.code, message: error.message});

            return tracdap.api.TracErrorDetails.create({code: grpc.StatusCode.UNKNOWN, message: error.message});
        }

        function _decodeBase64(base64) {

            // TODO: Universal alternative for base64 decoding
            const str = atob(base64)
            const buffer = new Uint8Array(str.length);

            for (let i = 0; i < str.length; i++) {
                buffer[i] = str.charCodeAt(i);
            }

            return buffer;
        }

        return utils;

    })();

    const api_mapping = $API_MAPPING;

    $root.tracdap = {
        ...$root.tracdap,
        ...api_mapping
    };

    return $root;
});