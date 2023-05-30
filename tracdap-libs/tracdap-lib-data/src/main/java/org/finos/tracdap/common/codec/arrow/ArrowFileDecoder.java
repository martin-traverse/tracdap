/*
 * Copyright 2023 Accenture Global Solutions Limited
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

package org.finos.tracdap.common.codec.arrow;

import org.finos.tracdap.common.codec.ICodec;
import org.finos.tracdap.common.data.DataPipeline;
import org.finos.tracdap.common.data.pipeline.BaseDataProducer;

import org.apache.arrow.memory.ArrowBuf;


public class ArrowFileDecoder
        extends BaseDataProducer<DataPipeline.ArrowApi>
        implements
        ICodec.Decoder<DataPipeline.FileApi>,
        DataPipeline.FileApi {

    public ArrowFileDecoder() {
        super(DataPipeline.ArrowApi.class);
    }

    @Override
    public void onStart(long fileSize, DataPipeline.FileSubscription subscription) {

    }

    @Override
    public void onChunk(long offset, ArrowBuf chunk) {

    }

    @Override
    public void onError(Throwable error) {

    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public void pump() {

    }

    @Override
    public DataPipeline.FileApi dataInterface() {
        return this;
    }

    @Override
    public void close() throws Exception {

    }
}
