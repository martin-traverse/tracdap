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

package org.finos.tracdap.test.meta;

import org.finos.tracdap.common.exception.EUnexpected;
import org.finos.tracdap.metadata.*;
import org.finos.tracdap.common.metadata.TypeSystem;
import org.finos.tracdap.common.metadata.MetadataCodec;
import com.google.protobuf.ByteString;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;


public class SampleMetadata {

    public static final boolean INCLUDE_HEADER = true;
    public static final boolean NO_HEADER = false;

    public static final boolean UPDATE_TAG_VERSION = true;
    public static final boolean KEEP_ORIGINAL_TAG_VERSION = false;

    public static final String TEST_TENANT = "ACME_CORP";
    public static final String ALT_TEST_TENANT = "ALT_CORP";


    private static final Random random = new Random(Instant.now().getEpochSecond());

    private static TagSelector storageSelector = TagSelector.newBuilder()
            .setObjectType(ObjectType.STORAGE)
            .setObjectId(UUID.randomUUID().toString())
            .setLatestObject(true)
            .setLatestTag(true)
            .build();

    public static void storageForPartialTests(TagSelector storageSelector) {

        SampleMetadata.storageSelector = storageSelector.toBuilder()
                .setLatestObject(true)
                .setLatestTag(true)
                .build();
    }

    public static ObjectDefinition dummyDefinitionForType(ObjectType objectType) {

        switch (objectType) {

            case DATA: return dummyDataDef();
            case MODEL: return dummyModelDef();
            case FLOW: return dummyFlowDef();
            case JOB: return dummyJobDef();
            case FILE: return dummyFileDef();
            case CUSTOM: return dummyCustomDef();
            case STORAGE: return dummyStorageDef();
            case SCHEMA: return dummySchemaDef();
            case RESULT: return dummyResultDef();
            case CONFIG: return dummyConfigDef();
            case RESOURCE: return dummyResourceDef();

            default:
                throw new RuntimeException("No dummy data available for object type " + objectType.name());
        }
    }

    public static Tag dummyTagForObjectType(ObjectType objectType) {

        return dummyTag(dummyDefinitionForType(objectType), INCLUDE_HEADER);
    }

    public static ObjectDefinition dummyVersionForType(ObjectDefinition definition) {

        // Not all object types have semantics defined for versioning
        // It is sometimes helpful to create versions anyway for testing
        // E.g. to test that version increments are rejected for objects that don't support versioning!

        var objectType = definition.getObjectType();

        switch (objectType) {

            case DATA: return nextDataDef(definition);
            case MODEL: return nextModelDef(definition);
            case CUSTOM: return nextCustomDef(definition);
            case STORAGE: return nextStorageDef(definition);
            case SCHEMA: return nextSchemaDef(definition);
            case FILE: return nextFileDef(definition);
            case CONFIG: return nextConfigDef(definition);
            case RESOURCE: return nextResourceDef(definition);

            case FLOW:
            case JOB:
            case RESULT:

                return definition;

            default:
                throw new RuntimeException("No second version available in dummy data for object type " + objectType.name());
        }
    }

    public static ObjectDefinition dummyBadVersionForType(ObjectDefinition definition) {

        // Not all object types have semantics defined for versioning
        // It is sometimes helpful to create versions anyway for testing
        // E.g. to test that version increments are rejected for objects that don't support versioning!

        var objectType = definition.getObjectType();

        switch (objectType) {

            case DATA: return nextBadDataDef(definition);
            case MODEL: return nextBadModelDef(definition);
            case CUSTOM: return nextBadCustomDef(definition);
            case STORAGE: return nextBadStorageDef(definition);
            case SCHEMA: return nextBadSchemaDef(definition);
            case FILE: return nextBadFileDef(definition);
            case CONFIG: return nextBadConfigDef(definition);
            case RESOURCE: return nextBadResourceDef(definition);

            case FLOW:
            case JOB:

                return definition;

            default:
                throw new RuntimeException("No second version available in dummy data for object type " + objectType.name());
        }
    }

    public static TagHeader newHeader(ObjectType objectType) {

        var timestamp = Instant.now().atOffset(ZoneOffset.UTC);

        return TagHeader.newBuilder()
                .setObjectType(objectType)
                .setObjectId(UUID.randomUUID().toString())
                .setObjectVersion(1)
                .setTagVersion(1)
                .setIsLatestObject(true)
                .setIsLatestTag(true)
                .setObjectTimestamp(MetadataCodec.encodeDatetime(timestamp))
                .setTagTimestamp(MetadataCodec.encodeDatetime(timestamp))
                .build();
    }

    public static TagHeader nextTagHeader(TagHeader priorTagHeader) {

        var timestamp = Instant.now().atOffset(ZoneOffset.UTC);

        return priorTagHeader.toBuilder()
                .setTagVersion(priorTagHeader.getTagVersion() + 1)
                .setIsLatestTag(true)
                .setTagTimestamp(MetadataCodec.encodeDatetime(timestamp))
                .build();
    }

    public static ObjectDefinition dummyDataDef() {

        return ObjectDefinition.newBuilder()
            .setObjectType(ObjectType.DATA)
            .setData(DataDefinition.newBuilder()

            // This storage selector can be set by test cases to handle consistency requirements in the meta svc
            .setStorageId(storageSelector)

            .setSchema(SchemaDefinition.newBuilder()
                .setSchemaType(SchemaType.TABLE)
                .setTable(TableSchema.newBuilder()
                .addFields(FieldSchema.newBuilder()
                        .setFieldName("transaction_id")
                        .setFieldType(BasicType.STRING)
                        .setFieldOrder(0)
                        .setBusinessKey(true))
                .addFields(FieldSchema.newBuilder()
                        .setFieldName("customer_id")
                        .setFieldType(BasicType.STRING)
                        .setFieldOrder(1)
                        .setBusinessKey(true))
                .addFields(FieldSchema.newBuilder()
                        .setFieldName("order_date")
                        .setFieldType(BasicType.DATE)
                        .setFieldOrder(2)
                        .setBusinessKey(true))
                .addFields(FieldSchema.newBuilder()
                        .setFieldName("widgets_ordered")
                        .setFieldType(BasicType.INTEGER)
                        .setFieldOrder(3)
                        .setBusinessKey(true))))

                .putParts("part-root", DataPartition.newBuilder()
                        .setPartKey(PartKey.newBuilder()
                        .setPartType(PartType.PART_ROOT)
                        .setOpaqueKey("part-root"))
                        .setSnap(DataSnapshot.newBuilder()
                        .setSnapIndex(0)
                        .addDeltas(DataDelta.newBuilder()
                        .setDeltaIndex(0)
                        .setDataItem("data/table/" + UUID.randomUUID() + "/snap-0/delta-0-x123456")))
                        .build()))

            .build();
    }

    public static ObjectDefinition nextDataDef(ObjectDefinition origDef) {

        var newSchema = addFieldToSchema(origDef.getData().getSchema());

        return origDef.toBuilder()
                .setData(origDef.getData().toBuilder()
                .setSchema(newSchema))
                .build();
    }

    public static ObjectDefinition nextBadDataDef(ObjectDefinition origDef) {

        var newSchema = changeBadSchemaField(origDef.getData().getSchema());

        return origDef.toBuilder()
                .setData(origDef.getData().toBuilder()
                .setSchema(newSchema))
                .build();
    }

    public static ObjectDefinition dummySchemaDef() {

        var dataDef = dummyDataDef();

        return ObjectDefinition.newBuilder()
                .setObjectType(ObjectType.SCHEMA)
                .setSchema(dataDef.getData().getSchema())
                .build();
    }

    public static ObjectDefinition nextSchemaDef(ObjectDefinition origDef) {

        return origDef.toBuilder()
                .setSchema(addFieldToSchema(origDef.getSchema()))
                .build();
    }

    public static ObjectDefinition nextBadSchemaDef(ObjectDefinition origDef) {

        return origDef.toBuilder()
                .setSchema(changeBadSchemaField(origDef.getSchema()))
                .build();
    }

    private static SchemaDefinition addFieldToSchema(SchemaDefinition origSchema) {

        var fieldName = "extra_field_" + (origSchema.getTable().getFieldsCount() + 1);

        var newTableSchema = origSchema.getTable().toBuilder()
                .addFields(FieldSchema.newBuilder()
                .setFieldName(fieldName)
                .setFieldOrder(origSchema.getTable().getFieldsCount())
                .setFieldType(BasicType.FLOAT)
                .setLabel("We got an extra field!")
                .setFormatCode("PERCENT"));

        return origSchema.toBuilder()
                .setTable(newTableSchema)
                .build();
    }

    private static SchemaDefinition changeBadSchemaField(SchemaDefinition origSchema) {

        var originalField = origSchema.getTable().getFields(0);
        var newField = originalField.toBuilder()
                .setFieldType(BasicType.DATE)
                .build();

        return origSchema.toBuilder()
                .setTable(origSchema.getTable().toBuilder()
                .setFields(0, newField))
                .build();
    }

    public static ObjectDefinition dummyStorageDef() {

        var storageTimestamp = OffsetDateTime.now();

        return ObjectDefinition.newBuilder()
            .setObjectType(ObjectType.STORAGE)
            .setStorage(StorageDefinition.newBuilder()
            .putDataItems("dummy_item", StorageItem.newBuilder()
                .addIncarnations(StorageIncarnation.newBuilder()
                .setIncarnationIndex(1)
                .setIncarnationTimestamp(MetadataCodec.encodeDatetime(storageTimestamp))
                .setIncarnationStatus(IncarnationStatus.INCARNATION_AVAILABLE)
                .addCopies(StorageCopy.newBuilder()
                    .setStorageKey("DUMMY_STORAGE")
                    .setStoragePath("path/to/the/dataset")
                    .setStorageFormat("AVRO")
                    .setCopyTimestamp(MetadataCodec.encodeDatetime(storageTimestamp))
                    .setCopyStatus(CopyStatus.COPY_AVAILABLE)))
                    .build())).build();
    }

    public static ObjectDefinition nextStorageDef(ObjectDefinition definition) {

        var storageTimestamp = OffsetDateTime.now();

        var archiveCopy = StorageCopy.newBuilder()
            .setStorageKey("DUMMY_ARCHIVE_STORAGE")
            .setStoragePath("path/to/the/dataset")
            .setStorageFormat("PARQUET")
            .setCopyTimestamp(MetadataCodec.encodeDatetime(storageTimestamp))
            .setCopyStatus(CopyStatus.COPY_AVAILABLE);

        var archivedIncarnation = definition
            .getStorage()
            .getDataItemsOrThrow("dummy_item")
            .getIncarnations(0)
            .toBuilder()
            .addCopies(archiveCopy);

        return definition.toBuilder()
            .setStorage(definition.getStorage().toBuilder()
            .putDataItems("dummy_item", StorageItem.newBuilder()
            .addIncarnations(archivedIncarnation).build()))
            .build();
    }

    public static ObjectDefinition nextBadStorageDef(ObjectDefinition definition) {

        var originalItem = definition.getStorage().getDataItemsOrThrow("dummy_item");
        var originalCopy = originalItem.getIncarnations(0).getCopies(0);

        var newCopy = originalCopy.toBuilder()
                .setStorageFormat("PARQUET")
                .build();

        var newItem = originalItem.toBuilder()
                .setIncarnations(0, originalItem.getIncarnations(0).toBuilder()
                .setCopies(0, newCopy))
                .build();

        return definition.toBuilder()
                .setStorage(definition.getStorage().toBuilder()
                .putDataItems("dummy_item", newItem))
                .build();
    }

    public static ObjectDefinition dummyModelDef() {

        return ObjectDefinition.newBuilder()
                .setObjectType(ObjectType.MODEL)
                .setModel(ModelDefinition.newBuilder()
                .setLanguage("python")
                .setRepository("trac_test_repo")
                .setPath("src/main/python")
                .setVersion("trac-test-repo-1.2.3-RC4")
                .setEntryPoint("trac_test.test1.SampleModel1")
                .putParameters("param1", ModelParameter.newBuilder().setParamType(TypeSystem.descriptor(BasicType.STRING)).build())
                .putParameters("param2", ModelParameter.newBuilder().setParamType(TypeSystem.descriptor(BasicType.INTEGER)).build())
                .putInputs("input1", ModelInputSchema.newBuilder()
                        .setObjectType(ObjectType.DATA)
                        .setSchema(SchemaDefinition.newBuilder()
                        .setSchemaType(SchemaType.TABLE)
                        .setTable(TableSchema.newBuilder()
                        .addFields(FieldSchema.newBuilder()
                                .setFieldName("field1")
                                .setFieldType(BasicType.DATE)
                                .setBusinessKey(true))
                        .addFields(FieldSchema.newBuilder()
                                .setFieldName("field2")
                                .setFieldType(BasicType.STRING)
                                .setCategorical(true))
                        .addFields(FieldSchema.newBuilder()
                                .setFieldName("field3")
                                .setFieldType(BasicType.DECIMAL)
                                .setLabel("A display name")
                                .setFormatCode("GBP"))))
                        .build())
                .putOutputs("output1", ModelOutputSchema.newBuilder()
                        .setObjectType(ObjectType.DATA)
                        .setSchema(SchemaDefinition.newBuilder()
                        .setSchemaType(SchemaType.TABLE)
                        .setTable(TableSchema.newBuilder()
                        .addFields(FieldSchema.newBuilder()
                                .setFieldName("checksum_field")
                                .setFieldType(BasicType.DECIMAL))))
                        .build()))
                .build();
    }

    public static ObjectDefinition nextModelDef(ObjectDefinition origDef) {

        return origDef.toBuilder()
                .setModel(origDef.getModel()
                .toBuilder()
                .putParameters("param3", ModelParameter.newBuilder().setParamType(TypeSystem.descriptor(BasicType.DATE)).build()))
                .build();
    }

    public static ObjectDefinition nextBadModelDef(ObjectDefinition origDef) {

        return origDef.toBuilder()
                .setModel(origDef.getModel()
                .toBuilder()
                .setPath("altered/layout/src"))
                .build();
    }

    public static ObjectDefinition dummyFlowDef() {

        return ObjectDefinition.newBuilder()
                .setObjectType(ObjectType.FLOW)
                .setFlow(FlowDefinition.newBuilder()
                .putNodes("input_1", FlowNode.newBuilder().setNodeType(FlowNodeType.INPUT_NODE).build())
                .putNodes("output_1", FlowNode.newBuilder().setNodeType(FlowNodeType.OUTPUT_NODE).build())
                .putNodes("main_model", FlowNode.newBuilder()
                        .setNodeType(FlowNodeType.MODEL_NODE)
                        .addInputs("input_1")
                        .addOutputs("output_1")
                        .build())
                .addEdges(FlowEdge.newBuilder()
                        .setSource(FlowSocket.newBuilder().setNode("input_1"))
                        .setTarget(FlowSocket.newBuilder().setNode("main_model").setSocket("input_1")))
                .addEdges(FlowEdge.newBuilder()
                        .setSource(FlowSocket.newBuilder().setNode("main_model").setSocket("output_1"))
                        .setTarget(FlowSocket.newBuilder().setNode("output_1"))))
                .build();
    }

    public static ObjectDefinition dummyJobDef() {

        // Job will be invalid because the model ID it points to does not exist!
        // Ok for e.g. DAL testing, but will fail metadata validation

        var targetSelector = TagSelector.newBuilder()
                .setObjectType(ObjectType.MODEL)
                .setObjectId(UUID.randomUUID().toString())
                .setObjectVersion(1)
                .setLatestTag(true);

        var resultSelector = TagSelector.newBuilder()
                .setObjectType(ObjectType.RESULT)
                .setObjectId(UUID.randomUUID().toString())
                .setObjectVersion(1)
                .setLatestTag(true);

        return ObjectDefinition.newBuilder()
                .setObjectType(ObjectType.JOB)
                .setJob(JobDefinition.newBuilder()
                .setJobType(JobType.RUN_MODEL)
                .setRunModel(RunModelJob.newBuilder()
                .setModel(targetSelector))
                .setResultId(resultSelector))
                .build();
    }

    public static ObjectDefinition dummyResultDef() {

        var jobSelector = TagSelector.newBuilder()
                .setObjectType(ObjectType.JOB)
                .setObjectId(UUID.randomUUID().toString())
                .setObjectVersion(1)
                .setLatestTag(true);

        var logFileSelector = TagSelector.newBuilder()
                .setObjectType(ObjectType.FILE)
                .setObjectId(UUID.randomUUID().toString())
                .setObjectVersion(1)
                .setLatestTag(true);

        return ObjectDefinition.newBuilder()
                .setObjectType(ObjectType.RESULT)
                .setResult(ResultDefinition.newBuilder()
                .setJobId(jobSelector)
                .setStatusCode(JobStatusCode.SUCCEEDED)
                .setStatusMessage("Job completed in [42] seconds")
                .setLogFileId(logFileSelector))
                .build();
    }

    public static ObjectDefinition dummyConfigDef() {

        return ObjectDefinition.newBuilder()
                .setObjectType(ObjectType.CONFIG)
                .setConfig(ConfigDefinition.newBuilder()
                        .setConfigType(ConfigType.PROPERTIES)
                        .putProperties("acme.rocket.fuel", "nuclear")
                        .putProperties("acme.rocket.payload", "habitation_pod")
                        .putProperties("acme.rocket.crew_compliment", "6")
                        .putProperties("acme.rocket.destination", "proxima_centauri"))
                .build();
    }

    public static ObjectDefinition nextConfigDef(ObjectDefinition origDef) {

        return origDef.toBuilder()
                .setConfig(origDef.getConfig().toBuilder()
                .putProperties("acme.rocket.crew_compliment", "7"))
                .build();
    }

    public static ObjectDefinition nextBadConfigDef(ObjectDefinition origDef) {

        return origDef.toBuilder()
                .setConfig(origDef.getConfig().toBuilder()
                .setConfigType(ConfigType.CONFIG_TYPE_NOT_SET))
                .build();
    }

    public static ObjectDefinition dummyResourceDef() {

        return ObjectDefinition.newBuilder()
                .setObjectType(ObjectType.RESOURCE)
                .setResource(ResourceDefinition.newBuilder()
                .setResourceType(ResourceType.MODEL_REPOSITORY)
                .setProtocol("git")
                .setSubProtocol("github_app")
                .putPublicProperties("friendlyName", "TRAC DAP Repository")
                .putProperties("reopUrl", "https://github.com/finos/tracdap")
                .putSecrets("secret1", "secret_alias"))
                .build();
    }

    public static ObjectDefinition nextResourceDef(ObjectDefinition origDef) {

        return origDef.toBuilder()
                .setResource(origDef.getResource().toBuilder()
                .setSubProtocol("github_oauth")
                .putProperties("reopUrl", "https://github.com/finos/tracdap-migrated")
                .putSecrets("secret1", "updated_secret_alias"))
                .build();
    }

    public static ObjectDefinition nextBadResourceDef(ObjectDefinition origDef) {

        return origDef.toBuilder()
                .setResource(origDef.getResource().toBuilder()
                .setResourceType(ResourceType.INTERNAL_STORAGE)
                .setProtocol("S3")
                .putPublicProperties("friendlyName", "Now this resource is an S3 bucket")
                .putProperties("bucketName", "expecting-a-model-repo"))
                .build();
    }

    public static ObjectDefinition dummyFileDef() {

        return ObjectDefinition.newBuilder()
                .setObjectType(ObjectType.FILE)
                .setFile(FileDefinition.newBuilder()
                .setName("magic_template")
                .setExtension("docx")
                .setMimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .setSize(45285)
                .setStorageId(storageSelector)
                .setDataItem("file/FILE_ID/version-1"))
                .build();
    }

    public static ObjectDefinition nextFileDef(ObjectDefinition origDef) {

        return origDef.toBuilder()
                .setFile(origDef.getFile().toBuilder()
                .setName("magic_template_v2_updated")  // File names are likely to be changed with suffixes etc
                .setSize(87533)
                .setDataItem("file/FILE_ID/version-2"))
                .build();
    }

    public static ObjectDefinition nextBadFileDef(ObjectDefinition origDef) {

        return origDef.toBuilder()
                .setFile(origDef.getFile().toBuilder()
                .setExtension("txt")
                .setMimeType("text/plain")
                .setSize(87533)
                .setDataItem("file/FILE_ID/version-2"))
                .build();
    }

    public static ObjectDefinition dummyCustomDef() {

        var jsonReportDef = "{ reportType: 'magic', mainGraph: { content: 'more_magic' } }";

        return ObjectDefinition.newBuilder()
                .setObjectType(ObjectType.CUSTOM)
                .setCustom(CustomDefinition.newBuilder()
                .setCustomSchemaType("REPORT")
                .setCustomSchemaVersion(1)
                .setCustomData(ByteString.copyFromUtf8(jsonReportDef)))
                .build();
    }

    public static ObjectDefinition nextCustomDef(ObjectDefinition origDef) {

        var ver = UUID.randomUUID();
        var jsonReportDef = "{ reportType: 'magic', mainGraph: { content: 'more_magic_" + ver + " ' } }";

        return origDef.toBuilder()
                .setCustom(origDef.getCustom()
                .toBuilder()
                .setCustomSchemaVersion(2)
                .setCustomData(ByteString.copyFromUtf8(jsonReportDef)))
                .build();
    }

    public static ObjectDefinition nextBadCustomDef(ObjectDefinition origDef) {

        return origDef.toBuilder()
                .setCustom(origDef.getCustom()
                .toBuilder()
                .setCustomSchemaType("DASHBOARD"))
                .build();
    }

    public static Tag dummyTag(ObjectDefinition definition, boolean includeHeader) {

        var tag = Tag.newBuilder()
                .setDefinition(definition)
                .putAttrs("dataset_key", MetadataCodec.encodeValue("widget_orders"))
                .putAttrs("widget_type", MetadataCodec.encodeValue("non_standard_widget"));

        if (includeHeader) {
            var header = newHeader(definition.getObjectType());
            return tag.setHeader(header).build();
        }
        else
            return tag.build();
    }

    public static Map<String, Value> dummyAttrs() {

        var attrs = new HashMap<String, Value>();
        attrs.put("dataset_key", MetadataCodec.encodeValue("widget_orders"));
        attrs.put("widget_type", MetadataCodec.encodeValue("non_standard_widget"));

        return attrs;
    }

    public static List<TagUpdate> tagUpdatesForAttrs(Map<String, Value> attrs) {

        return attrs.entrySet().stream()
                .map(entry -> TagUpdate.newBuilder()
                .setAttrName(entry.getKey())
                .setValue(entry.getValue()).build())
                .collect(Collectors.toList());
    }

    public static Tag tagForNextObject(Tag previous, ObjectDefinition obj, boolean includeHeader) {

        var timestamp = Instant.now().atOffset(ZoneOffset.UTC);

        var newTag = previous.toBuilder()
                .setDefinition(obj)
                .putAttrs("extra_attr", Value.newBuilder()
                        .setType(TypeSystem.descriptor(BasicType.STRING))
                        .setStringValue("A new descriptive value")
                        .build());

        if (includeHeader) {

            var header = previous.getHeader().toBuilder()
                    .setObjectVersion(previous.getHeader().getObjectVersion() + 1)
                    .setTagVersion(1)
                    .setIsLatestTag(true)
                    .setIsLatestObject(true)
                    .setObjectTimestamp(MetadataCodec.encodeDatetime(timestamp))
                    .setTagTimestamp(MetadataCodec.encodeDatetime(timestamp))
                    .build();

            return newTag.setHeader(header).build();
        }
        else
            return newTag.clearHeader().build();
    }

    public static Tag nextTag(Tag previous, boolean updateTagVersion) {

        var updatedTag = previous.toBuilder()
                .putAttrs("extra_attr", Value.newBuilder()
                .setType(TypeSystem.descriptor(BasicType.STRING))
                .setStringValue("A new descriptive value")
                .build());

        if (updateTagVersion == KEEP_ORIGINAL_TAG_VERSION)
            return updatedTag.build();

        var nextHeader = nextTagHeader(previous.getHeader());
        return updatedTag.setHeader(nextHeader).build();
    }

    public static Tag addMultiValuedAttr(Tag tag) {

        var dataClassification = MetadataCodec.encodeArrayValue(
                List.of("pii", "bcbs239", "confidential"),
                TypeSystem.descriptor(BasicType.STRING));

        return tag.toBuilder()
                .putAttrs("data_classification", dataClassification)
                .build();
    }

    // Create Java objects according to the TRAC type system

    public static Object objectOfType(BasicType basicType) {

        switch (basicType) {

            case BOOLEAN: return true;
            case INTEGER: return (long) 42;
            case FLOAT: return Math.PI;
            case DECIMAL: return new BigDecimal("1234.567");
            case STRING: return "the_droids_you_are_looking_for";
            case DATE: return LocalDate.now();

            // Metadata datetime attrs have microsecond precision
            case DATETIME:
                var dateTime = OffsetDateTime.now(ZoneOffset.UTC);
                return truncateMicrosecondPrecision(dateTime);

            default:
                throw new RuntimeException("Test object not available for basic type " + basicType);
        }
    }

    public static Object objectOfDifferentType(BasicType basicType) {

        if (basicType == BasicType.STRING)
            return objectOfType(BasicType.INTEGER);
        else
            return objectOfType(BasicType.STRING);
    }

    public static Object differentObjectOfSameType(BasicType basicType, Object originalObject) {

        switch (basicType) {

            case BOOLEAN: return ! ((Boolean) originalObject);
            case INTEGER: return ((Long) originalObject) + 1L;
            case FLOAT: return ((Double) originalObject) + 2.0D;
            case DECIMAL: return ((BigDecimal) originalObject).add(new BigDecimal(2));
            case STRING: return originalObject.toString() + " and friends";
            case DATE: return ((LocalDate) originalObject).plusDays(1);
            case DATETIME: return ((OffsetDateTime) originalObject).plusHours(1);

            default:
                throw new RuntimeException("Test object not available for basic type " + basicType);
        }
    }

    public static OffsetDateTime truncateMicrosecondPrecision(OffsetDateTime dateTime) {

        int precision = 6;

        var nanos = dateTime.getNano();
        var nanoPrecision = (int) Math.pow(10, 9 - precision);
        var truncatedNanos = (nanos / nanoPrecision) * nanoPrecision;
        return dateTime.withNano(truncatedNanos);
    }

    public static TagSelector selectorForTag(TagHeader tagHeader) {

        return TagSelector.newBuilder()
                .setObjectType(tagHeader.getObjectType())
                .setObjectId(tagHeader.getObjectId())
                .setObjectVersion(tagHeader.getObjectVersion())
                .setTagVersion(tagHeader.getTagVersion())
                .build();
    }

    public static TagSelector selectorForTag(Tag tag) {

        return selectorForTag(tag.getHeader());
    }

    public static Value randomPrimitive(BasicType basicType) {

        var typeDescriptor = TypeSystem.descriptor(basicType);

        switch (basicType) {

            case BOOLEAN:

                return Value.newBuilder()
                        .setType(typeDescriptor)
                        .setBooleanValue(true)
                        .build();

            case INTEGER:

                return Value.newBuilder()
                        .setType(typeDescriptor)
                        .setIntegerValue(random.nextLong())
                        .build();

            case FLOAT:

                return Value.newBuilder()
                        .setType(typeDescriptor)
                        .setFloatValue(random.nextDouble())
                        .build();

            case DECIMAL:

                var decimalValue = BigDecimal.valueOf(random.nextDouble());

                return Value.newBuilder()
                        .setType(typeDescriptor)
                        .setDecimalValue(DecimalValue.newBuilder()
                        .setDecimal(decimalValue.toPlainString()))
                        .build();

            case STRING:

                var stringValue = "random_string_" + random.nextLong();

                return Value.newBuilder()
                        .setType(typeDescriptor)
                        .setStringValue(stringValue)
                        .build();

            case DATE:

                var dateValue = MetadataCodec.encodeDate(LocalDate.now());

                return Value.newBuilder()
                        .setType(typeDescriptor)
                        .setDateValue(dateValue)
                        .build();

            case DATETIME:

                var datetimeValue = MetadataCodec.encodeDatetime(Instant.now());

                return Value.newBuilder()
                        .setType(typeDescriptor)
                        .setDatetimeValue(datetimeValue)
                        .build();

            default:

                throw new EUnexpected();
        }

    }
}
