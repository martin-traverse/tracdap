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
import org.finos.tracdap.common.config.ConfigManager;
import org.finos.tracdap.config.StorageConfig;
import org.finos.tracdap.common.exception.*;
import org.finos.tracdap.common.exec.*;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.*;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import io.kubernetes.client.util.Yaml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class KubernetesBatchExecutor implements IBatchExecutor<KubernetesBatchState> {

    public static final String JOB_YAML_CONFIG_KEY = "job.yaml";
    public static final String POD_YAML_CONFIG_KEY = "pod.yaml";
    public static final String SERVICE_YAML_CONFIG_KEY = "service.yaml";

    public static final String CONTAINER_NAME_CONFIG_KEY = "container.name";
    public static final String CONTAINER_TAG_CONFIG_KEY = "container.tag";
    public static final String JOB_NAMESPACE_CONFIG_KEY = "job.namespace";
    public static final String SERVICE_ADDRESS_CONFIG_KEY = "service.address";

    private static final String JOB_NAME_LABEL = "job-name";
    private static final String TRAC_JOB_KEY_LABEL = "trac-job-key";
    private static final String TRAC_STORAGE_KEY_LABEL = "trac-storage-key";

    private static final int DEFAULT_JOB_RETRIES = 0;
    private static final int MIN_SERVICE_PORT = 30000;
    private static final int MAX_SERVICE_PORT = 32000;

    private static final List<Feature> EXECUTOR_FEATURES = List.of(Feature.EXPOSE_PORT, Feature.STORAGE_MAPPING);

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final CoreV1Api coreClient;
    private final BatchV1Api batchClient;

    private final String containerName;
    private final String containerTag;
    private final String jobNamespace;
    private final String serviceAddress;

    private final V1Job jobTemplate;
    private final V1Pod podTemplate;
    private final V1Service serviceTemplate;

    private final AtomicInteger nextServicePort;

    public KubernetesBatchExecutor(Properties properties, ConfigManager configManager) {

        coreClient = new CoreV1Api();
        batchClient = new BatchV1Api();

        containerName = ConfigHelpers.readString("executor", properties, CONTAINER_NAME_CONFIG_KEY);
        containerTag = ConfigHelpers.readString("executor", properties, CONTAINER_TAG_CONFIG_KEY);
        jobNamespace = ConfigHelpers.readString("executor", properties, JOB_NAMESPACE_CONFIG_KEY);
        serviceAddress = ConfigHelpers.readString("executor", properties, SERVICE_ADDRESS_CONFIG_KEY, false);

        jobTemplate = loadYaml(properties, configManager, JOB_YAML_CONFIG_KEY, V1Job.class);
        podTemplate = loadYaml(properties, configManager, POD_YAML_CONFIG_KEY, V1Pod.class);
        serviceTemplate = loadYaml(properties, configManager, SERVICE_YAML_CONFIG_KEY, V1Service.class);

        nextServicePort = new AtomicInteger(MIN_SERVICE_PORT);
    }

    private <T> T loadYaml(Properties properties, ConfigManager configManager, String configKey, Class<T> configClass) {

        if (properties.containsKey(configKey)) {
            var jobYamlFile = ConfigHelpers.readString("executor", properties, configKey);
            var jobYaml = configManager.loadTextConfig(jobYamlFile);
            return Yaml.loadAs(jobYaml, configClass);
        }
        else
            return null;
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

        // No-op, nothing to do
    }

    @Override
    public boolean hasFeature(Feature feature) {
        return EXECUTOR_FEATURES.contains(feature);
    }

    @Override
    public KubernetesBatchState createBatch(String batchKey) {

        var batchState = new KubernetesBatchState();
        batchState.tracJobKey = batchKey;
        batchState.jobNamespace = jobNamespace;
        batchState.jobName = batchKey.toLowerCase();

        try {
            if (jobTemplate != null) {

                batchState.job = V1Job.fromJson(jobTemplate.toJson());
                if (batchState.job.getMetadata() == null)
                    batchState.job.setMetadata(new V1ObjectMeta());
                batchState.job.getMetadata().setName(batchState.jobName);
                batchState.job.getMetadata().putLabelsItem(TRAC_JOB_KEY_LABEL, batchState.jobName);
                if (batchState.job.getSpec() == null)
                    batchState.job.setSpec(new V1JobSpec());
                var backoffLimit = batchState.job.getSpec().getBackoffLimit();
                if (backoffLimit != null && backoffLimit > 0)
                    batchState.jobRetries = backoffLimit;
                else {
                    batchState.jobRetries = DEFAULT_JOB_RETRIES;
                    batchState.job.getSpec().setBackoffLimit(DEFAULT_JOB_RETRIES);
                }
            }

            if (podTemplate != null) {

                batchState.pod = V1Pod.fromJson(podTemplate.toJson());
                if (batchState.pod.getMetadata() == null)
                    batchState.pod.setMetadata(new V1ObjectMeta());
                batchState.pod.getMetadata().setName(batchState.jobName);
                batchState.pod.getMetadata().putLabelsItem(JOB_NAME_LABEL, batchState.jobName);
                batchState.pod.getMetadata().putLabelsItem(TRAC_JOB_KEY_LABEL, batchState.tracJobKey);

                // Save the pod name
                batchState.podName = batchState.pod.getMetadata().getName();

                // This is a bug in the current (v21) Java Kubernetes library
                // Overhead is set to the empty map instead of null, which means something different
                // https://github.com/kubernetes-client/java/issues/3076
                getPodSpec(batchState).setOverhead(null);
            }

            if (serviceTemplate != null) {

                batchState.service = V1Service.fromJson(serviceTemplate.toJson());
                if (batchState.service.getMetadata() == null)
                    batchState.service.setMetadata(new V1ObjectMeta());
                batchState.service.getMetadata().setName(batchState.jobName + "-service");
                batchState.service.getMetadata().putLabelsItem(JOB_NAME_LABEL, batchState.jobName);
                batchState.service.getMetadata().putLabelsItem(TRAC_JOB_KEY_LABEL, batchState.tracJobKey);
                if (batchState.service.getSpec() == null)
                    batchState.service.setSpec(new V1ServiceSpec());
                if (batchState.service.getSpec().getSelector() == null)
                    batchState.service.getSpec().setSelector(new HashMap<>());
                batchState.service.getSpec().getSelector().put(JOB_NAME_LABEL, batchState.jobName);
                // Assuming the first port mapping in the service YAML is for the runtime API
                if (batchState.service.getSpec().getPorts() != null && !batchState.service.getSpec().getPorts().isEmpty()) {
                    var portMapping = batchState.service.getSpec().getPorts().get(0);
                    portMapping.setPort(nextServicePort.getAndIncrement());
                    // Loop port number if the max is hit
                    if (nextServicePort.get() == MAX_SERVICE_PORT)
                        nextServicePort.set(MIN_SERVICE_PORT);
                }
            }
        }
        catch (IOException e) {
            // Should never happen, JSON source is already valid
            throw new EUnexpected(e);
        }

        var podSpec = getPodSpec(batchState);
        var container = podSpec.getContainers().get(0);

        if (containerName != null && containerTag != null) {
            var image = String.format("%s:%s", containerName, containerTag);
            container.setImage(image);
        }

        if (container.getPorts() != null && !container.getPorts().isEmpty()) {
            var containerPort = container.getPorts().get(0);
            var port = containerPort.getContainerPort();
        }

        return batchState;
    }

    public KubernetesBatchState configureBatchStorage(
            String batchKey, KubernetesBatchState batchState,
            StorageConfig storageConfig, Consumer<StorageConfig> storageUpdate) {

        // Find and map any volumes that are configured in Kubernetes
        // Depending on the deployment, some volumes may be mapped and not others
        // E.g. if volumes are using native cloud storage, it is fine to access that directly

        V1PersistentVolumeClaimList storageClaims;

        try {
            var request = coreClient.listNamespacedPersistentVolumeClaim(batchState.jobNamespace);
            request.labelSelector(TRAC_STORAGE_KEY_LABEL);
            storageClaims = request.execute();
        }
        catch (ApiException apiError) {

            var detailMessage = getErrorMessage(apiError);
            var message = String.format("Error querying Kubernetes volumes: %s", detailMessage);
            log.error(message);

            throw new EExecutorFailure(message, apiError);
        }

        var storageConfigBuilder = storageConfig.toBuilder();

        for (var storageClaim : storageClaims.getItems()) {

            var storageMetadata = storageClaim.getMetadata();

            // Should never happen - request used a label selector so labels should always exist
            if (storageMetadata == null || storageMetadata.getLabels() == null || storageClaim.getSpec() == null)
                throw new EUnexpected();

            var tracStorageKey = storageMetadata.getLabels().get(TRAC_STORAGE_KEY_LABEL);

            // Ignore any volumes not needed for this job (do not map anything extra)
            if (!storageConfig.containsBuckets(tracStorageKey))
                continue;

            var bucketConfig = storageConfig.getBucketsOrThrow(tracStorageKey).toBuilder();
            var bucketProps = new Properties();
            bucketProps.putAll(bucketConfig.getPropertiesMap());

            var volumeName = tracStorageKey.toLowerCase().replace("_", "-");
            var readOnly = ConfigHelpers.optionalBoolean(tracStorageKey, bucketProps, "readOnly", false);

            var claimSource = new V1PersistentVolumeClaimVolumeSource();
            claimSource.setClaimName(storageMetadata.getName());
            claimSource.setReadOnly(readOnly);

            var volume = new V1Volume();
            volume.setName(volumeName);
            volume.setPersistentVolumeClaim(claimSource);

            var mountPath = "/mnt/trac/storage/" + tracStorageKey;
            var mount = new V1VolumeMount();
            mount.setName(volumeName);
            mount.setMountPath(mountPath);

            addVolumeToPod(batchState, volume, mount);

            var storageBucket = storageConfig.getBucketsOrThrow(tracStorageKey).toBuilder();
            storageBucket.setProtocol("LOCAL");
            storageBucket.clearProperties();
            storageBucket.putProperties("rootPath", mountPath);
            storageBucket.putProperties("readOnly", Boolean.toString(readOnly));

            storageConfigBuilder.putBuckets(tracStorageKey, storageBucket.build());
        }

        storageUpdate.accept(storageConfigBuilder.build());

        return batchState;
    }

    @Override
    public KubernetesBatchState addVolume(String batchKey, KubernetesBatchState batchState, String volumeName, BatchVolumeType volumeType) {

        var volume = new V1Volume();
        volume.setName(volumeName);

        switch (volumeType) {

            case CONFIG_VOLUME:

                var configMapName = (batchKey + "-" + volumeName).toLowerCase();

                var configMetadata = new V1ObjectMeta();
                configMetadata.setName(configMapName);
                configMetadata.putLabelsItem(TRAC_JOB_KEY_LABEL, batchState.tracJobKey);

                var configMap = new V1ConfigMap();
                configMap.setMetadata(configMetadata);

                batchState.configMaps.put(volumeName, configMap);

                var configMapSource = new V1ConfigMapVolumeSource();
                configMapSource.setName(configMapName);
                volume.setConfigMap(configMapSource);

                break;

            case SCRATCH_VOLUME:

                var emptyDirSource = new V1EmptyDirVolumeSource();
                volume.setEmptyDir(emptyDirSource);

                break;

            case OUTPUT_VOLUME:

                // Should never be called, the executor does not advertise output volumes in its features
                throw new ETracInternal("Kubernetes executor does not support output volumes");

            default:
                throw new EUnexpected();
        }

        var mountPath = "/mnt/trac/" + volumeName;
        var mount = new V1VolumeMount();
        mount.setName(volumeName);
        mount.setMountPath(mountPath);

        return addVolumeToPod(batchState, volume, mount);
    }

    private KubernetesBatchState addVolumeToPod(KubernetesBatchState batchState, V1Volume volume, V1VolumeMount mount) {

        var podSpec = getPodSpec(batchState);
        var container = podSpec.getContainers().get(0);

        container.addVolumeMountsItem(mount);
        podSpec.addVolumesItem(volume);

        return batchState;
    }

    @Override
    public KubernetesBatchState addFile(String batchKey, KubernetesBatchState batchState, String volumeName, String fileName, byte[] fileContent) {

        var configMap = batchState.configMaps.get(volumeName);

        // Orchestrator should never ask for this, treat it as an internal error
        if (configMap == null) {
            var error = String.format("Cannot add files to volume [%s]: This is not a config volume", volumeName);
            throw new ETracInternal(error);
        }

        configMap.putBinaryDataItem(fileName, fileContent);

        return batchState;
    }

    @Override
    public KubernetesBatchState submitBatch(String batchKey, KubernetesBatchState batchState, BatchConfig batchConfig) {

        var launchCmd = batchConfig.getLaunchCmd();
        var launchArgs = batchConfig.getLaunchArgs();

        var commandArgs = Stream.concat(launchCmd.commandArgs().stream(), launchArgs.stream())
                .map(arg -> translateLaunchArg(batchState, arg));

        var command = Stream.concat(Stream.of(launchCmd.command()), commandArgs)
                .collect(Collectors.toList());

        var podSpec = getPodSpec(batchState);
        var container = podSpec.getContainers().get(0);
        container.setCommand(command);

        // TODO: Retry logic for submission failures
        // Currently any error during submit will fail with a non-retrying error
        // In fact some errors can be retried, we could raise a temporary failure in those cases
        // Then we'd need to check and only create resources that don't already exist

        try {

            for (var configMap : batchState.configMaps.values()) {
                if (configMap.getMetadata() != null) {
                    log.info("Creating config map [{}]", configMap.getMetadata().getName());
                    var configRequest = coreClient.createNamespacedConfigMap(batchState.jobNamespace, configMap);
                    var configResponse = configRequest.execute();
                    configMap.setMetadata(configResponse.getMetadata());
                }
            }

            for (var volumeClaim : batchState.volumeClaims.values()) {
                if (volumeClaim.getMetadata() != null) {
                    log.info("Creating volume claim [{}]", volumeClaim.getMetadata().getName());
                    var claimRequest = coreClient.createNamespacedPersistentVolumeClaim(batchState.jobNamespace, volumeClaim);
                    var claimResponse = claimRequest.execute();
                    volumeClaim.setMetadata(claimResponse.getMetadata());
                }
            }

            if (batchState.job != null)
                batchState.job = submitBatchAsJob(batchState);
            else
                batchState.pod = submitBatchAsPod(batchState);

            if (batchState.service != null)
                batchState.service = createBatchService(batchState);

            return batchState;
        }
        catch (ApiException apiError) {

            var detailMessage = getErrorMessage(apiError);
            var message = String.format("Failed to start Kubernetes batch for [%s]: %s", batchKey, detailMessage);
            log.error(message);

            throw new EExecutorFailure(message, apiError);
        }
    }

    private V1Job submitBatchAsJob(KubernetesBatchState batchState) throws ApiException {

        var metadata = batchState.job.getMetadata();

        // Should never happen, metadata is set up in createBatch()
        if (metadata == null)
            throw new EUnexpected();

        log.info("Creating job [{}]", metadata.getName());

        var request = batchClient.createNamespacedJob(batchState.jobNamespace, batchState.job);
        var response = request.execute();
        var responseMetadata = response.getMetadata();

        // A response without metadata is invalid (this should not happen in practice)
        if (responseMetadata == null)
            throw new EExecutorFailure("Invalid response from Kubernetes cluster (missing metadata)");

        log.info("Job created for [{}], namespace = [{}], UID = [{}]",
                responseMetadata.getName(),
                responseMetadata.getNamespace(),
                responseMetadata.getUid());

        log.debug(response.toString());

        return response;
    }

    private V1Pod submitBatchAsPod(KubernetesBatchState batchState) throws ApiException {

        var metadata = batchState.pod.getMetadata();

        // Should never happen, metadata is set up in createBatch()
        if (metadata == null)
            throw new EUnexpected();

        log.info("Creating pod [{}]", metadata.getName());

        var request = coreClient.createNamespacedPod(batchState.jobNamespace, batchState.pod);
        var response = request.execute();
        var responseMetadata = response.getMetadata();

        // A response without metadata is invalid (this should not happen in practice)
        if (responseMetadata == null)
            throw new EExecutorFailure("Invalid response from Kubernetes cluster (missing metadata)");

        log.info("Pod created for [{}], namespace = [{}], UID = [{}]",
                responseMetadata.getName(),
                responseMetadata.getNamespace(),
                responseMetadata.getUid());

        log.debug(response.toString());

        return response;
    }

    private V1Service createBatchService(KubernetesBatchState batchState) throws ApiException {

        var metadata = batchState.service.getMetadata();

        // Should never happen, metadata is set up in createBatch()
        if (metadata == null)
            throw new EUnexpected();

        log.info("Creating API service for [{}]", metadata.getName());

        var request = coreClient.createNamespacedService(batchState.jobNamespace, batchState.service);
        var response = request.execute();
        var responseMetadata = response.getMetadata();

        // A response without metadata is invalid (this should not happen in practice)
        if (responseMetadata == null)
            throw new EExecutorFailure("Invalid response from Kubernetes cluster (missing metadata)");

        log.info("API service created for [{}], namespace = [{}], UID = [{}]",
                responseMetadata.getName(),
                responseMetadata.getNamespace(),
                responseMetadata.getUid());

        log.debug(response.toString());

        return response;
    }

    @Override
    public KubernetesBatchState cancelBatch(String batchKey, KubernetesBatchState batchState) {

        // This should never be called, the executor does not advertise cancellation in its features
        throw new ETracInternal("Kubernetes executor does not support batch cancellation");
    }

    @Override
    public void deleteBatch(String batchKey, KubernetesBatchState batchState) {

        try {

            // For all resources, assume creationTimestamp means the resource is created in the cluster

            for (var configMap : batchState.configMaps.values()) {

                var configMetadata = configMap.getMetadata();

                if (configMetadata != null && configMetadata.getCreationTimestamp() != null) {

                    log.info("Deleting config map [{}]", configMetadata.getName());

                    var configRequest = coreClient.deleteNamespacedConfigMap(
                            configMetadata.getName(),
                            batchState.jobNamespace);

                    configRequest.execute();

                }
            }

            for (var volumeClaim : batchState.volumeClaims.values()) {

                var claimMetadata = volumeClaim.getMetadata();

                if (claimMetadata != null && claimMetadata.getCreationTimestamp() != null) {

                    log.info("Deleting volume claim [{}]", claimMetadata.getName());

                    var claimRequest = coreClient.deleteNamespacedPersistentVolumeClaim(
                            claimMetadata.getName(),
                            batchState.jobNamespace);

                    claimRequest.execute();
                }
            }

            if (batchState.job != null) {

                var jobMetadata = batchState.job.getMetadata();

                if (jobMetadata != null && jobMetadata.getCreationTimestamp() != null) {

                    log.info("Deleting job [{}]", batchState.job.getMetadata().getName());

                    var request = batchClient.deleteNamespacedJob(
                            batchState.job.getMetadata().getName(),
                            batchState.jobNamespace);

                    // Set propagation so the job pods will also get cleaned up
                    var options = new V1DeleteOptions();
                    options.setPropagationPolicy("Background");

                    request.body(options);
                    request.execute();
                }
            }

            if (batchState.pod != null) {

                var podMetadata = batchState.pod.getMetadata();

                if (podMetadata != null && podMetadata.getCreationTimestamp() != null) {

                    log.info("Deleting pod [{}]", batchState.pod.getMetadata().getName());

                    var request = coreClient.deleteNamespacedPod(
                            batchState.pod.getMetadata().getName(),
                            batchState.jobNamespace);

                    request.execute();
                }
            }

            if (batchState.service != null) {

                var serviceMetadata = batchState.service.getMetadata();

                if (serviceMetadata != null && serviceMetadata.getCreationTimestamp() != null) {

                    log.info("Deleting API service for [{}]", batchState.service.getMetadata().getName());

                    var request = coreClient.deleteNamespacedService(
                            batchState.service.getMetadata().getName(),
                            batchState.jobNamespace);

                    request.execute();
                }
            }
        }
        catch (ApiException apiError) {

            var detailMessage = getErrorMessage(apiError);
            var message = String.format("Failed to clean up Kubernetes batch for [%s]: %s", batchState.jobName, detailMessage);
            log.error(message);

            throw new EExecutorFailure(message, apiError);
        }
    }

    @Override
    public BatchStatus getBatchStatus(String batchKey, KubernetesBatchState batchState) {

        try {

            var batchStatus = batchState.job != null
                    ? getBatchStatusFromJob(batchKey, batchState)
                    : getBatchStatusFromPod(batchKey, batchState);

            if (batchStatus == null) {

                var message = String.format("Failed to poll Kubernetes batch for [%s]: Status unknown", batchKey);
                log.error(message);

                throw new EExecutorFailure(message);
            }

            return batchStatus;
        }
        catch (ApiException apiError) {

            var detailMessage = getErrorMessage(apiError);
            var message = String.format("Failed to poll Kubernetes batch for [%s]: %s", batchKey, detailMessage);
            log.error(message);

            throw new EExecutorFailure(message, apiError);
        }
    }

    private BatchStatus getBatchStatusFromJob(String batchKey, KubernetesBatchState batchState) throws ApiException {

        var jobStatusRequest = batchClient.readNamespacedJobStatus(batchState.jobName, batchState.jobNamespace);
        var jobResponse = jobStatusRequest.execute();
        var jobStatus = jobResponse.getStatus();

        if (jobStatus == null)
            return null;

        var batchStatus = translateJobStatus(jobStatus, batchState);

        // It is possible for pods to hang indefinitely due to scheduling / affinity rules
        // The job will still be reported as running though!
        // So, if there are active pods, we need to check they are really running

        var activePods = jobStatus.getActive();

        // No active pods means nothing can hang - no need to check pod status
        if (activePods == null || activePods == 0)
            return batchStatus;

        // Find all the live pods associated with this job
        var podSelector = String.format("job-name=%s", batchState.jobName);
        var podListRequest = coreClient.listNamespacedPod(batchState.jobNamespace);
        podListRequest.labelSelector(podSelector);

        var podList = podListRequest.execute();

        // Check for pods that failed scheduling
        // If it happens the job will not complete, report the error state
        for (var pod : podList.getItems()) {
            if (pod.getStatus() != null) {
                var failedScheduling = podFailedScheduling(pod.getStatus());
                if (failedScheduling.isPresent())
                    return failedScheduling.get();
            }
        }

        // No hung pods found - return the status reported by the job
        return batchStatus;
    }

    private BatchStatus getBatchStatusFromPod(String batchKey, KubernetesBatchState batchState) throws ApiException {

        var podStatusRequest = coreClient.readNamespacedPodStatus(batchState.podName, batchState.jobNamespace);
        var podStatusResponse = podStatusRequest.execute();
        var podStatus = podStatusResponse.getStatus();

        return podStatus != null ? translatePodStatus(podStatus) : null;
    }

    @Override
    public boolean hasOutputFile(String batchKey, KubernetesBatchState batchState, String volumeName, String fileName) {

        // Should never be called, the executor does not advertise output volumes in its features
        throw new ETracInternal("Kubernetes executor does not support output volumes");
    }

    @Override
    public byte[] getOutputFile(String batchKey, KubernetesBatchState batchState, String volumeName, String fileName) {

        // Should never be called, the executor does not advertise output volumes in its features
        throw new ETracInternal("Kubernetes executor does not support output volumes");
    }

    @Override
    public InetSocketAddress getBatchAddress(String batchKey, KubernetesBatchState batchState) {

        // TODO: This logic assumes the job has an associated service
        // Running inside the cluster, we should be able to use the active POD IP

        if (batchState.service != null && batchState.service.getSpec() != null) {

            var serviceSpec = batchState.service.getSpec();
            var serviceType = serviceSpec.getType();

            var ports = serviceSpec.getPorts();

            if (ports == null || ports.isEmpty())
                throw new ETracInternal("Batch address not available");

            String ip = null;
            int port = 0;

            if ("NodePort".equals(serviceType)) {
                var ips = serviceSpec.getExternalIPs();
                if (ips != null && !ips.isEmpty())
                    ip = ips.get(0);
                var portBoxed = serviceSpec.getPorts().get(0).getNodePort();
                if (portBoxed != null)
                    port = portBoxed;
            }
            else if ("Port".equals(serviceType)) {
                ip = serviceSpec.getClusterIP();
                port = ports.get(0).getPort();
            }
            else
                throw new ETracInternal("Batch address not available");

            if (ip == null && serviceAddress != null)
                ip = serviceAddress;;

            if (ip == null || port == 0)
                throw new ETracInternal("Batch address not available");

            return InetSocketAddress.createUnresolved(ip, port);
        }

        throw new ETracInternal("Batch address not available");
    }

    private String translateLaunchArg(KubernetesBatchState batchState, LaunchArg launchArg) {

        switch (launchArg.getArgType()) {

            case STRING:
                return launchArg.getStringArg();

            case PATH:

                var podSpec = getPodSpec(batchState);
                var container = podSpec.getContainers().get(0);

                var volumeMounts = container.getVolumeMounts() != null
                        ? container.getVolumeMounts()
                        : List.<V1VolumeMount>of();

                var volumeMount = volumeMounts.stream()
                        .filter(mount -> mount.getName().equals(launchArg.getPathVolume()))
                        .findFirst();

                // Sanity check - the orchestrator should never ask for this
                if (volumeMount.isEmpty()) {
                    throw new ETracInternal(String.format(
                            "Launch arg refers to volume [%s], which is not configured",
                            launchArg.getPathVolume()));
                }

                var mountPoint = volumeMount.get().getMountPath();
                var childPath = launchArg.getPathArg();

                return mountPoint + "/" + childPath;

            default:

                var msg = String.format(
                        "Command argument type [%s] is not supported by the Kubernetes executor",
                        launchArg.getArgType());

                log.error(msg);

                throw new ETracInternal(msg);
        }

    }

    private BatchStatus translatePodStatus(V1PodStatus podStatus) {

        if (podStatus == null || podStatus.getConditions() == null) {
            return null;
        }

        var phase = podStatus.getPhase();
        var statusMessage = podStatus.getMessage();

        BatchStatusCode statusCode;

        // Check for pods that failed scheduling - phase is Pending but they are hanging forever
        var failedScheduling = podFailedScheduling(podStatus);

        if (failedScheduling.isPresent())
            return failedScheduling.get();

        if ("Pending".equals(phase))
            statusCode = BatchStatusCode.QUEUED;
        else if ("Running".equals(phase))
            statusCode = BatchStatusCode.RUNNING;
        else if ("Succeeded".equals(phase))
            statusCode = BatchStatusCode.SUCCEEDED;
        else if ("Failed".equals(phase))
            statusCode = BatchStatusCode.FAILED;
        else
            statusCode = BatchStatusCode.STATUS_UNKNOWN;

        // Try to get a more detailed error message from the main (first) container for failures
        if (statusCode == BatchStatusCode.FAILED) {
            if (podStatus.getContainerStatuses() != null && !podStatus.getContainerStatuses().isEmpty()) {
                var container = podStatus.getContainerStatuses().get(0);
                if (container.getLastState() != null && container.getLastState().getTerminated() != null)
                    statusMessage = container.getLastState().getTerminated().getMessage();
            }
        }

        return new BatchStatus(statusCode, statusMessage);
    }

    private Optional<BatchStatus> podFailedScheduling(V1PodStatus podStatus) {

        if ("Pending".equals(podStatus.getPhase()) && podStatus.getConditions() != null) {

            var scheduleCondition = podStatus.getConditions()
                    .stream().filter(c -> "PodScheduled".equals(c.getType()))
                    .findFirst().orElse(null);

            // If a pod fails scheduling the job cannot complete
            // Report a failed status code, the orchestrator will call to delete the job
            if (scheduleCondition != null && "Unschedulable".equals(scheduleCondition.getReason())) {
                var failedStatus = new BatchStatus(BatchStatusCode.FAILED, scheduleCondition.getMessage());
                return Optional.of(failedStatus);
            }
        }

        return Optional.empty();
    }

    private BatchStatus translateJobStatus(V1JobStatus kubeStatus, KubernetesBatchState batchState) {

        var status = getJobStatus(kubeStatus, batchState);

        if (status != BatchStatusCode.FAILED)
            return new BatchStatus(status);

        if (kubeStatus.getConditions() != null) {
            for (var i = kubeStatus.getConditions().size() - 1; i >= 0; i--) {

                var condition = kubeStatus.getConditions().get(i);

                if ("failed".equalsIgnoreCase(condition.getType())) {
                    var message = condition.getMessage();
                    return new BatchStatus(status, message);
                }
            }
        }

        var message = "No further information available";

        return new BatchStatus(status, message);
    }

    private BatchStatusCode getJobStatus(V1JobStatus kubeStatus, KubernetesBatchState batchState) {

        var ready = kubeStatus.getReady();
        var active = kubeStatus.getActive();
        var succeeded = kubeStatus.getSucceeded();
        var failed = kubeStatus.getFailed();

        if (failed != null && failed > batchState.jobRetries)
            return BatchStatusCode.FAILED;

        if (succeeded != null && succeeded > 0)
            return BatchStatusCode.SUCCEEDED;

        if (active != null && active > 0)
            return BatchStatusCode.RUNNING;

        if (ready != null && ready > 0)
            return BatchStatusCode.QUEUED;

        log.info(kubeStatus.toJson());

        return BatchStatusCode.STATUS_UNKNOWN;
    }

    private String getErrorMessage(ApiException apiError) {

        var errorStatus = getErrorStatus(apiError);

        if (errorStatus != null)
            return errorStatus.getMessage();
        // Error code != 0 means there was an HTTP error code
        else if (apiError.getCode() != 0)
            return String.format("Cluster returned HTTP %d", apiError.getCode());
        // Error code == 0 means HTTP communication failed, cause will be more relevant
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

    private V1PodSpec getPodSpec(KubernetesBatchState batchState) {

        V1PodSpec podSpec = null;

        if (batchState.job != null) {

            var jobSpec = batchState.job.getSpec();
            var podTemplate = jobSpec != null ? jobSpec.getTemplate() : null;

            podSpec = podTemplate != null ? podTemplate.getSpec() : null;
        }

        if (batchState.pod != null) {

            podSpec =  batchState.pod.getSpec();
        }

        if (podSpec == null || podSpec.getContainers().isEmpty())
            throw new EUnexpected();

        return podSpec;
    }
}
