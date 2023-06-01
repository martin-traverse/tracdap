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

package org.finos.tracdap.common.storage;

import org.apache.arrow.memory.ArrowBuf;

import java.util.concurrent.CompletionStage;


public interface FileIO {

    long position();
    void position(long pos);
    long size();

    CompletionStage<ArrowBuf> read(long n);
    CompletionStage<Long> write(ArrowBuf buf);

    CompletionStage<Void> close();
}
