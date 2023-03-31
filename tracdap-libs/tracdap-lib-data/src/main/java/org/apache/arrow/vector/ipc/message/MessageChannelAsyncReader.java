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

import org.finos.tracdap.common.data.util.ByteInputChannel;
import org.finos.tracdap.common.exception.EDataCorruption;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.ipc.ReadChannel;

import io.netty.buffer.ByteBuf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Deque;


public class MessageChannelAsyncReader extends MessageChannelReader {

    private static final int HEADER_SIZE = 4;
    private static final int MAX_ARROW_MESSAGE_SIZE = 16 * 1024 * 1024;

    private static final MessageResult EOS_MARKER = new MessageResult(null, null);

    int expectHeader;
    int expectMetadata;
    long expectBody;

    private final ByteInputChannel inputChannel;

    private final Deque<MessageResult> messageQueue;
    private MessageMetadataResult currentMessage;

    public MessageChannelAsyncReader(ByteInputChannel inputChannel, BufferAllocator allocator) {

        super(new ReadChannel(inputChannel), allocator);

        this.inputChannel = inputChannel;

        messageQueue = new ArrayDeque<>();
        currentMessage = null;
    }

    public void feedBytes(ByteBuf chunk) throws IOException {

        inputChannel.feedBytes(chunk);

        while (inputChannel.readableBytes() > 0) {

            if (expectHeader == 0 && expectMetadata == 0 && expectBody == 0) {

                expectHeader = HEADER_SIZE;
            }

            if (expectHeader > 0) {

                if (inputChannel.readableBytes() < HEADER_SIZE)
                    break;

                var headerBuf = ByteBuffer.allocate(HEADER_SIZE);
                headerBuf.order(ByteOrder.LITTLE_ENDIAN);

                inputChannel.peek(headerBuf, 0, HEADER_SIZE);
                headerBuf.flip();

                var metadataSize = headerBuf.getInt();

                if (metadataSize == MessageSerializer.IPC_CONTINUATION_TOKEN) {

                    if (inputChannel.readableBytes() < HEADER_SIZE)
                        break;

                    headerBuf.rewind();
                    inputChannel.peek(headerBuf, HEADER_SIZE, HEADER_SIZE);
                    headerBuf.flip();

                    metadataSize = headerBuf.getInt();
                }

                if (metadataSize < 0 || metadataSize > MAX_ARROW_MESSAGE_SIZE)
                    throw new EDataCorruption("Data is corrupt, or not an Arrow stream");

                expectHeader = 0;
                expectMetadata = metadataSize;

                // metadata size == 0 indicates EOS
                // MessageChannelReader will return a null message for EOS, so we do the same
                if (expectMetadata == 0)
                    messageQueue.add(EOS_MARKER);
            }

            if (expectMetadata > 0) {

                if (inputChannel.readableBytes() < expectMetadata)
                    break;

                currentMessage = MessageSerializer.readMessage(in);
                expectMetadata = 0;

                if (currentMessage.messageHasBody())
                    expectBody = currentMessage.getMessageBodyLength();
                else {
                    var message = new TracMessageResult(currentMessage.getMessage(), null);
                    messageQueue.add(message);
                }
            }

            if (expectBody > 0) {

                if (inputChannel.readableBytes() < expectBody)
                    break;

                var body = MessageSerializer.readMessageBody(in, expectBody, allocator);
                expectBody = 0;

                var message = new TracMessageResult(currentMessage.getMessage(), body);
                messageQueue.add(message);
            }
        }
    }

    public boolean messageAvailable(byte messageType) {

        for (var message : messageQueue)
            if (message != EOS_MARKER && message.getMessage().headerType() == messageType)
                return true;

        return false;
    }

    @Override
    public MessageResult readNext() throws IOException {

        if (messageQueue.isEmpty())
            throw new IOException("Message not available on input channel");

        var msg = messageQueue.pop();

        if (msg == EOS_MARKER)
            return null;
        else
            return msg;
    }

    @Override
    public void close() throws IOException {

        while (!messageQueue.isEmpty()) {
            var message = messageQueue.pop();
            if (message != EOS_MARKER) {
                message.getMessage().customMetadataVector().reset();
                if (message.getBodyBuffer() != null)
                    message.getBodyBuffer().close();
            }
        }

        inputChannel.close();
    }
}