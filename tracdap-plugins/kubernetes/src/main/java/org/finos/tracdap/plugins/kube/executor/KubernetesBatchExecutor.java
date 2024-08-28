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

package org.finos.tracdap.plugins.kube.executor;

import org.finos.tracdap.common.config.ConfigHelpers;
import org.finos.tracdap.common.exception.EExecutorFailure;
import org.finos.tracdap.common.exception.EStartup;
import org.finos.tracdap.common.exception.EUnexpected;
import org.finos.tracdap.common.exec.*;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.VersionApi;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;


public class KubernetesBatchExecutor implements IBatchExecutor<KubernetesBatchState> {

    public static final String CONTAINER_NAME_CONFIG_KEY = "container.name";
    public static final String CONTAINER_TAG_CONFIG_KEY = "container.tag";
    public static final String JOB_NAMESPACE_CONFIG_KEY = "job.namespace";

    private static final String KUBERNETES_RESTART_POLICY_NEVER = "Never";

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final CoreV1Api coreClient;
    private final BatchV1Api batchClient;

    private final String containerName;
    private final String containerTag;
    private final String jobNamespace;

    public KubernetesBatchExecutor(Properties properties) {

        coreClient = new CoreV1Api();
        batchClient = new BatchV1Api();

        containerName = ConfigHelpers.readString("executor", properties, CONTAINER_NAME_CONFIG_KEY);
        containerTag = ConfigHelpers.readString("executor", properties, CONTAINER_TAG_CONFIG_KEY);

        jobNamespace = ConfigHelpers.readString("executor", properties, JOB_NAMESPACE_CONFIG_KEY);
    }

    @Override
    public void start() {

        try {

            log.info("Kubernetes executor starting, checking cluster connection...");

            // Set up the API client using the default config resolution mechanism
            var apiClient = Config.defaultClient();
            coreClient.setApiClient(apiClient);
            batchClient.setApiClient(apiClient);

            log.info("Cluster address: [{}]", apiClient.getBasePath());

            // Get cluster version info to test connection
            var versionClient = new VersionApi(apiClient);
            var versionRequest = versionClient.getCode();
            var versionResponse = versionRequest.execute();

            log.info("Cluster version: [{}]", versionResponse.getGitVersion());

            // Check the execution namespace
            var namespaceRequest = coreClient.readNamespace(jobNamespace);
            var namespaceResponse = namespaceRequest.execute();
            var namespaceStatus = namespaceResponse.getStatus();

            if (namespaceStatus == null) {

                var message = String.format("Job namespace [%s] has unknown status", jobNamespace);
                log.error(message);

                throw new EStartup(message);
            }

            log.info("Job namespace [{}] is in phase [{}]", jobNamespace, namespaceStatus.getPhase());
        }
        catch (IOException configError) {

            var message = String.format("Failed to load Kubernetes config: %s", configError.getMessage());
            log.error(message);

            throw new EStartup(message, configError);
        }
        catch (ApiException apiError) {

            var detailMessage = getErrorMessage(apiError);
            var message = String.format("Failed to start Kubernetes executor: %s", detailMessage);
            log.error(message);

            throw new EStartup(message, apiError);
        }
    }

    @Override
    public void stop() {

    }

    @Override
    public Class<KubernetesBatchState> stateClass() {
        return KubernetesBatchState.class;
    }

    @Override
    public KubernetesBatchState createBatch(String batchKey) {

        var name = containerName.replaceFirst("^(.*/)*", "");
        var image = String.format("%s:%s", containerName, containerTag);

        var container = new V1Container();
        container.setName(name);
        container.setImage(image);

        var podSpec = new V1PodSpec();
        podSpec.addContainersItem(container);
        podSpec.setRestartPolicy(KUBERNETES_RESTART_POLICY_NEVER);

        var batchState = new KubernetesBatchState();
        batchState.jobNamespace = jobNamespace;
        batchState.podSpec = podSpec;

        return batchState;
    }

    @Override
    public void destroyBatch(String batchKey, KubernetesBatchState batchState) {

    }

    @Override
    public KubernetesBatchState createVolume(String batchKey, KubernetesBatchState batchState, String volumeName, ExecutorVolumeType volumeType) {

        var volume = new V1Volume();

        switch (volumeType) {

            case CONFIG_DIR:

                // var configMap = new V1ConfigMap();
                // batchState.configVolumes.put(volumeName, configMap);

                var configMapSource = new V1ConfigMapVolumeSource();
                volume.setConfigMap(configMapSource);

                break;

            case SCRATCH_DIR:

                var emptyDirSource = new V1EmptyDirVolumeSource();
                volume.setEmptyDir(emptyDirSource);

                break;

            case RESULT_DIR:

                // var persistentClaim = new V1PersistentVolumeClaim();

                var persistentSource = new V1PersistentVolumeClaimVolumeSource();
                volume.setPersistentVolumeClaim(persistentSource);

                break;

        }

        // batchState.podSpec.addVolumesItem(volume);

        return batchState;
    }

    @Override
    public KubernetesBatchState writeFile(String batchKey, KubernetesBatchState batchState, String volumeName, String fileName, byte[] fileContent) {

//        var configMap = batchState.configVolumes.get(volumeName);
//
//        if (configMap == null)
//            throw new EUnexpected();  // TODO
//
//        configMap.putBinaryDataItem(fileName, fileContent);

        return batchState;
    }

    @Override
    public byte[] readFile(String batchKey, KubernetesBatchState batchState, String volumeName, String fileName) {
        return new byte[0];
    }

    @Override
    public KubernetesBatchState startBatch(String batchKey, KubernetesBatchState batchState, LaunchCmd launchCmd, List<LaunchArg> launchArgs) {

        var templateSpec = new V1PodTemplateSpec();
        templateSpec.setSpec(batchState.podSpec);

        var jobSpec = new V1JobSpec();
        jobSpec.setTemplate(templateSpec);
        jobSpec.setBackoffLimit(0);  // Do not retry on failure (TODO)

        var jobMetadata = new V1ObjectMeta();
        jobMetadata.setName(batchKey.toLowerCase());

        var job = new V1Job();
        job.setMetadata(jobMetadata);
        job.setSpec(jobSpec);

        try {

            var request = batchClient.createNamespacedJob(batchState.jobNamespace, job);
            var response = request.execute();
            var responseMetadata = response.getMetadata();

            if (responseMetadata == null) {
                throw new EUnexpected();  // TODO
            }

            log.info("Submitted job [{}], namespace = [{}], UID = [{}]",
                    responseMetadata.getName(),
                    responseMetadata.getNamespace(),
                    responseMetadata.getUid());

            log.debug(response.toString());

            batchState.jobName = responseMetadata.getName();
            batchState.job = response;  // The response is a V1Job object

            return batchState;
        }
        catch (ApiException apiError) {

            var detailMessage = getErrorMessage(apiError);
            var message = String.format("Failed to start Kubernetes batch for [%s]: %s", batchKey, detailMessage);
            log.error(message);

            throw new EExecutorFailure(message, apiError);
        }
    }

    @Override
    public ExecutorJobInfo pollBatch(String batchKey, KubernetesBatchState batchState) {

        try {

            var jobStatusRequest = batchClient.readNamespacedJobStatus(batchState.jobName, batchState.jobNamespace);
            var jobResponse = jobStatusRequest.execute();
            var jobStatus = jobResponse.getStatus();

            if (jobStatus == null) {

                var message = String.format("Failed to poll Kubernetes batch for [%s]: Status unknown", batchKey);
                log.error(message);

                throw new EExecutorFailure(message);
            }

            return getJobInfo(jobStatus);
        }
        catch (ApiException apiError) {

            var detailMessage = getErrorMessage(apiError);
            var message = String.format("Failed to poll Kubernetes batch for [%s]: %s", batchKey, detailMessage);
            log.error(message);

            throw new EExecutorFailure(message, apiError);
        }
    }

    @Override
    public List<ExecutorJobInfo> pollBatches(List<Map.Entry<String, KubernetesBatchState>> batches) {

        var results = new ArrayList<ExecutorJobInfo>();

        for (var batch : batches) {

            try {

                var priorState = batch.getValue();
                var pollResult = pollBatch(batch.getKey(), priorState);

                results.add(pollResult);
            }
            catch (Exception e) {

                log.warn("Failed to poll job: [{}] {}", batch.getKey(), e.getMessage(), e);
                results.add(new ExecutorJobInfo(ExecutorJobStatus.STATUS_UNKNOWN));
            }
        }

        return results;
    }

    private ExecutorJobInfo getJobInfo(V1JobStatus kubeStatus) {

        var status = getJobStatus(kubeStatus);

        if (status != ExecutorJobStatus.FAILED)
            return new ExecutorJobInfo(status);

        if (kubeStatus.getConditions() != null) {
            for (var i = kubeStatus.getConditions().size() - 1; i >= 0; i--) {

                var condition = kubeStatus.getConditions().get(i);

                if ("failed".equalsIgnoreCase(condition.getType())) {
                    var message = condition.getMessage();
                    var detail = condition.getReason();  // TODO: Detail should be job outputs
                    return new ExecutorJobInfo(status, message, detail);
                }
            }
        }

        var message = "No further information available";
        var detail = "";

        return new ExecutorJobInfo(status, message, detail);
    }

    private ExecutorJobStatus getJobStatus(V1JobStatus kubeStatus) {

        var ready = kubeStatus.getReady();
        var active = kubeStatus.getActive();
        var succeeded = kubeStatus.getSucceeded();
        var failed = kubeStatus.getFailed();

        if (failed != null && failed > 0)  // TODO: Retries
            return ExecutorJobStatus.FAILED;

        if (succeeded != null && succeeded > 0)
            return ExecutorJobStatus.SUCCEEDED;

        if (active != null && active > 0)
            return ExecutorJobStatus.RUNNING;

        if (ready != null && ready > 0)
            return ExecutorJobStatus.QUEUED;

        return ExecutorJobStatus.STATUS_UNKNOWN;
    }

    private String getErrorMessage(ApiException apiError) {

        var errorStatus = getErrorStatus(apiError);

        if (errorStatus != null)
            return errorStatus.getMessage();
        else if (apiError.getCode() != 0)
            return String.format("Cluster returned HTTP %d", apiError.getCode());
        else if (apiError.getCause() != null)
            return apiError.getCause().getMessage();
        else
            return apiError.getMessage();
    }

    private V1Status getErrorStatus(ApiException apiError) {

        var responseBody = apiError.getResponseBody();

        if (responseBody == null || responseBody.isBlank())
            return null;

        try {
            return V1Status.fromJson(responseBody);
        }
        catch (IOException decodeError) {

            log.error("Failed to decode error status, error details may not be available");
            log.error(decodeError.getMessage(), decodeError);

            return null;
        }
    }
}
