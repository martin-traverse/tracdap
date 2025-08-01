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

package org.finos.tracdap.svc.orch.jobs;

import org.finos.tracdap.api.*;
import org.finos.tracdap.common.metadata.MetadataCodec;
import org.finos.tracdap.common.metadata.MetadataUtil;
import org.finos.tracdap.metadata.*;
import org.finos.tracdap.metadata.ImportModelJob;
import org.finos.tracdap.metadata.RunFlowJob;
import org.finos.tracdap.svc.admin.TracAdminService;
import org.finos.tracdap.svc.data.TracDataService;
import org.finos.tracdap.svc.meta.TracMetadataService;
import org.finos.tracdap.svc.orch.TracOrchestratorService;
import org.finos.tracdap.test.helpers.GitHelpers;
import org.finos.tracdap.test.helpers.PlatformTest;

import com.google.protobuf.ByteString;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.finos.tracdap.svc.orch.jobs.Helpers.runJob;


@Tag("integration")
@Tag("int-e2e")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RunFlowTest {

    // Run flow is a more realistic test of production processes
    // This test uses external schema files for model inputs

    private static final String TEST_TENANT = "ACME_CORP";
    private static final String E2E_CONFIG = "config/trac-e2e.yaml";
    private static final String E2E_TENANTS = "config/trac-e2e-tenants.yaml";

    private static final String LOANS_INPUT_PATH = "examples/models/python/data/inputs/loan_final313_100_shortform.csv";
    private static final String CURRENCY_INPUT_PATH = "examples/models/python/data/inputs/currency_data_sample.csv";
    private static final String ACCOUNT_FILTER_INPUT_PATH = "examples/models/python/data/inputs/account_filter.csv";
    private static final String STRUCT_INPUT_PATH = "examples/models/python/data/inputs/structured_run_config.json";
    public static final String ANALYSIS_TYPE = "analysis_type";

    // Only test E2E run model using the local repo
    // E2E model loading with different repo types is tested in ImportModelTest
    // We don't need to test all combinations of model run from different repo types

    protected String useTracRepo() { return "TRAC_LOCAL_REPO"; }


    @RegisterExtension
    public static final PlatformTest platform = PlatformTest.forConfig(E2E_CONFIG, List.of(E2E_TENANTS))
            .runDbDeploy(true)
            .runCacheDeploy(true)
            .addTenant(TEST_TENANT)
            .prepareLocalExecutor(true)
            .startService(TracMetadataService.class)
            .startService(TracDataService.class)
            .startService(TracOrchestratorService.class)
            .startService(TracAdminService.class)
            .build();

    private final Logger log = LoggerFactory.getLogger(getClass());

    static TagHeader flowId;
    static TagHeader loansSchemaId;
    static TagHeader loansDataId;
    static TagHeader currencySchemaId;
    static TagHeader currencyDataId;
    static TagHeader model1Id;
    static TagHeader model2Id;
    static TagHeader jobId;
    static TagHeader outputDataId;

    static TagHeader flowId2;
    static TagHeader accountFilterSchemaId;
    static TagHeader accountFilterDataId;
    static TagHeader dynamicFilterModelId;
    static TagHeader pnlAggregationModelId;
    static TagHeader profitByRegionDataId;
    static TagHeader exclusionsDataId;

    static TagHeader structFlowId;
    static TagHeader structModelId;
    static SchemaDefinition structModelInputSchema;
    static TagHeader structInputDataId;
    static TagHeader structOutputDataId;

    @Test @Order(1)
    void createFlow() {

        var metaClient = platform.metaClientBlocking();

        var pi_value = Value.newBuilder()
                .setType(
                        TypeDescriptor.newBuilder()
                                .setBasicType(BasicType.FLOAT)
                                .build()
                )
                .setFloatValue(3.14)
                .build();

        var flowDef = FlowDefinition.newBuilder()
                .putNodes("customer_loans", FlowNode.newBuilder().setNodeType(FlowNodeType.INPUT_NODE).build())
                .putNodes("currency_data", FlowNode.newBuilder().setNodeType(FlowNodeType.INPUT_NODE).build())
                .putNodes("model_1", FlowNode.newBuilder()
                        .setNodeType(FlowNodeType.MODEL_NODE)
                        .addInputs("customer_loans")
                        .addInputs("currency_data")
                        .addOutputs("preprocessed_data")
                        .build())
                .putNodes("model_2", FlowNode.newBuilder()
                        .setNodeType(FlowNodeType.MODEL_NODE)
                        .addInputs("preprocessed_data")
                        .addOutputs("profit_by_region")
                        .build())
                .putNodes("profit_by_region", FlowNode.newBuilder()
                        .setNodeType(FlowNodeType.OUTPUT_NODE)
                        .addAllNodeAttrs(List.of(
                                TagUpdate.newBuilder()
                                        .setAttrName(ANALYSIS_TYPE)
                                        .setValue(pi_value)
                                        .build()
                        ))
                        .build())
                .putNodes("preprocessed_data", FlowNode.newBuilder()
                        .setNodeType(FlowNodeType.OUTPUT_NODE)
                        .build())
                .addEdges(FlowEdge.newBuilder()
                        .setSource(FlowSocket.newBuilder().setNode("customer_loans"))
                        .setTarget(FlowSocket.newBuilder().setNode("model_1").setSocket("customer_loans")))
                .addEdges(FlowEdge.newBuilder()
                        .setSource(FlowSocket.newBuilder().setNode("currency_data"))
                        .setTarget(FlowSocket.newBuilder().setNode("model_1").setSocket("currency_data")))
                .addEdges(FlowEdge.newBuilder()
                        .setSource(FlowSocket.newBuilder().setNode("model_1").setSocket("preprocessed_data"))
                        .setTarget(FlowSocket.newBuilder().setNode("model_2").setSocket("preprocessed_data")))
                .addEdges(FlowEdge.newBuilder()
                        .setSource(FlowSocket.newBuilder().setNode("model_2").setSocket("profit_by_region"))
                        .setTarget(FlowSocket.newBuilder().setNode("profit_by_region")))
                .addEdges(FlowEdge.newBuilder()
                        .setSource(FlowSocket.newBuilder().setNode("model_1").setSocket("preprocessed_data"))
                        .setTarget(FlowSocket.newBuilder().setNode("preprocessed_data")))
                .build();

        var createReq = MetadataWriteRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setObjectType(ObjectType.FLOW)
                .setDefinition(ObjectDefinition.newBuilder().setObjectType(ObjectType.FLOW).setFlow(flowDef))
                .build();

        flowId = metaClient.createObject(createReq);
    }

    @Test @Order(2)
    void createSchemas() {

        var loansSchema = ObjectDefinition.newBuilder()
                .setObjectType(ObjectType.SCHEMA)
                .setSchema(SchemaDefinition.newBuilder()
                .setSchemaType(SchemaType.TABLE)
                .setTable(TableSchema.newBuilder()
                        .addFields(FieldSchema.newBuilder()
                                .setFieldName("id")
                                .setFieldType(BasicType.STRING)
                                .setBusinessKey(true)
                                .setLabel("Customer Id"))
                        .addFields(FieldSchema.newBuilder()
                                .setFieldName("loan_amount")
                                .setFieldType(BasicType.FLOAT)
                                .setLabel("Loan amount"))
                        .addFields(FieldSchema.newBuilder()
                                .setFieldName("loan_condition_cat")
                                .setFieldType(BasicType.INTEGER)
                                .setLabel("Loan condition category code"))
                        .addFields(FieldSchema.newBuilder()
                                .setFieldName("total_pymnt")
                                .setFieldType(BasicType.FLOAT)
                                .setLabel("Total payment received to date"))
                        .addFields(FieldSchema.newBuilder()
                                .setFieldName("region")
                                .setFieldType(BasicType.STRING)
                                .setCategorical(true)
                                .setLabel("Customer region"))))
                .build();

        var currencySchema = ObjectDefinition.newBuilder()
                .setObjectType(ObjectType.SCHEMA)
                .setSchema(SchemaDefinition.newBuilder()
                .setSchemaType(SchemaType.TABLE)
                .setTable(TableSchema.newBuilder()
                        .addFields(FieldSchema.newBuilder()
                                .setFieldName("ccy_code")
                                .setFieldType(BasicType.STRING)
                                .setCategorical(true))
                        .addFields(FieldSchema.newBuilder()
                                .setFieldName("spot_date")
                                .setFieldType(BasicType.DATE))
                        .addFields(FieldSchema.newBuilder()
                                .setFieldName("dollar_rate")
                                .setFieldType(BasicType.FLOAT))))
                .build();

        var loansSchemaAttrs = List.of(TagUpdate.newBuilder()
                .setAttrName("e2e_test_schema")
                .setValue(MetadataCodec.encodeValue("run_flow:customer_loans"))
                .build());

        var currencySchemaAttrs = List.of(TagUpdate.newBuilder()
                .setAttrName("e2e_test_schema")
                .setValue(MetadataCodec.encodeValue("run_flow:currency_data"))
                .build());

        loansSchemaId = createSchema(loansSchema, loansSchemaAttrs);
        currencySchemaId = createSchema(currencySchema, currencySchemaAttrs);
    }

    TagHeader createSchema(ObjectDefinition schema, List<TagUpdate> attrs) {

        var metaClient = platform.metaClientBlocking();

        var writeRequest = MetadataWriteRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setObjectType(ObjectType.SCHEMA)
                .setDefinition(schema)
                .addAllTagUpdates(attrs)
                .build();

        return metaClient.createObject(writeRequest);
    }

    @Test @Order(3)
    void loadInputData() throws Exception {

        log.info("Loading input data...");

        var loansAttrs = List.of(TagUpdate.newBuilder()
                .setAttrName("e2e_test_dataset")
                .setValue(MetadataCodec.encodeValue("run_flow:customer_loans"))
                .build());

        var currencyAttrs = List.of(TagUpdate.newBuilder()
                .setAttrName("e2e_test_dataset")
                .setValue(MetadataCodec.encodeValue("run_flow:currency_data"))
                .build());

        loansDataId = loadDataset(loansSchemaId, LOANS_INPUT_PATH, loansAttrs);
        currencyDataId = loadDataset(currencySchemaId, CURRENCY_INPUT_PATH, currencyAttrs);
    }

    TagHeader loadDataset(TagHeader schemaId, String dataPath, List<TagUpdate> attrs) throws Exception {

        var dataClient = platform.dataClientBlocking();

        var inputPath = platform.tracRepoDir().resolve(dataPath);
        var inputBytes = Files.readAllBytes(inputPath);

        var writeRequest = DataWriteRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setSchemaId(MetadataUtil.selectorFor(schemaId))
                .setFormat("text/csv")
                .setContent(ByteString.copyFrom(inputBytes))
                .addAllTagUpdates(attrs)
                .build();

        return dataClient.createSmallDataset(writeRequest);
    }

    @Test @Order(4)
    void importModels() {

        log.info("Running IMPORT_MODEL job...");

        var modelsPath = "examples/models/python/src";
        var modelsVersion = "main";

        var model1EntryPoint = "tutorial.model_1.FirstModel";
        var model1Attrs = List.of(TagUpdate.newBuilder()
                .setAttrName("e2e_test_model")
                .setValue(MetadataCodec.encodeValue("run_flow:chaining:model_1"))
                .build());
        var model1JobAttrs = List.of(TagUpdate.newBuilder()
                .setAttrName("e2e_test_job")
                .setValue(MetadataCodec.encodeValue("run_flow:import_model:model_1"))
                .build());

        var model2EntryPoint = "tutorial.model_2.SecondModel";
        var model2Attrs = List.of(TagUpdate.newBuilder()
                .setAttrName("e2e_test_model")
                .setValue(MetadataCodec.encodeValue("run_flow:chaining:model_2"))
                .build());
        var model2JobAttrs = List.of(TagUpdate.newBuilder()
                .setAttrName("e2e_test_job")
                .setValue(MetadataCodec.encodeValue("run_flow:import_model:model_2"))
                .build());

        model1Id = importModel(
                modelsPath, model1EntryPoint, modelsVersion,
                model1Attrs, model1JobAttrs);

        model2Id = importModel(
                modelsPath, model2EntryPoint, modelsVersion,
                model2Attrs, model2JobAttrs);
    }

    private TagHeader importModel(
            String path, String entryPoint, String version,
            List<TagUpdate> modelAttrs, List<TagUpdate> jobAttrs) {

        var metaClient = platform.metaClientBlocking();
        var orchClient = platform.orchClientBlocking();

        var importModel = ImportModelJob.newBuilder()
                .setLanguage("python")
                .setRepository(useTracRepo())
                .setPath(path)
                .setEntryPoint(entryPoint)
                .setVersion(version)
                .addAllModelAttrs(modelAttrs)
                .build();

        var jobRequest = JobRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setJob(JobDefinition.newBuilder()
                        .setJobType(JobType.IMPORT_MODEL)
                        .setImportModel(importModel))
                .addAllJobAttrs(jobAttrs)
                .build();

        var jobStatus = runJob(orchClient, jobRequest);
        var jobKey = MetadataUtil.objectKey(jobStatus.getJobId());

        Assertions.assertEquals(JobStatusCode.SUCCEEDED, jobStatus.getStatusCode());

        var modelSearch = MetadataSearchRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setSearchParams(SearchParameters.newBuilder()
                .setObjectType(ObjectType.MODEL)
                .setSearch(SearchExpression.newBuilder()
                .setTerm(SearchTerm.newBuilder()
                        .setAttrName("trac_create_job")
                        .setAttrType(BasicType.STRING)
                        .setOperator(SearchOperator.EQ)
                        .setSearchValue(MetadataCodec.encodeValue(jobKey)))))
                .build();

        var modelSearchResult = metaClient.search(modelSearch);

        Assertions.assertEquals(1, modelSearchResult.getSearchResultCount());

        return modelSearchResult.getSearchResult(0).getHeader();
    }

    @Test @Order(5)
    void runFlow() {

        var metaClient = platform.metaClientBlocking();
        var orchClient = platform.orchClientBlocking();

        var runFlow = RunFlowJob.newBuilder()
                .setFlow(MetadataUtil.selectorFor(flowId))
                .putParameters("param_1", MetadataCodec.encodeValue(2))
                .putParameters("param_2", MetadataCodec.encodeValue(LocalDate.now()))
                .putParameters("param_3", MetadataCodec.encodeValue(1.2345))
                .putInputs("customer_loans", MetadataUtil.selectorFor(loansDataId))
                .putInputs("currency_data", MetadataUtil.selectorFor(currencyDataId))
                .putModels("model_1", MetadataUtil.selectorFor(model1Id))
                .putModels("model_2", MetadataUtil.selectorFor(model2Id))
                .addOutputAttrs(TagUpdate.newBuilder()
                        .setAttrName("e2e_test_data")
                        .setValue(MetadataCodec.encodeValue("run_flow:data_output")))
                .build();

        var jobRequest = JobRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setJob(JobDefinition.newBuilder()
                        .setJobType(JobType.RUN_FLOW)
                        .setRunFlow(runFlow))
                .addJobAttrs(TagUpdate.newBuilder()
                        .setAttrName("e2e_test_job")
                        .setValue(MetadataCodec.encodeValue("run_flow:run_flow")))
                .build();

        var jobStatus = runJob(orchClient, jobRequest);

        jobId = jobStatus.getJobId();

        Assertions.assertEquals(JobStatusCode.SUCCEEDED, jobStatus.getStatusCode());

        var dataSearch = MetadataSearchRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setSearchParams(SearchParameters.newBuilder()
                .setObjectType(ObjectType.DATA)
                .setSearch(SearchExpression.newBuilder()
                .setTerm(SearchTerm.newBuilder()
                        .setAttrName("trac_create_job")
                        .setAttrType(BasicType.STRING)
                        .setOperator(SearchOperator.EQ)
                        .setSearchValue(MetadataCodec.encodeValue(MetadataUtil.objectKey(jobId))))))
                .build();

        var dataSearchResult = metaClient.search(dataSearch);

        Assertions.assertEquals(2, dataSearchResult.getSearchResultCount());

        var indexedResult = dataSearchResult.getSearchResultList().stream()
                .collect(Collectors.toMap(RunFlowTest::getJobOutput, Function.identity()));

        var profitByRegionAnalysisType = indexedResult.get("profit_by_region")
                .getAttrsOrThrow(ANALYSIS_TYPE)
                .getFloatValue();
        Assertions.assertEquals(3.14, profitByRegionAnalysisType);

        Assertions.assertFalse(
            indexedResult.get("preprocessed_data")
                    .getAttrsMap().containsKey(ANALYSIS_TYPE)
        );

        var searchResult = indexedResult.get("profit_by_region");
        var dataReq = MetadataReadRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setSelector(MetadataUtil.selectorFor(searchResult.getHeader()))
                .build();

        var dataTag = metaClient.readObject(dataReq);
        var dataDef = dataTag.getDefinition().getData();
        var outputAttr = dataTag.getAttrsOrThrow("e2e_test_data");
        var fieldCountAttr = dataTag.getAttrsOrThrow("trac_schema_field_count");
        var rowCountAttr = dataTag.getAttrsOrThrow("trac_data_row_count");

        Assertions.assertEquals("run_flow:data_output", MetadataCodec.decodeStringValue(outputAttr));
        Assertions.assertTrue(MetadataCodec.decodeIntegerValue(fieldCountAttr) > 0);
        Assertions.assertTrue(MetadataCodec.decodeIntegerValue(rowCountAttr) > 0);
        Assertions.assertEquals(1, dataDef.getPartsCount());

        outputDataId = dataTag.getHeader();
    }

    private static String getJobOutput(org.finos.tracdap.metadata.Tag t) {
        var attr = t.getAttrsOrThrow("trac_job_output");
        return attr.getStringValue();
    }

    @Test @Order(6)
    void checkResultAndLogFile() {

        var metaClient = platform.metaClientBlocking();
        var dataClient = platform.dataClientBlocking();

        var jobDef = metaClient.readObject(MetadataReadRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setSelector(MetadataUtil.selectorFor(jobId))
                .build())
                .getDefinition().getJob();

        var resultId = jobDef.getResultId();
        var resultDef = metaClient.readObject(MetadataReadRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setSelector(resultId)
                .build())
                .getDefinition().getResult();

        var logFileId = resultDef.getLogFileId();
        var logFileDef = metaClient.readObject(MetadataReadRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setSelector(logFileId)
                .build())
                .getDefinition().getFile();

        Assertions.assertEquals(jobId.getObjectId(), resultDef.getJobId().getObjectId());
        Assertions.assertEquals(JobStatusCode.SUCCEEDED, resultDef.getStatusCode());
        Assertions.assertTrue(logFileDef.getSize() > 0);

        var logFileContent = dataClient.readSmallFile(FileReadRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setSelector(logFileId)
                .build())
                .getContent()
                .toString(StandardCharsets.UTF_8);

        System.out.println(logFileContent);

        // Some things that are expected in the log
        // Might need to be updated if there are logging changes in the runtime
        Assertions.assertTrue(logFileContent.contains(MetadataUtil.objectKey(jobId)));
        Assertions.assertTrue(logFileContent.contains("START RunModel"));

        // This should be the last line in the job log, it is the last message from the job processor
        // Higher level messages (from the engine and runtime classes) are not part of the recorded job log
        var successLine = String.format("Job succeeded [%s]", MetadataUtil.objectKey(jobId));
        Assertions.assertTrue(logFileContent.contains(successLine));
    }

    @Test @Order(6)
    void checkOutputData() {

        log.info("Checking output data...");

        var dataClient = platform.dataClientBlocking();

        var EXPECTED_REGIONS = 2;  // based on the chaining example models

        var readRequest = DataReadRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setSelector(MetadataUtil.selectorFor(outputDataId))
                .setFormat("text/csv")
                .build();

        var readResponse = dataClient.readSmallDataset(readRequest);

        var csvText = readResponse.getContent().toString(StandardCharsets.UTF_8);
        var csvLines = csvText.split("\n");

        var csvHeaders = Arrays.stream(csvLines[0].split(","))
                .map(String::trim)
                .collect(Collectors.toList());

        Assertions.assertEquals(List.of("region", "gross_profit"), csvHeaders);
        Assertions.assertEquals(EXPECTED_REGIONS + 1, csvLines.length);
    }

    @Test @Order(7)
    void createFlow2() {

        var metaClient = platform.metaClientBlocking();

        var flowDef = FlowDefinition.newBuilder()
                .putNodes("customer_loans", FlowNode.newBuilder().setNodeType(FlowNodeType.INPUT_NODE).build())
                .putNodes("account_filter", FlowNode.newBuilder().setNodeType(FlowNodeType.INPUT_NODE).build())
                .putNodes("dynamic_filter", FlowNode.newBuilder()
                        .setNodeType(FlowNodeType.MODEL_NODE)
                        .addInputs("original_data")
                        .addOutputs("filtered_data")
                        .build())
                .putNodes("pnl_aggregation", FlowNode.newBuilder()
                        .setNodeType(FlowNodeType.MODEL_NODE)
                        .addInputs("customer_loans")
                        .addInputs("account_filter")
                        .addOutputs("profit_by_region")
                        .addOutputs("exclusions")
                        .build())
                .putNodes("profit_by_region", FlowNode.newBuilder()
                        .setNodeType(FlowNodeType.OUTPUT_NODE)
                        .build())
                .putNodes("exclusions", FlowNode.newBuilder()
                        .setNodeType(FlowNodeType.OUTPUT_NODE)
                        .build())
                .addEdges(FlowEdge.newBuilder()
                        .setSource(FlowSocket.newBuilder().setNode("customer_loans"))
                        .setTarget(FlowSocket.newBuilder().setNode("dynamic_filter").setSocket("original_data")))
                .addEdges(FlowEdge.newBuilder()
                        .setSource(FlowSocket.newBuilder().setNode("dynamic_filter").setSocket("filtered_data"))
                        .setTarget(FlowSocket.newBuilder().setNode("pnl_aggregation").setSocket("customer_loans")))
                .addEdges(FlowEdge.newBuilder()
                        .setSource(FlowSocket.newBuilder().setNode("account_filter"))
                        .setTarget(FlowSocket.newBuilder().setNode("pnl_aggregation").setSocket("account_filter")))
                .addEdges(FlowEdge.newBuilder()
                        .setSource(FlowSocket.newBuilder().setNode("pnl_aggregation").setSocket("profit_by_region"))
                        .setTarget(FlowSocket.newBuilder().setNode("profit_by_region")))
                .addEdges(FlowEdge.newBuilder()
                        .setSource(FlowSocket.newBuilder().setNode("pnl_aggregation").setSocket("exclusions"))
                        .setTarget(FlowSocket.newBuilder().setNode("exclusions")))
                .build();

        var createReq = MetadataWriteRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setObjectType(ObjectType.FLOW)
                .setDefinition(ObjectDefinition.newBuilder().setObjectType(ObjectType.FLOW).setFlow(flowDef))
                .build();

        flowId2 = metaClient.createObject(createReq);
    }

    @Test @Order(8)
    void createSchemas2() {

        var accountFilterSchema = ObjectDefinition.newBuilder()
                .setObjectType(ObjectType.SCHEMA)
                .setSchema(SchemaDefinition.newBuilder()
                .setSchemaType(SchemaType.TABLE)
                .setTable(TableSchema.newBuilder()
                .addFields(FieldSchema.newBuilder()
                        .setFieldName("account_id")
                        .setFieldType(BasicType.STRING)
                        .setBusinessKey(true))
                .addFields(FieldSchema.newBuilder()
                        .setFieldName("reason")
                        .setFieldType(BasicType.STRING))))
                .build();

        var accountFilterSchemaAttrs = List.of(TagUpdate.newBuilder()
                .setAttrName("e2e_test_schema")
                .setValue(MetadataCodec.encodeValue("run_flow:account_filter"))
                .build());

        accountFilterSchemaId = createSchema(accountFilterSchema, accountFilterSchemaAttrs);
    }

    @Test @Order(9)
    void loadInputData2() throws Exception {

        log.info("Loading input data...");

        var accountFilterAttrs = List.of(TagUpdate.newBuilder()
                .setAttrName("e2e_test_dataset")
                .setValue(MetadataCodec.encodeValue("run_flow:account_filter"))
                .build());

        accountFilterDataId = loadDataset(accountFilterSchemaId, ACCOUNT_FILTER_INPUT_PATH, accountFilterAttrs);
    }

    @Test @Order(10)
    void importModels2() {

        log.info("Running IMPORT_MODEL job...");

        var modelsPath = "examples/models/python/src";
        var modelsVersion = "main";

        var model1EntryPoint = "tutorial.dynamic_io.DynamicDataFilter";
        var model1Attrs = List.of(TagUpdate.newBuilder()
                .setAttrName("e2e_test_model")
                .setValue(MetadataCodec.encodeValue("run_flow:chaining:dynamic_io"))
                .build());
        var model1JobAttrs = List.of(TagUpdate.newBuilder()
                .setAttrName("e2e_test_job")
                .setValue(MetadataCodec.encodeValue("run_flow:import_model:dynamic_io"))
                .build());

        var model2EntryPoint = "tutorial.optional_io.OptionalIOModel";
        var model2Attrs = List.of(TagUpdate.newBuilder()
                .setAttrName("e2e_test_model")
                .setValue(MetadataCodec.encodeValue("run_flow:chaining:optional_io"))
                .build());
        var model2JobAttrs = List.of(TagUpdate.newBuilder()
                .setAttrName("e2e_test_job")
                .setValue(MetadataCodec.encodeValue("run_flow:import_model:optional_io"))
                .build());

        dynamicFilterModelId = importModel(
                modelsPath, model1EntryPoint, modelsVersion,
                model1Attrs, model1JobAttrs);

        pnlAggregationModelId = importModel(
                modelsPath, model2EntryPoint, modelsVersion,
                model2Attrs, model2JobAttrs);
    }

    @Test @Order(11)
    void runFlow2() {

        var metaClient = platform.metaClientBlocking();
        var orchClient = platform.orchClientBlocking();

        var runFlow = RunFlowJob.newBuilder()
                .setFlow(MetadataUtil.selectorFor(flowId2))
                .putParameters("eur_usd_rate", MetadataCodec.encodeValue(1.2071))
                .putParameters("default_weighting", MetadataCodec.encodeValue(1.5))
                .putParameters("filter_defaults", MetadataCodec.encodeValue(false))
                .putParameters("filter_column", MetadataCodec.encodeValue("region"))
                .putParameters("filter_value", MetadataCodec.encodeValue("munster"))
                .putInputs("customer_loans", MetadataUtil.selectorFor(loansDataId))
                .putInputs("account_filter", MetadataUtil.selectorFor(accountFilterDataId))
                .putModels("dynamic_filter", MetadataUtil.selectorFor(dynamicFilterModelId))
                .putModels("pnl_aggregation", MetadataUtil.selectorFor(pnlAggregationModelId))
                .addOutputAttrs(TagUpdate.newBuilder()
                        .setAttrName("e2e_test_data")
                        .setValue(MetadataCodec.encodeValue("run_flow:data_output_2")))
                .build();

        var jobRequest = JobRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setJob(JobDefinition.newBuilder()
                        .setJobType(JobType.RUN_FLOW)
                        .setRunFlow(runFlow))
                .addJobAttrs(TagUpdate.newBuilder()
                        .setAttrName("e2e_test_job")
                        .setValue(MetadataCodec.encodeValue("run_flow:run_flow_2")))
                .build();

        var jobStatus = runJob(orchClient, jobRequest);
        var jobKey = MetadataUtil.objectKey(jobStatus.getJobId());

        Assertions.assertEquals(JobStatusCode.SUCCEEDED, jobStatus.getStatusCode());

        var dataSearch = MetadataSearchRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setSearchParams(SearchParameters.newBuilder()
                        .setObjectType(ObjectType.DATA)
                        .setSearch(SearchExpression.newBuilder()
                                .setTerm(SearchTerm.newBuilder()
                                        .setAttrName("trac_create_job")
                                        .setAttrType(BasicType.STRING)
                                        .setOperator(SearchOperator.EQ)
                                        .setSearchValue(MetadataCodec.encodeValue(jobKey)))))
                .build();

        var dataSearchResult = metaClient.search(dataSearch);

        Assertions.assertEquals(2, dataSearchResult.getSearchResultCount());

        var indexedResult = dataSearchResult.getSearchResultList().stream()
                .collect(Collectors.toMap(RunFlowTest::getJobOutput, Function.identity()));

        var searchResult = indexedResult.get("profit_by_region");
        var dataReq = MetadataReadRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setSelector(MetadataUtil.selectorFor(searchResult.getHeader()))
                .build();

        var dataTag = metaClient.readObject(dataReq);
        var dataDef = dataTag.getDefinition().getData();
        var outputAttr = dataTag.getAttrsOrThrow("e2e_test_data");

        Assertions.assertEquals("run_flow:data_output_2", MetadataCodec.decodeStringValue(outputAttr));
        Assertions.assertEquals(1, dataDef.getPartsCount());

        profitByRegionDataId = dataTag.getHeader();

        var searchResult2 = indexedResult.get("exclusions");
        var dataReq2 = MetadataReadRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setSelector(MetadataUtil.selectorFor(searchResult2.getHeader()))
                .build();

        var dataTag2 = metaClient.readObject(dataReq2);
        var dataDef2 = dataTag2.getDefinition().getData();
        var outputAttr2 = dataTag.getAttrsOrThrow("e2e_test_data");

        Assertions.assertEquals("run_flow:data_output_2", MetadataCodec.decodeStringValue(outputAttr2));
        Assertions.assertEquals(1, dataDef2.getPartsCount());

        exclusionsDataId = dataTag2.getHeader();
    }

    @Test @Order(12)
    void checkOutputData2() {

        log.info("Checking output data...");

        var dataClient = platform.dataClientBlocking();

        var readRequest = DataReadRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setSelector(MetadataUtil.selectorFor(profitByRegionDataId))
                .setFormat("text/csv")
                .build();

        var readResponse = dataClient.readSmallDataset(readRequest);

        var csvText = readResponse.getContent().toString(StandardCharsets.UTF_8);
        var csvLines = csvText.split("\n");

        var csvHeaders = Arrays.stream(csvLines[0].split(","))
                .map(String::trim)
                .collect(Collectors.toList());

        Assertions.assertEquals(List.of("region", "gross_profit"), csvHeaders);

        // Check the dynamic filter was applied successfully

        // Filtered categorical vars present in aggregation, should have zero value
        Assertions.assertTrue(csvText.contains("leinster"));
        Assertions.assertFalse(csvText.contains("leinster,0\n"));
        Assertions.assertTrue(csvText.contains("munster,0\n"));

        var readRequest2 = DataReadRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setSelector(MetadataUtil.selectorFor(exclusionsDataId))
                .setFormat("text/csv")
                .build();

        var readResponse2 = dataClient.readSmallDataset(readRequest2);

        var csvText2 = readResponse2.getContent().toString(StandardCharsets.UTF_8);
        var csvLines2 = csvText2.split("\n");

        var csvHeaders2 = Arrays.stream(csvLines2[0].split(","))
                .map(String::trim)
                .collect(Collectors.toList());

        Assertions.assertEquals(List.of("reason", "count"), csvHeaders2);

        // At least one exclusion row after the header

        Assertions.assertTrue(csvLines2.length >= 2);
    }

    @Test @Order(13)
    void struct_createFlow() {

        var metaClient = platform.metaClientBlocking();

        var flowDef = FlowDefinition.newBuilder()
                .putNodes("run_config", FlowNode.newBuilder().setNodeType(FlowNodeType.INPUT_NODE).build())
                .putNodes("model_1", FlowNode.newBuilder()
                        .setNodeType(FlowNodeType.MODEL_NODE)
                        .addInputs("run_config")
                        .addOutputs("modified_config")
                        .build())
                .putNodes("model_2", FlowNode.newBuilder()
                        .setNodeType(FlowNodeType.MODEL_NODE)
                        .addInputs("run_config")
                        .addOutputs("modified_config")
                        .build())
                .putNodes("modified_config", FlowNode.newBuilder()
                        .setNodeType(FlowNodeType.OUTPUT_NODE)
                        .build())
                .addEdges(FlowEdge.newBuilder()
                        .setSource(FlowSocket.newBuilder().setNode("run_config"))
                        .setTarget(FlowSocket.newBuilder().setNode("model_1").setSocket("run_config")))
                .addEdges(FlowEdge.newBuilder()
                        .setSource(FlowSocket.newBuilder().setNode("model_1").setSocket("modified_config"))
                        .setTarget(FlowSocket.newBuilder().setNode("model_2").setSocket("run_config")))
                .addEdges(FlowEdge.newBuilder()
                        .setSource(FlowSocket.newBuilder().setNode("model_2").setSocket("modified_config"))
                        .setTarget(FlowSocket.newBuilder().setNode("modified_config")))
                .build();

        var createReq = MetadataWriteRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setObjectType(ObjectType.FLOW)
                .setDefinition(ObjectDefinition.newBuilder().setObjectType(ObjectType.FLOW).setFlow(flowDef))
                .build();

        structFlowId = metaClient.createObject(createReq);
    }

    @Test @Order(14)
    void struct_importModel() throws Exception {

        log.info("Running IMPORT_MODEL job for struct...");

        var modelVersion = GitHelpers.getCurrentCommit();
        var modelStub = ModelDefinition.newBuilder()
                .setLanguage("python")
                .setRepository(useTracRepo())
                .setPath("examples/models/python/src")
                .setEntryPoint("tutorial.structured_objects.StructModel")
                .setVersion(modelVersion)
                .build();

        var modelAttrs = List.of(TagUpdate.newBuilder()
                .setAttrName("e2e_test_model")
                .setValue(MetadataCodec.encodeValue("run_flow:struct"))
                .build());

        var jobAttrs = List.of(TagUpdate.newBuilder()
                .setAttrName("e2e_test_job")
                .setValue(MetadataCodec.encodeValue("run_flow:struct_import_model"))
                .build());

        var modelTag = ImportModelTest.doImportModel(platform, TEST_TENANT, modelStub, modelAttrs, jobAttrs);
        var modelDef = modelTag.getDefinition().getModel();
        var modelAttr = modelTag.getAttrsOrThrow("e2e_test_model");

        Assertions.assertEquals("run_flow:struct", MetadataCodec.decodeStringValue(modelAttr));
        Assertions.assertEquals("tutorial.structured_objects.StructModel", modelDef.getEntryPoint());
        Assertions.assertTrue(modelDef.getInputsMap().containsKey("run_config"));
        Assertions.assertTrue(modelDef.getOutputsMap().containsKey("modified_config"));

        structModelId = modelTag.getHeader();
        structModelInputSchema = modelDef.getInputsOrThrow("run_config").getSchema();
    }

    @Test @Order(15)
    void struct_loadInputData() throws Exception {

        log.info("Loading struct input data...");

        var metaClient = platform.metaClientBlocking();
        var dataClient = platform.dataClientBlocking();

        var inputPath = platform.tracRepoDir().resolve(STRUCT_INPUT_PATH);
        var inputBytes = Files.readAllBytes(inputPath);

        var writeRequest = DataWriteRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setSchema(structModelInputSchema)
                .setFormat("application/json")
                .setContent(ByteString.copyFrom(inputBytes))
                .addTagUpdates(TagUpdate.newBuilder()
                        .setAttrName("e2e_test_dataset")
                        .setValue(MetadataCodec.encodeValue("run_flow:run_config")))
                .build();

        structInputDataId = dataClient.createSmallDataset(writeRequest);

        var dataSelector = MetadataUtil.selectorFor(structInputDataId);
        var dataRequest = MetadataReadRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setSelector(dataSelector)
                .build();

        var dataTag = metaClient.readObject(dataRequest);

        var datasetAttr = dataTag.getAttrsOrThrow("e2e_test_dataset");
        var datasetSchema = dataTag.getDefinition().getData().getSchema();

        Assertions.assertEquals("run_flow:run_config", MetadataCodec.decodeStringValue(datasetAttr));
        Assertions.assertEquals(SchemaType.STRUCT_SCHEMA, datasetSchema.getSchemaType());
        Assertions.assertEquals(4, datasetSchema.getFieldsCount());

        log.info("Struct input data loaded, data ID = [{}]", dataTag.getHeader().getObjectId());
    }

    @Test @Order(16)
    void struct_runFlow() {

        var metaClient = platform.metaClientBlocking();
        var orchClient = platform.orchClientBlocking();

        var runFlow = RunFlowJob.newBuilder()
                .setFlow(MetadataUtil.selectorFor(structFlowId))
                .putParameters("t0_date", MetadataCodec.encodeValue(LocalDate.now()))
                .putParameters("projection_period", MetadataCodec.encodeValue(365))
                .putInputs("run_config", MetadataUtil.selectorFor(structInputDataId))
                .putModels("model_1", MetadataUtil.selectorFor(structModelId))
                .putModels("model_2", MetadataUtil.selectorFor(structModelId))
                .addOutputAttrs(TagUpdate.newBuilder()
                        .setAttrName("e2e_test_data")
                        .setValue(MetadataCodec.encodeValue("run_flow:struct_data_output")))
                .build();

        var jobRequest = JobRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setJob(JobDefinition.newBuilder()
                        .setJobType(JobType.RUN_FLOW)
                        .setRunFlow(runFlow))
                .addJobAttrs(TagUpdate.newBuilder()
                        .setAttrName("e2e_test_job")
                        .setValue(MetadataCodec.encodeValue("run_flow:struct_run_flow")))
                .build();

        var jobStatus = runJob(orchClient, jobRequest);

        jobId = jobStatus.getJobId();

        Assertions.assertEquals(JobStatusCode.SUCCEEDED, jobStatus.getStatusCode());

        var dataSearch = MetadataSearchRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setSearchParams(SearchParameters.newBuilder()
                        .setObjectType(ObjectType.DATA)
                        .setSearch(SearchExpression.newBuilder()
                                .setTerm(SearchTerm.newBuilder()
                                        .setAttrName("trac_create_job")
                                        .setAttrType(BasicType.STRING)
                                        .setOperator(SearchOperator.EQ)
                                        .setSearchValue(MetadataCodec.encodeValue(MetadataUtil.objectKey(jobId))))))
                .build();

        var dataSearchResult = metaClient.search(dataSearch);

        Assertions.assertEquals(1, dataSearchResult.getSearchResultCount());

        var indexedResult = dataSearchResult.getSearchResultList().stream()
                .collect(Collectors.toMap(RunFlowTest::getJobOutput, Function.identity()));

        var searchResult = indexedResult.get("modified_config");

        var dataReq = MetadataReadRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setSelector(MetadataUtil.selectorFor(searchResult.getHeader()))
                .build();

        var dataTag = metaClient.readObject(dataReq);
        var dataDef = dataTag.getDefinition().getData();
        var outputAttr = dataTag.getAttrsOrThrow("e2e_test_data");

        Assertions.assertEquals("run_flow:struct_data_output", MetadataCodec.decodeStringValue(outputAttr));
        Assertions.assertEquals(1, dataDef.getPartsCount());

        structOutputDataId = dataTag.getHeader();
    }

    @Test @Order(17)
    void struct_checkOutputData() {

        log.info("Checking struct output data...");

        var dataClient = platform.dataClientBlocking();

        var readRequest = DataReadRequest.newBuilder()
                .setTenant(TEST_TENANT)
                .setSelector(MetadataUtil.selectorFor(structOutputDataId))
                .setFormat("application/json")
                .build();


        var readResponse = dataClient.readSmallDataset(readRequest);
        var jsonText = readResponse.getContent().toString(StandardCharsets.UTF_8);

        // Model should have added this scenario, just look for its name
        Assertions.assertTrue(jsonText.contains("hpi_shock"));
    }

}
