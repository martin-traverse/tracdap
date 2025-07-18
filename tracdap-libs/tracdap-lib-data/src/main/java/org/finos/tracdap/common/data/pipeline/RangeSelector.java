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

package org.finos.tracdap.common.data.pipeline;

import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.util.TransferPair;
import org.finos.tracdap.common.data.ArrowVsrContext;
import org.finos.tracdap.common.data.DataPipeline;
import org.finos.tracdap.common.exception.EUnexpected;

import java.util.ArrayList;


public class RangeSelector
        extends
        BaseDataProducer<DataPipeline.ArrowApi>
        implements
        DataPipeline.ArrowApi,
        DataPipeline.DataConsumer<DataPipeline.ArrowApi>,
        DataPipeline.DataProducer<DataPipeline.ArrowApi> {

    private final long offset;
    private final long limit;
    private long currentRow;

    private ArrowVsrContext incomingRoot;
    private ArrowVsrContext sliceRoot;
    private ArrayList<TransferPair> sliceTransfers;

    public RangeSelector(long offset, long limit) {

        super(DataPipeline.ArrowApi.class);

        this.offset = offset;
        this.limit = limit;
        this.currentRow = 0;
    }

    @Override
    public boolean isReady() {

        if (sliceRoot != null)
            return sliceRoot.readyToFlip() || sliceRoot.readyToLoad();
        else
            return consumerReady();
    }

    @Override
    public void pump() {

        if (incomingRoot == null)
            return;

        if (incomingRoot.readyToUnload())
            flipBatch();

        if (consumerReady() && sliceRoot.readyToUnload())
            consumer().onBatch();
    }

    @Override
    public DataPipeline.ArrowApi dataInterface() {
        return this;
    }

    @Override
    public void close() {

        releaseResources();
    }

    @Override
    public void onStart(ArrowVsrContext context) {

        if (incomingRoot != null)
            throw new EUnexpected();

        var sliceTransfers = new ArrayList<TransferPair>();
        var sliceVectors = new ArrayList<FieldVector>();

        for (var vector : context.getFrontBuffer().getFieldVectors()) {

            var transfer = vector.getTransferPair(vector.getAllocator());
            var sliceVector = (FieldVector) transfer.getTo();

            sliceTransfers.add(transfer);
            sliceVectors.add(sliceVector);
        }

        this.incomingRoot = context;

        this.sliceTransfers = sliceTransfers;
        this.sliceRoot = ArrowVsrContext.forSource(
                new VectorSchemaRoot(sliceVectors),
                context.getDictionaries(),
                context.getAllocator(),
                /* takeOwnership = */ true);

        consumer().onStart(sliceRoot);
    }

    @Override
    public void onBatch() {

        if (incomingRoot == null)
            throw new EUnexpected();

        flipBatch();

        if (consumerReady() && sliceRoot.readyToUnload())
            consumer().onBatch();
    }

    private void flipBatch() {

        if (sliceRoot.readyToFlip())
            sliceRoot.flip();

        if (sliceRoot.readyToLoad()) {

            var batchSize = incomingRoot.getFrontBuffer().getRowCount();
            var batchStartRow = currentRow;
            var batchEndRow = currentRow + batchSize;

            if (batchStartRow >= offset && (batchEndRow < offset + limit || limit == 0)) {

                sliceTransfers.forEach(TransferPair::transfer);

                sliceRoot.setRowCount(batchSize);
                sliceRoot.setLoaded();
            }
            else if (batchEndRow >= offset && (batchStartRow < offset + limit || limit == 0)) {

                var sliceStart = (int) (offset - batchStartRow);
                var sliceEnd = (int) Math.min(offset + limit - batchStartRow, batchSize);
                var sliceLength = sliceEnd - sliceStart;

                sliceTransfers.forEach(slice -> slice.splitAndTransfer(sliceStart, sliceLength));

                sliceRoot.setRowCount(sliceLength);
                sliceRoot.setLoaded();
            }

            incomingRoot.setUnloaded();
            currentRow += batchSize;
        }

        if (sliceRoot.readyToFlip())
            sliceRoot.flip();


        // TODO: if (batchStartRow >= offset + limit && limit != 0) {
        //      pipeline.cancel()
        // }
    }

    @Override
    public void onComplete() {

        if (incomingRoot == null)
            throw new EUnexpected();

        try {
            consumer().onComplete();
        }
        finally {
            releaseResources();
        }
    }

    @Override
    public void onError(Throwable error) {

        if (incomingRoot == null)
            throw new EUnexpected();

        try {
            consumer().onError(error);
        }
        finally {
            releaseResources();
        }
    }

    private void releaseResources() {

        // Incoming root is owned by the source, do not close
        incomingRoot = null;

        if (sliceRoot != null) {
            sliceRoot.close();
            sliceRoot = null;
        }

        if (sliceTransfers != null) {
            sliceTransfers.clear();
            sliceTransfers = null;
        }
    }
}
