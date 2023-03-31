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

package org.apache.arrow.vector.ipc;

import org.apache.arrow.flatbuf.Message;
import org.apache.arrow.flatbuf.MessageHeader;
import org.apache.arrow.memory.ArrowBuf;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.FieldVector;
import org.apache.arrow.vector.compression.CompressionCodec;
import org.apache.arrow.vector.ipc.message.*;
import org.apache.arrow.vector.types.MetadataVersion;
import org.apache.arrow.vector.types.pojo.DictionaryEncoding;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.validate.MetadataV4UnionChecker;



import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;


public class ArrowStreamAsyncReader extends ArrowReader {

    private MessageChannelReader messageReader;

    private int loadedDictionaryCount;

    private final Deque<MessageResult> messages;

    public ArrowStreamAsyncReader(BufferAllocator allocator, CompressionCodec.Factory compressionFactory) {

        super(allocator, compressionFactory);

        this.messages = new ArrayDeque<>();
    }

    public void feedBytes(ByteBuffer buffer) {

    }

    /**
     * Get the number of bytes read from the stream since constructing the reader.
     *
     * @return number of bytes
     */
    @Override
    public long bytesRead() {
        return messageReader.bytesRead();
    }

    /**
     * Closes the underlying read source.
     *
     * @throws IOException on error
     */
    @Override
    protected void closeReadSource() throws IOException {
        messageReader.close();
    }

    /**
     * Load the next ArrowRecordBatch to the vector schema root if available.
     *
     * @return true if a batch was read, false on EOS
     * @throws IOException on error
     */
    public boolean loadNextBatch() throws IOException {

        prepareLoadNextBatch();

        if (messages.isEmpty()) {
            throw new IOException("");   // todo
        }

        var result = messages.pop();

        // Reached EOS
        if (result.isEos) {
            return false;
        }

        if (result.message.headerType() == MessageHeader.RecordBatch) {

            // For zero-length batches, need an empty buffer to deserialize the batch
            if (result.bodyBuffer == null) {
                result.bodyBuffer = allocator.getEmpty();
            }

            var batch = MessageSerializer.deserializeRecordBatch(result.message, result.bodyBuffer);

            loadRecordBatch(batch);
            checkDictionaries();

            return true;

        }
        else if (result.message.headerType() == MessageHeader.DictionaryBatch) {

            // if it's dictionary message, read dictionary message out and continue to read unless get a batch or eos.
            var dictionaryBatch = readDictionary(result);

            loadDictionary(dictionaryBatch);
            loadedDictionaryCount++;

            return loadNextBatch();
        }
        else {

            throw new IOException("Expected RecordBatch or DictionaryBatch but header was " +
                    result.message.headerType());
        }
    }

    /**
     * When read a record batch, check whether its dictionaries are available.
     */
    private void checkDictionaries() throws IOException {
        // if all dictionaries are loaded, return.
        if (loadedDictionaryCount == dictionaries.size()) {
            return;
        }
        for (FieldVector vector : getVectorSchemaRoot().getFieldVectors()) {
            DictionaryEncoding encoding = vector.getField().getDictionary();
            if (encoding != null) {
                // if the dictionaries it needs is not available and the vector is not all null, something was wrong.
                if (!dictionaries.containsKey(encoding.getId()) && vector.getNullCount() < vector.getValueCount()) {
                    throw new IOException("The dictionary was not available, id was:" + encoding.getId());
                }
            }
        }
    }

    /**
     * Reads the schema message from the beginning of the stream.
     *
     * @return the deserialized arrow schema
     */
    @Override
    protected Schema readSchema() throws IOException {

        if (messages.isEmpty()) {
            throw new IOException("");  // todo
        }

        var result = messages.pop();

        if (result.isEos) {
            throw new IOException("Unexpected end of input. Missing schema.");
        }

        if (result.message.headerType() != MessageHeader.Schema) {
            throw new IOException("Expected schema but header was " + result.message.headerType());
        }

        final Schema schema = MessageSerializer.deserializeSchema(result.getMessage());
        MetadataV4UnionChecker.checkRead(schema, MetadataVersion.fromFlatbufID(result.getMessage().version()));
        return schema;
    }


    private ArrowDictionaryBatch readDictionary(MessageResult result) throws IOException {

        ArrowBuf bodyBuffer = result.getBodyBuffer();

        // For zero-length batches, need an empty buffer to deserialize the batch
        if (bodyBuffer == null) {
            bodyBuffer = allocator.getEmpty();
        }

        return MessageSerializer.deserializeDictionaryBatch(result.getMessage(), bodyBuffer);
    }
}
