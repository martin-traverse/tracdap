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

package org.finos.tracdap.svc.orch.test;

import org.finos.tracdap.common.exec.*;

import java.util.List;
import java.util.Map;


public class UnitTestExecutor implements IBatchExecutor<UnitTestExecutorState> {

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public Class<UnitTestExecutorState> stateClass() {
        return null;
    }

    @Override
    public boolean hasFeature(Feature feature) {
        return false;
    }

    @Override
    public UnitTestExecutorState createBatch(String batchKey) {
        return null;
    }

    @Override
    public UnitTestExecutorState addVolume(String batchKey, UnitTestExecutorState batchState, String volumeName, BatchVolumeType volumeType) {
        return null;
    }

    @Override
    public UnitTestExecutorState addFile(String batchKey, UnitTestExecutorState batchState, String volumeName, String fileName, byte[] fileContent) {
        return null;
    }

    @Override
    public UnitTestExecutorState submitBatch(String batchKey, UnitTestExecutorState batchState, BatchConfig batchConfig) {
        return null;
    }

    @Override
    public UnitTestExecutorState cancelBatch(String batchKey, UnitTestExecutorState batchState) {
        return null;
    }

    @Override
    public void deleteBatch(String batchKey, UnitTestExecutorState batchState) {

    }

    @Override
    public BatchStatus getBatchStatus(String batchKey, UnitTestExecutorState batchState) {
        return null;
    }

    @Override
    public List<BatchStatus> pollBatches(List<Map.Entry<String, UnitTestExecutorState>> batches) {
        return List.of();
    }

    @Override
    public boolean hasOutputFile(String batchKey, UnitTestExecutorState batchState, String volumeName, String fileName) {
        return false;
    }

    @Override
    public byte[] getOutputFile(String batchKey, UnitTestExecutorState batchState, String volumeName, String fileName) {
        return new byte[0];
    }
}
