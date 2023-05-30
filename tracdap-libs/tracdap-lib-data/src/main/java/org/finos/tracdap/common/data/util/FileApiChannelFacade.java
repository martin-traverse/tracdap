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

package org.finos.tracdap.common.data.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;


public class FileApiChannelFacade implements SeekableByteChannel {

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return 0;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public long position() throws IOException {
        return 0;
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        return null;
    }

    @Override
    public long size() throws IOException {
        return 0;
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        return null;
    }

    @Override
    public boolean isOpen() {
        return false;
    }

    @Override
    public void close() throws IOException {

    }
}
