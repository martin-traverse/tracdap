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

package org.finos.tracdap.svc.orch.service;

import org.finos.tracdap.api.JobRequest;
import org.finos.tracdap.api.internal.RuntimeJobResult;
import org.finos.tracdap.api.internal.RuntimeJobStatus;
import org.finos.tracdap.common.exception.EUnexpected;
import org.finos.tracdap.common.grpc.RequestMetadata;
import org.finos.tracdap.common.grpc.UserMetadata;
import org.finos.tracdap.common.middleware.GrpcClientConfig;
import org.finos.tracdap.common.middleware.GrpcClientState;
import org.finos.tracdap.config.JobConfig;
import org.finos.tracdap.config.RuntimeConfig;
import org.finos.tracdap.metadata.JobDefinition;
import org.finos.tracdap.metadata.JobType;
import org.finos.tracdap.metadata.JobStatusCode;
import org.finos.tracdap.metadata.ObjectDefinition;
import org.finos.tracdap.metadata.TagHeader;

import java.io.*;
import java.util.HashMap;
import java.util.Map;


public class JobState implements Serializable, Cloneable {

    // Note: Do not keep exceptions in job state, not all exception classes are fully serializable
    // Serialization failures can mask real errors, especially for rare error conditions

    private final static long serialVersionUID = 1L;

    // Original request as received from the client
    String tenant;
    JobRequest jobRequest;

    RequestMetadata requestMetadata;
    UserMetadata userMetadata;

    // Middleware config for client calls
    // Client state is serialized with the job state and is always available
    // Client config is not saved but can be restored from client state when required
    GrpcClientState clientState;
    transient GrpcClientConfig clientConfig;

    // Identifiers
    String jobKey;
    TagHeader jobId;
    JobType jobType;

    // Status information
    JobStatusCode tracStatus;
    String cacheStatus;
    String statusMessage;
    String errorDetail;
    int retries;

    // Job definition and resources, built up by the job logic
    JobDefinition definition;
    Map<String, ObjectDefinition> resources = new HashMap<>();
    Map<String, TagHeader> resourceMapping = new HashMap<>();
    Map<String, TagHeader> resultMapping = new HashMap<>();

    // Input / output config files for communicating with the runtime
    JobConfig jobConfig;
    RuntimeConfig sysConfig;

    RuntimeJobStatus executorStatus;
    RuntimeJobResult executorResult;

    // Executor state data
    Serializable executorState;

    @Override
    public JobState clone() {

        try {

            var clone = (JobState) super.clone();

            clone.resources = new HashMap<>(this.resources);
            clone.resourceMapping = new HashMap<>(this.resourceMapping);
            clone.resultMapping = new HashMap<>(this.resultMapping);

            return clone;
        }
        catch (CloneNotSupportedException e) {
            throw new EUnexpected(e);
        }
    }
}
