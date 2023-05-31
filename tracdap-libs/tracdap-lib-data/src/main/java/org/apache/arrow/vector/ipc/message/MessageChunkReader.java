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

package org.apache.arrow.vector.ipc.message;

import org.apache.arrow.flatbuf.Message;
import org.apache.arrow.memory.ArrowBuf;

import java.nio.ByteBuffer;


public class MessageChunkReader {

    public static MessageResult readMessage(ArrowBuf chunk, ArrowBuffer chunkInfo, ArrowBlock block) {

        long blockOffset = block.getOffset() - chunkInfo.getOffset();
        long blockSize = block.getMetadataLength() + block.getBodyLength();

        if (blockOffset < 0 || blockOffset + blockSize > chunkInfo.getSize())
            throw new IllegalStateException();  // todo

        int prefixSize = chunk.getInt(blockOffset) == MessageSerializer.IPC_CONTINUATION_TOKEN ? 8 : 4;
        int messageLength = block.getMetadataLength() - prefixSize;
        long messageOffset = blockOffset + prefixSize;

        ByteBuffer messageBuffer = ByteBuffer.allocate(messageLength);
        chunk.getBytes(messageOffset, messageBuffer);

        messageBuffer.flip();

        Message message = Message.getRootAsMessage(messageBuffer.asReadOnlyBuffer());

        if (block.getBodyLength() > 0) {
            long bodyOffset = messageOffset + messageLength;
            ArrowBuf bodyBuffer = chunk.slice(bodyOffset, block.getBodyLength());
            return new MessageResult(message, bodyBuffer);
        }
        else {
            return new MessageResult(message, null);
        }
    }

    public static ByteBuffer readBytes(ArrowBuf chunk, ArrowBuffer chunkInfo, long offset, int size) {

        long chunkOffset = offset - chunkInfo.getOffset();

        if (chunkOffset < 0 || chunkOffset + size > chunkInfo.getSize())
            throw new IllegalStateException();  // todo

        ByteBuffer buffer = ByteBuffer.allocate(size);
        chunk.getBytes(chunkOffset, buffer);

        buffer.flip();

        return buffer;
    }
}
