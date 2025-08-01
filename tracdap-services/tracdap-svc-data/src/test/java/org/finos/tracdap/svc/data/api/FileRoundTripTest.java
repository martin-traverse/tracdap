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

package org.finos.tracdap.svc.data.api;

import org.finos.tracdap.api.*;
import org.finos.tracdap.common.data.DataContext;
import org.finos.tracdap.common.async.Flows;
import org.finos.tracdap.common.async.Futures;
import org.finos.tracdap.common.data.IExecutionContext;
import org.finos.tracdap.metadata.CopyStatus;
import org.finos.tracdap.metadata.ObjectDefinition;
import org.finos.tracdap.metadata.ObjectType;
import org.finos.tracdap.metadata.TagSelector;
import org.finos.tracdap.svc.admin.TracAdminService;
import org.finos.tracdap.svc.data.TracDataService;
import org.finos.tracdap.svc.meta.TracMetadataService;
import org.finos.tracdap.test.data.DataApiTestHelpers;
import org.finos.tracdap.test.helpers.PlatformTest;

import com.google.common.collect.Streams;
import com.google.protobuf.ByteString;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.apache.arrow.memory.RootAllocator;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.finos.tracdap.common.metadata.MetadataUtil.selectorFor;
import static org.finos.tracdap.test.concurrent.ConcurrentTestHelpers.resultOf;
import static org.finos.tracdap.test.concurrent.ConcurrentTestHelpers.waitFor;


abstract class FileRoundTripTest  {

    public static final String TRAC_CONFIG_UNIT = "config/trac-unit.yaml";
    public static final String TRAC_TENANTS_UNIT = "config/trac-unit-tenants.yaml";
    public static final String TRAC_CONFIG_ENV_VAR = "TRAC_CONFIG_FILE";
    public static final String TRAC_TENANTS_ENV_VAR = "TRAC_TENANTS_FILE";
    public static final String TEST_TENANT = "ACME_CORP";
    public static final Duration TEST_TIMEOUT = Duration.ofSeconds(20);

    protected static EventLoopGroup elg;
    protected IExecutionContext execContext;
    protected TracMetadataApiGrpc.TracMetadataApiFutureStub metaClient;
    protected TracDataApiGrpc.TracDataApiStub dataClient;


    // Include this test case as a unit test
    static class UnitTest extends FileRoundTripTest {

        @RegisterExtension
        public static final PlatformTest platform = PlatformTest.forConfig(TRAC_CONFIG_UNIT, List.of(TRAC_TENANTS_UNIT))
                .runDbDeploy(true)
                .addTenant(TEST_TENANT)
                .startService(TracMetadataService.class)
                .startService(TracDataService.class)
                .startService(TracAdminService.class)
                .build();

        @BeforeAll
        static void setupClass() {
            elg = new NioEventLoopGroup(2);
        }

        @BeforeEach
        void setup() {
            execContext = new DataContext(elg.next(), new RootAllocator());
            metaClient = platform.metaClientFuture();
            dataClient = platform.dataClient();
        }
    }

    // Include this test case for integration against different storage backends
    @Tag("integration")
    @Tag("int-storage")
    @Tag("all-platforms")
    static class IntegrationTest extends FileRoundTripTest {

        // Slow unit tests count as integration, so fall back to using the unit test config
        private static final String TRAC_CONFIG_FILE = System.getenv(TRAC_CONFIG_ENV_VAR) != null
                ? System.getenv(TRAC_CONFIG_ENV_VAR)
                : TRAC_CONFIG_UNIT;

        private static final String TRAC_TENANTS_FILE = System.getenv(TRAC_TENANTS_ENV_VAR) != null
                ? System.getenv(TRAC_TENANTS_ENV_VAR)
                : TRAC_TENANTS_UNIT;

        @RegisterExtension
        public static final PlatformTest platform = PlatformTest.forConfig(TRAC_CONFIG_FILE, List.of(TRAC_TENANTS_FILE))
                .runDbDeploy(true)
                .addTenant(TEST_TENANT)
                .manageDataPrefix(true)
                .startService(TracAdminService.class)
                .startService(TracMetadataService.class)
                .startService(TracDataService.class)
                .build();

        @BeforeAll
        static void setupClass() {
            elg = new NioEventLoopGroup(2);
        }

        @AfterAll
        static void tearDownClass() {
            elg.shutdownGracefully();
        }

        @BeforeEach
        void setup() {
            execContext = new DataContext(elg.next(), new RootAllocator());
            metaClient = platform.metaClientFuture();
            dataClient = platform.dataClient();
        }
    }


    @Test
    void testRoundTrip_basic() throws Exception {

        var random = new Random();
        var content = List.of(
                new byte[4096],
                new byte[4096],
                new byte[4096]);

        for (var buf: content)
            random.nextBytes(buf);

        roundTripTest(content, true);
        roundTripTest(content, false);
    }

    @Test
    void testRoundTrip_heterogeneousChunks() throws Exception {

        var random = new Random();
        var content = List.of(
                new byte[3],
                new byte[10000],
                new byte[42],
                new byte[4097],
                new byte[1],
                new byte[2000]);

        for (var buf: content)
            random.nextBytes(buf);

        roundTripTest(content, true);
        roundTripTest(content, false);
    }

    @Test
    void testRoundTrip_megabyteChunk() throws Exception {

        var content = List.of(new byte[1024 * 1024]);

        var random = new Random();
        random.nextBytes(content.get(0));

        roundTripTest(content, true);
        roundTripTest(content, false);
    }

    @Test
    void testRoundTrip_lageFile() throws Exception {

        var content = new ArrayList<byte[]>();
        var random = new Random();

        for (var i = 0; i < 500; i++) {
            var chunk = new byte[4096];
            random.nextBytes(chunk);
            content.add(chunk);
        }

        roundTripTest(content, true);
        roundTripTest(content, false);
    }

    @Test
    void testRoundTrip_singleByte() throws Exception {

        var content = List.of(new byte[1]);
        content.get(0)[0] = 0;

        roundTripTest(content, true);
        roundTripTest(content, false);
    }

    @Test
    void testRoundTrip_smallTextFile() throws Exception {

        var contentText = "Hello world!\n";
        var contentBytes = contentText.getBytes(StandardCharsets.UTF_8);
        var content = List.of(contentBytes);

        roundTripTest(content, true);
        roundTripTest(content, false);
    }

    @RepeatedTest(100)
    void rapidFireTest() throws Exception {

        testRoundTrip_heterogeneousChunks();
    }

    private void roundTripTest(List<byte[]> content, boolean dataInChunkZero) throws Exception {

        // Set up a request stream and client streaming call, wait for the call to complete

        var createFileRequest = fileWriteRequest(content, dataInChunkZero);
        var createFile = DataApiTestHelpers.clientStreaming(dataClient::createFile, createFileRequest);

        waitFor(TEST_TIMEOUT, createFile);
        var objHeader = resultOf(createFile);

        // Fetch metadata for the file and storage objects that should be created

        var fileDef = fetchDefinition(selectorFor(objHeader), ObjectDefinition::getFile);
        var storageDef = fetchDefinition(fileDef.getStorageId(), ObjectDefinition::getStorage);

        var dataItem = fileDef.getDataItem();
        var storageItem = storageDef.getDataItemsOrThrow(dataItem);
        var incarnation = storageItem.getIncarnations(0);
        var copy = incarnation.getCopies(0);

        var expectedSize = content.stream()
                .mapToLong(bs -> bs.length)
                .sum();

        Assertions.assertEquals(ObjectType.FILE, objHeader.getObjectType());
        Assertions.assertEquals(1, objHeader.getObjectVersion());
        Assertions.assertEquals(1, objHeader.getTagVersion());

        Assertions.assertEquals("test_file.dat", fileDef.getName());
        Assertions.assertEquals("dat", fileDef.getExtension());
        Assertions.assertEquals("application/octet-stream", fileDef.getMimeType());
        Assertions.assertEquals(expectedSize, fileDef.getSize());
        Assertions.assertEquals(CopyStatus.COPY_AVAILABLE, copy.getCopyStatus());

        var originalBytes = ByteString.copyFrom(
            content.stream()
            .map(ByteString::copyFrom)
            .collect(Collectors.toList()));

        // Set up a server-streaming request to read the file back

        var readRequest = FileReadRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setSelector(selectorFor(objHeader))
                .build();

        var readResponse = Flows.<FileReadResponse>hub(execContext.eventLoopExecutor());
        var readResponse0 = Flows.first(readResponse);
        var readByteStream = Flows.map(readResponse, FileReadResponse::getContent);
        var readBytes = Flows.fold(readByteStream, ByteString::concat, ByteString.EMPTY);

        DataApiTestHelpers.serverStreaming(dataClient::readFile, readRequest, readResponse);

        waitFor(TEST_TIMEOUT, readResponse0, readBytes);
        var roundTripDef = resultOf(readResponse0).getFileDefinition();
        var roundTripBytes = resultOf(readBytes);

        Assertions.assertEquals("test_file.dat", roundTripDef.getName());
        Assertions.assertEquals("dat", roundTripDef.getExtension());
        Assertions.assertEquals("application/octet-stream", roundTripDef.getMimeType());
        Assertions.assertEquals(expectedSize, roundTripDef.getSize());

        Assertions.assertEquals(originalBytes, roundTripBytes);
    }

    private Flow.Publisher<FileWriteRequest>
    fileWriteRequest(List<byte[]> content, boolean dataInChunkZero) {

        var chunkZeroBytes = dataInChunkZero
                ? ByteString.copyFrom(content.get(0))
                : ByteString.EMPTY;

        var requestZero = FileWriteRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setName("test_file.dat")
                .setMimeType("application/octet-stream")
                .setContent(chunkZeroBytes)
                .build();

        var remainingContent = dataInChunkZero
                ? content.subList(1, content.size())
                : content;

        var requestStream = remainingContent.stream().map(bytes ->
                FileWriteRequest.newBuilder()
                .setContent(ByteString.copyFrom(bytes))
                .build());

        return Flows.publish(Streams.concat(
                Stream.of(requestZero),
                requestStream));
    }

    private <TDef>
    TDef fetchDefinition(
            TagSelector selector,
            Function<ObjectDefinition, TDef> defTypeFunc)
            throws Exception {

        var tagGrpc = metaClient.readObject(MetadataReadRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setSelector(selector)
                .build());

        var tag = Futures.javaFuture(tagGrpc);

        waitFor(TEST_TIMEOUT, tag);

        var objDef = resultOf(tag).getDefinition();

        return defTypeFunc.apply(objDef);
    }
}
