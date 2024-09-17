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

import io.kubernetes.client.openapi.models.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;


public class KubernetesBatchState implements Serializable {

    private static final long serialVersionUID = 1L;

    String tracJobKey;

    String jobNamespace;
    String jobName;
    String podName;

    int jobRetries;

    transient V1Job job;
    transient V1Pod pod;
    transient V1Service service;

    transient Map<String, V1ConfigMap> configMaps = new HashMap<>();
    transient Map<String, V1PersistentVolumeClaim> volumeClaims = new HashMap<>();

    private void writeObject(ObjectOutputStream oos) throws IOException {

        oos.defaultWriteObject();

        oos.writeUTF(job != null ? job.toJson() : "");
        oos.writeUTF(pod != null ? pod.toJson() : "");
        oos.writeUTF(service != null ? service.toJson() : "");

        oos.writeInt(configMaps.size());

        for (var configEntry : configMaps.entrySet()) {
            oos.writeUTF(configEntry.getKey());
            oos.writeUTF(configEntry.getValue().toJson());
        }

        oos.writeInt(volumeClaims.size());

        for (var volumeEntry : volumeClaims.entrySet()) {
            oos.writeUTF(volumeEntry.getKey());
            oos.writeUTF(volumeEntry.getValue().toJson());
        }
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {

        ois.defaultReadObject();

        job = V1Job.fromJson(ois.readUTF());
        pod = V1Pod.fromJson(ois.readUTF());
        service = V1Service.fromJson(ois.readUTF());

        var configSize = ois.readInt();
        configMaps = new HashMap<>(configSize);

        for (int i = 0; i < configSize; i++) {
            var key = ois.readUTF();
            var configMap = V1ConfigMap.fromJson(ois.readUTF());
            configMaps.put(key, configMap);
        }

        var volumeClaimsSize = ois.readInt();
        volumeClaims = new HashMap<>(volumeClaimsSize);

        for (int i = 0; i < volumeClaimsSize; i++) {
            var key = ois.readUTF();
            var volumeClaim = V1PersistentVolumeClaim.fromJson(ois.readUTF());
            volumeClaims.put(key, volumeClaim);
        }
    }
}
