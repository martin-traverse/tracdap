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

package org.finos.tracdap.test.data;

import org.finos.tracdap.common.data.ArrowVsrContext;
import org.finos.tracdap.common.data.ArrowVsrSchema;
import org.finos.tracdap.common.data.DataPipeline;
import org.finos.tracdap.common.data.pipeline.BaseDataSink;

import java.util.function.Consumer;


public class SingleBatchDataSink
        extends BaseDataSink <DataPipeline.ArrowApi>
        implements DataPipeline.ArrowApi {

    private final Consumer<ArrowVsrContext> callback;

    private ArrowVsrContext root;
    private ArrowVsrSchema schema;
    private long rowCount;
    private int batchCount;

    public SingleBatchDataSink(DataPipeline pipeline) {
        this(pipeline, batch -> {});
    }

    public SingleBatchDataSink(DataPipeline pipeline, Consumer<ArrowVsrContext> callback) {

        super(pipeline);

        this.callback = callback;
        this.batchCount = 0;
    }

    @Override
    public DataPipeline.ArrowApi dataInterface() {
        return this;
    }

    public ArrowVsrSchema getSchema() {
        return schema;
    }

    public long getRowCount() { return rowCount; }

    public int getBatchCount() {
        return batchCount;
    }

    @Override
    public void connect() {
        // no-op
    }

    @Override
    public void pump() { /* no-op, immediate stage */ }

    @Override
    public boolean isReady() { return true; }

    @Override
    public void terminate(Throwable error) {

    }

    @Override
    public void close() {

    }

    @Override
    public void onStart(ArrowVsrContext root) {
        this.root = root;
        this.schema = root.getSchema();
    }

    @Override
    public void onBatch() {

        batchCount += 1;
        rowCount += root.getFrontBuffer().getRowCount();

        try {
            callback.accept(root);
            root.setUnloaded();
        }
        catch (Throwable t) {
            markAsDone();
            reportRegularError(t);
        }
    }

    @Override
    public void onComplete() {

        markAsDone();
        reportComplete();
    }

    @Override
    public void onError(Throwable error) {

        markAsDone();
        reportRegularError(error);
    }
}
