/*
 * Copyright 2024 Accenture Global Solutions Limited
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

package org.finos.tracdap.common.exec;

import org.finos.tracdap.api.internal.*;
import org.finos.tracdap.common.config.ConfigFormat;
import org.finos.tracdap.common.config.ConfigParser;
import org.finos.tracdap.common.exception.*;
import org.finos.tracdap.common.grpc.CompressionClientInterceptor;
import org.finos.tracdap.common.grpc.GrpcChannelFactory;
import org.finos.tracdap.common.grpc.LoggingClientInterceptor;
import org.finos.tracdap.common.metadata.MetadataUtil;
import org.finos.tracdap.config.JobConfig;
import org.finos.tracdap.config.JobResult;
import org.finos.tracdap.config.RuntimeConfig;
import org.finos.tracdap.config.ServiceConfig;
import org.finos.tracdap.metadata.JobStatusCode;
import org.finos.tracdap.metadata.TagHeader;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.regex.Pattern;


public class BatchJobExecutor<TBatchState extends Serializable> implements IJobExecutor<BatchJobState<TBatchState>> {

    private static final Pattern TRAC_ERROR_LINE = Pattern.compile("tracdap.rt.exceptions.(E\\w+): (.+)");

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final IBatchExecutor<TBatchState> batchExecutor;

    private GrpcChannelFactory channelFactory;

    public BatchJobExecutor(IBatchExecutor<TBatchState> batchExecutor) {
        this.batchExecutor = batchExecutor;
    }

    @Override
    public void start(GrpcChannelFactory channelFactory) {
        this.channelFactory = channelFactory;
        batchExecutor.start();
    }

    @Override
    public void stop() {
        batchExecutor.stop();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<BatchJobState<TBatchState>> stateClass() {
        return (Class<BatchJobState<TBatchState>>) (Object) BatchJobState.class;
    }

    @Override
    public BatchJobState<TBatchState> submitJob() {
        throw new ETracInternal("Not implemented yet");
    }

    @Override
    public BatchJobState<TBatchState> submitOneshotJob(TagHeader jobId, JobConfig jobConfig, RuntimeConfig sysConfig) {

        var runtimeApiEnabled = batchExecutor.hasFeature(IBatchExecutor.Feature.REMOTE_API);
        var resultVolumesEnabled = batchExecutor.hasFeature(IBatchExecutor.Feature.OUTPUT_VOLUMES);
        var logVolumesEnabled = batchExecutor.hasFeature(IBatchExecutor.Feature.OUTPUT_VOLUMES);

        var runtimeApiConfig = ServiceConfig.newBuilder()
                .setEnabled(runtimeApiEnabled)
                .setPort(9000)  // TODO
                .clearAlias()
                .build();

        sysConfig = sysConfig.toBuilder()
                .setRuntimeApi(runtimeApiConfig)
                .build();

        var jobConfigJson = ConfigParser.quoteConfig(jobConfig, ConfigFormat.JSON);
        var sysConfigJson = ConfigParser.quoteConfig(sysConfig, ConfigFormat.JSON);

        var batchKey = MetadataUtil.objectKey(jobId);
        var batchState = batchExecutor.createBatch(batchKey);

        batchState = batchExecutor.addVolume(batchKey, batchState, "config", BatchVolumeType.CONFIG_VOLUME);
        batchState = batchExecutor.addFile(batchKey, batchState, "config", "job_config.json", jobConfigJson);
        batchState = batchExecutor.addFile(batchKey, batchState, "config", "sys_config.json", sysConfigJson);

        batchState = batchExecutor.addVolume(batchKey, batchState, "scratch", BatchVolumeType.SCRATCH_VOLUME);

        var batchConfig = BatchConfig.forCommand(
                LaunchCmd.trac(), List.of(
                LaunchArg.string("--sys-config"), LaunchArg.path("config", "sys_config.json"),
                LaunchArg.string("--job-config"), LaunchArg.path("config", "job_config.json"),
                LaunchArg.string("--scratch-dir"), LaunchArg.path("scratch", ".")));

        if (resultVolumesEnabled) {
            batchState = batchExecutor.addVolume(batchKey, batchState, "result", BatchVolumeType.RESULT_VOLUME);
            batchConfig.addExtraArgs(List.of(
                    LaunchArg.string("--job-result-dir"), LaunchArg.path("result", "."),
                    LaunchArg.string("--job-result-format"), LaunchArg.string("json")));
        }

        if (logVolumesEnabled) {
            batchState = batchExecutor.addVolume(batchKey, batchState, "log", BatchVolumeType.RESULT_VOLUME);
            batchConfig.addLoggingRedirect(
                    LaunchArg.path("log", "trac_rt_stdout.log"),
                    LaunchArg.path("log", "trac_rt_stderr.log"));
        }

        batchState = batchExecutor.submitBatch(batchKey, batchState, batchConfig);

        var jobState = new BatchJobState<TBatchState>();
        jobState.batchKey = batchKey;
        jobState.batchState = batchState;
        jobState.runtimeApiEnabled = runtimeApiEnabled;
        jobState.resultVolumeEnabled = resultVolumesEnabled;
        jobState.logVolumeEnabled = logVolumesEnabled;

        // TODO: Get runtime API address

        return jobState;
    }

    @Override
    public BatchJobState<TBatchState> submitExternalJob() {
        throw new ETracInternal("Not implemented yet");
    }

    @Override
    public BatchJobState<TBatchState> cancelJob(BatchJobState<TBatchState> jobState) {
        jobState.batchState = batchExecutor.cancelBatch(jobState.batchKey, jobState.batchState);
        return jobState;
    }

    @Override
    public void deleteJob(BatchJobState<TBatchState> jobState) {
        batchExecutor.deleteBatch(jobState.batchKey, jobState.batchState);
    }

    @Override
    public List<RuntimeJobStatus> listJobs() {
        return List.of();
    }

    @Override
    public RuntimeJobStatus getJobStatus(BatchJobState<TBatchState> jobState) {

        var batchStatus = batchExecutor.getBatchStatus(jobState.batchKey, jobState.batchState);

        // Prefer using the API server to get the job status, if it is available
        if (jobState.runtimeApiEnabled && batchStatus.getStatusCode() == BatchStatusCode.RUNNING)
            return getStatusFromApi(jobState);

        // Otherwise build job status based on the batch status
        var jobStatus = RuntimeJobStatus.newBuilder()
                .setStatusCode(mapStatusCode(batchStatus.getStatusCode()))
                .setStatusMessage(batchStatus.getStatusMessage());
                // error detail;  TODO

        if (jobState.logVolumeEnabled && batchStatus.getStatusCode() == BatchStatusCode.FAILED)
            updateStatusFromLogs(jobState, jobStatus);

        return jobStatus.build();
    }

    private JobStatusCode mapStatusCode(BatchStatusCode batchStatusCode) {

        switch (batchStatusCode) {

            case QUEUED: return JobStatusCode.SUBMITTED;
            case RUNNING: return JobStatusCode.RUNNING;
            case COMPLETE: return JobStatusCode.FINISHING;
            case SUCCEEDED: return JobStatusCode.SUCCEEDED;
            case FAILED: return JobStatusCode.FAILED;
            case CANCELLED: return JobStatusCode.CANCELLED;

            case STATUS_UNKNOWN:
            default:
                return JobStatusCode.UNRECOGNIZED;
        }
    }

    private RuntimeJobStatus getStatusFromApi(BatchJobState<TBatchState> jobState) {

        var runtimeRequest = RuntimeJobInfoRequest.newBuilder()
                .setJobKey(jobState.batchKey)
                .build();

        var runtimeChannel = (ManagedChannel) null;

        try {

            runtimeChannel = channelFactory.createChannel(jobState.runtimeApiAddress);
            var runtimeApi = getRuntimeApi(runtimeChannel);

            return runtimeApi.getJobStatus(runtimeRequest);
        }
        catch (StatusRuntimeException e) {
            // TODO
            throw new ETracInternal(e.getMessage());
        }
        finally {
            if (runtimeChannel != null)
                runtimeChannel.shutdown();
        }
    }

    private void updateStatusFromLogs(BatchJobState<TBatchState> jobState, RuntimeJobStatus.Builder batchJobStatus) {

        // If the error log is not available, fall back on the basic batch status
        // Typically this is a lot less useful (e.g. "Batch failed, exit code 5")!

        var logsAvailable = batchExecutor.hasOutputFile(
                jobState.batchKey, jobState.batchState,
                "log", "trac_rt_stderr.log");

        if (!logsAvailable)
            return;

        // If a log file is available, fetch it and look for a TRAC exception message

        var stdErrBytes = batchExecutor.getOutputFile(
                jobState.batchKey, jobState.batchState,
                "log", "trac_rt_stderr.log");

        var stdErrText = new String(stdErrBytes, StandardCharsets.UTF_8);
        var statusMessage = extractErrorFromLogs(stdErrText);

        batchJobStatus.setStatusMessage(statusMessage);
                // detail
    }

    @Override
    public Flow.Publisher<RuntimeJobStatus> followJobStatus(BatchJobState<TBatchState> jobState) {
        return null;
    }

    @Override
    public RuntimeJobResult getJobResult(BatchJobState<TBatchState> jobState) {

        var batchStatus = batchExecutor.getBatchStatus(jobState.batchKey, jobState.batchState);

        if (jobState.runtimeApiEnabled && batchStatus.getStatusCode() == BatchStatusCode.RUNNING)
            return getResultFromApi(jobState);

        if (jobState.resultVolumeEnabled && batchStatus.getStatusCode() == BatchStatusCode.COMPLETE)
            return getResultFromResultFile(jobState);

        throw new ETracInternal("Not finished");  // TODO
    }

    private RuntimeJobResult getResultFromApi(BatchJobState<TBatchState> jobState) {

        var runtimeChannel = (ManagedChannel) null;

        try {

            runtimeChannel = channelFactory.createChannel(jobState.runtimeApiAddress);
            var runtimeApi = getRuntimeApi(runtimeChannel);

            var runtimeRequest = RuntimeJobInfoRequest.newBuilder()
                    .setJobKey(jobState.batchKey)
                    .build();

            return runtimeApi.getJobDetails(runtimeRequest);
        }
        catch (StatusRuntimeException e) {

            // TODO: Real error handling

            switch (e.getStatus().getCode()) {

                case UNAVAILABLE:
                    throw new EExecutorUnavailable(e.getMessage(), e);

                case UNAUTHENTICATED:
                case PERMISSION_DENIED:
                    throw new EExecutorAccess(e.getMessage(), e);

                case INVALID_ARGUMENT:
                case FAILED_PRECONDITION:
                    throw new EExecutorValidation(e.getMessage(), e);

                default:
                    throw new EExecutorFailure(e.getMessage(), e);
            }
        }
        finally {
            if (runtimeChannel != null)
                runtimeChannel.shutdown();
        }
    }

    private RuntimeJobResult getResultFromResultFile(BatchJobState<TBatchState> jobState) {

        try {

            var resultFile = String.format("job_result_%s.json", jobState.batchKey);
            var resultBytes = batchExecutor.getOutputFile(jobState.batchKey, jobState.batchState, "result", resultFile);

            var batchResult = ConfigParser.parseConfig(resultBytes, ConfigFormat.JSON, JobResult.class);

            return RuntimeJobResult.newBuilder()
                    .setJobId(batchResult.getJobId())
                    .setStatusCode(batchResult.getStatusCode())
                    .setStatusMessage(batchResult.getStatusMessage())
                    .putAllResults(batchResult.getResultsMap())
                    .build();
        }
        catch (EConfigParse e) {

            // Parsing and validation failures mean the job has definitely failed
            // Handle these as part of the result processing
            // These are not executor / communication errors and should not be retried

            var errorMessage = e.getMessage();
            var shortMessage = errorMessage.lines().findFirst().orElse("No details available");

            // TODO: Internal validation class
            throw new EExecutorValidation("Invalid job result: " + shortMessage, e);
        }
    }

    private TracRuntimeApiGrpc.TracRuntimeApiBlockingStub getRuntimeApi(Channel clientChannel) {

        return TracRuntimeApiGrpc
                .newBlockingStub(clientChannel)
                .withCompression(CompressionClientInterceptor.COMPRESSION_TYPE)
                .withInterceptors(new CompressionClientInterceptor())
                .withInterceptors(new LoggingClientInterceptor(BatchJobExecutor.class));
    }

    private String extractErrorFromLogs(String stdErrText) {

        var lastLineIndex = stdErrText.stripTrailing().lastIndexOf("\n");
        var lastLine = stdErrText.substring(lastLineIndex + 1).stripTrailing();

        var tracError = TRAC_ERROR_LINE.matcher(lastLine);

        if (tracError.matches()) {

            var exception = tracError.group(1);
            var message = tracError.group(2);

            log.error("Runtime error [{}]: {}", exception, message);
            return message;
        }
        else {

            return null;  // TODO String.format(FALLBACK_ERROR_MESSAGE, exitCode);
        }
    }
}
