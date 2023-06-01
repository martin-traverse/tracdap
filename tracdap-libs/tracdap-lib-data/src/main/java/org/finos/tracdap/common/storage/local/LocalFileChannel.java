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

package org.finos.tracdap.common.storage.local;

import org.finos.tracdap.common.storage.IFileChannel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.ReadPendingException;
import java.util.concurrent.Future;


public class LocalFileChannel implements IFileChannel {

    private final AsynchronousFileChannel fileChannel;

    private CompletionHandler<Integer, Object> handler;
    private long position;

    LocalFileChannel(AsynchronousFileChannel fileChannel) {
        this.fileChannel = fileChannel;
    }

    @Override
    public boolean isReadable() {
        return true;
    }

    @Override
    public boolean isWritable() {
        return true;
    }

    @Override
    public boolean isSeekable() {
        return true;
    }

    @Override
    public long position() throws IOException {

        if (!isOpen())
            throw new ClosedChannelException();

        return position;
    }

    @Override
    public void position(long pos) throws IOException {

        if (pos < 0)
            throw new IllegalArgumentException();

        if (!isOpen())
            throw new ClosedChannelException();

        position = pos;
    }

    @Override
    public long size() throws IOException {
        return fileChannel.size();
    }

    @Override
    public <A> void read(ByteBuffer dst, A attachment, CompletionHandler<Integer, ? super A> handler) {

        if (this.handler != null)
            throw new ReadPendingException();

        this.handler = (CompletionHandler<Integer, Object>) handler;

        fileChannel.read(dst, position, attachment, handler);
    }

    @Override
    public Future<Integer> read(ByteBuffer dst) {
        return fileChannel.read(dst, position);
    }

    @Override
    public <A> void write(ByteBuffer src, A attachment, CompletionHandler<Integer, ? super A> handler) {

        if (this.handler != null)
            throw new ReadPendingException();

        this.handler = (CompletionHandler<Integer, Object>) handler;

        fileChannel.write(src, position, attachment, handler);
    }

    @Override
    public Future<Integer> write(ByteBuffer src) {
        return fileChannel.write(src, position);
    }

    @Override
    public void close() throws IOException {
        fileChannel.close();
    }

    @Override
    public boolean isOpen() {
        return fileChannel.isOpen();
    }

    private class ProxyHandler implements CompletionHandler<Integer, Object> {

        @Override
        public void completed(Integer nBytes, Object attachment) {

            var proxy = handler;
            handler = null;
            position += nBytes;

            proxy.completed(nBytes, attachment);
        }

        @Override
        public void failed(Throwable error, Object attachment) {

            var proxy = handler;
            handler = null;

            proxy.failed(error, attachment);
        }
    }
}
