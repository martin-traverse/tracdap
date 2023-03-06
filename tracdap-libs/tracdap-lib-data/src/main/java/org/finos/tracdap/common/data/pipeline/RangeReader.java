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

package org.finos.tracdap.common.data.pipeline;

import org.finos.tracdap.common.data.DataPipeline;
import org.finos.tracdap.common.storage.IFileReader;


public class RangeReader extends BaseDataProducer<DataPipeline.RangeApi> {

    private IFileReader reader;

    public RangeReader(IFileReader reader) {

        super(DataPipeline.RangeApi.class);

        this.reader = reader;
    }

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public void pump() {

    }

    @Override
    public void close() throws Exception {

    }

    private void onRequest(long offset, long size) {

        reader.readRange(offset, size);

        this.consumer().
    }

    private void onCancel() {

    }

    private class Subscription implements DataPipeline.RangeSubscription {
        @Override
        public void request(long offset, long size) {
            onRequest(offset, size);
        }

        @Override
        public void cancel() {
            onCancel();
        }
    }
}
