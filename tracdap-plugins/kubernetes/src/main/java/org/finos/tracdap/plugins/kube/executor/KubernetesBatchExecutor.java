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

import io.kubernetes.client.openapi.apis.VersionApi;
import org.finos.tracdap.common.exception.EStartup;
import org.finos.tracdap.common.exception.EUnexpected;
import org.finos.tracdap.common.exec.*;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;


public class KubernetesBatchExecutor implements IBatchExecutor<KubernetesBatchState> {

    private static final int TRUNCATE_STARTUP_NS = 10;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final CoreV1Api coreClient;
    private final BatchV1Api batchClient;

    public KubernetesBatchExecutor(Properties properties) {

        coreClient = new CoreV1Api();
        batchClient = new BatchV1Api();
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

            // List available cluster namespaces
            var ping = coreClient.listNamespace();
            var pong = ping.execute();

            log.info("Found {} namespaces:", pong.getItems().size());

            var nsCount = 0;

            for (var ns : pong.getItems()) {
                if (nsCount >= TRUNCATE_STARTUP_NS) {
                    log.info("... (truncated)");
                    break;
                }
                if (ns.getMetadata() != null) {
                    log.info(">>> {}", ns.getMetadata().getName());
                    nsCount++;
                }
            }
        }
        catch (ApiException e) {
            throw new EStartup("Failed to start Kubernetes executor", e);
        }
        catch (IOException e) {
            throw new EStartup("Failed to start Kubernetes executor", e);
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

        var container = new V1Container();

        var podSpec = new V1PodSpec();
        podSpec.addContainersItem(container);

        var batchState = new KubernetesBatchState();
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

                var configMap = new V1ConfigMap();
                batchState.configVolumes.put(volumeName, configMap);

                var configMapSource = new V1ConfigMapVolumeSource();
                volume.setConfigMap(configMapSource);

                break;

            case SCRATCH_DIR:

                var emptyDirSource = new V1EmptyDirVolumeSource();
                volume.setEmptyDir(emptyDirSource);

                break;

            case RESULT_DIR:

                var persistentClaim = new V1PersistentVolumeClaim();

                var persistentSource = new V1PersistentVolumeClaimVolumeSource();
                volume.setPersistentVolumeClaim(persistentSource);

                break;

        }

        batchState.podSpec.addVolumesItem(volume);

        return batchState;
    }

    @Override
    public KubernetesBatchState writeFile(String batchKey, KubernetesBatchState batchState, String volumeName, String fileName, byte[] fileContent) {

        var configMap = batchState.configVolumes.get(volumeName);

        if (configMap == null)
            throw new EUnexpected();  // TODO

        configMap.putBinaryDataItem(fileName, fileContent);

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

        var job = new V1Job();
        job.setSpec(jobSpec);

        var jobRequest = batchClient.createNamespacedJob(batchState.namespace, job);
        // var jobResult = jobRequest.execute();

        return batchState;
    }

    @Override
    public ExecutorJobInfo pollBatch(String batchKey, KubernetesBatchState batchState) {
        return null;
    }

    @Override
    public List<ExecutorJobInfo> pollBatches(List<Map.Entry<String, KubernetesBatchState>> batches) {
        return List.of();
    }
}
