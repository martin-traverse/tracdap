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

import org.apache.arrow.flatbuf.Footer;
import org.apache.arrow.flatbuf.Message;
import org.apache.arrow.memory.ArrowBuf;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.util.VisibleForTesting;
import org.apache.arrow.vector.compression.CompressionCodec;
import org.apache.arrow.vector.compression.NoCompressionCodec;
import org.apache.arrow.vector.ipc.message.*;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.validate.MetadataV4UnionChecker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

import static org.apache.arrow.vector.ipc.message.MessageSerializer.IPC_CONTINUATION_TOKEN;


public class ArrowFileAsyncReader extends ArrowReader {

    private static final long OPTIMISTIC_CHUNK_THRESHOLD = 16 * 1048576;

    private static final Logger LOGGER = LoggerFactory.getLogger(ArrowFileAsyncReader.class);

    private final long fileSize;
    private final Deque<DataChunk> chunks;
    private final Deque<DataRequest> requests;

    private boolean requestedFooterSize;
    private int footerSize;

    private boolean requestedFooter;
    private ArrowFooter footer;

    private int currentDictionaryBatch = 0;
    private int currentRecordBatch = 0;

    private long bytesConsumed;


    public ArrowFileAsyncReader(long fileSize, BufferAllocator allocator, CompressionCodec.Factory compressionFactory) {
        super(allocator, compressionFactory);
        this.fileSize = fileSize;
        this.chunks = new ArrayDeque<>();
        this.requests = new ArrayDeque<>();
    }

    public ArrowFileAsyncReader(long fileSize, BufferAllocator allocator) {
        this(fileSize, allocator, NoCompressionCodec.Factory.INSTANCE);
    }

    public DataRequest requestBytes() {

        if (footer != null && hasDictionaries()) {

            long nextOffset = 0;
            long nextSize = 0;
            int nBlocks = 0;

            for (int i = currentDictionaryBatch, n = footer.getRecordBatches().size(); i < n; i++) {

                ArrowBlock block = footer.getRecordBatches().get(i);
                long blockOffset = block.getOffset();
                long blockSize = block.getMetadataLength() + block.getBodyLength();

                if (bytesReady(blockOffset, blockSize) || bytesPending(blockOffset, blockSize))
                    continue;

                if (nBlocks == 0) {
                    nextOffset = blockOffset;
                    nextSize = blockSize;
                    nBlocks = 1;
                    if (blockSize >= OPTIMISTIC_CHUNK_THRESHOLD)
                        break;
                }
                else {
                    long blockEnd = blockOffset + blockSize;
                    if (blockEnd - nextOffset <= OPTIMISTIC_CHUNK_THRESHOLD) {
                        nextSize = blockEnd - nextOffset;
                        nBlocks += 1;
                    }
                    else
                        break;
                }
            }

            if (nBlocks > 0) {
                DataRequest request = new DataRequest(nextOffset, nextSize);
                requests.addLast(request);
                return request;
            }
        }

        if (!requestedFooterSize) {
            requestedFooterSize = true;
            long size = 4 + ArrowMagic.MAGIC_LENGTH;
            long offset = fileSize - size;
            return new DataRequest(offset, size);
        }

        if (footerSize == 0)
            return null;

        if (!requestedFooter) {
            requestedFooter = true;
            long offset = fileSize - footerSize -
        }
    }

    public void feedBytes(long offset, ArrowBuf buffer) throws IOException {

        if (requests.isEmpty())
            throw new IOException("Data received out of order");

        DataRequest request = requests.pop();
        long chunkEnd = offset + buffer.readableBytes();
        long requestEnd = request.offset + request.size;

        if (offset > request.offset || chunkEnd < requestEnd)
            throw new IOException("Data received out of order");

        var chunk = new DataChunk();
        chunk.offset = offset;
        chunk.buffer = buffer;
        chunks.addLast(chunk);

        bytesConsumed += buffer.readableBytes();
    }

    public int bufferedChunks() {
        return chunks.size();
    }

    public long bufferedBytes() {

        return chunks.stream()
                .map(chunk -> chunk.buffer)
                .mapToLong(ArrowBuf::readableBytes)
                .sum();
    }

    public int pendingChunks() {
        return requests.size();
    }

    public long pendingBytes() {

        return requests.stream()
                .mapToLong(DataRequest::getSize)
                .sum();
    }

    public boolean hasFooter() {

        if (footer != null)
            return true;
    }

    public boolean hasDictionaries() {

    }

    public boolean hasFooterAndDictionaries() {
        return hasFooter() && hasDictionaries();
    }

    public boolean hasNextBatch() {

        if (footer == null)
            return false;

        ArrowBlock nextBatch = footer.getRecordBatches().get(currentRecordBatch);
        long offset = nextBatch.getOffset();
        long size = offset + nextBatch.getMetadataLength() + nextBatch.getBodyLength();

        return hasBytes(offset, size);
    }

    private boolean bytesReady(long offset, long size) {

        // Look for a chunk that wholly contains the requested byte range
        // Ranges split across multiple chunks cannot be used and will not be counted

        for (DataChunk chunk : chunks) {
            long chunkEnd = chunk.offset + chunk.buffer.readableBytes();
            if (offset >= chunk.offset && offset + size <= chunkEnd)
                return true;
        }

        return false;
    }

    private boolean bytesPending(long offset, long size) {

        // Like bytesReady(), but for requests that have not been fulfilled yet
        // Look for a request that wholly contains the requested byte range

        for (DataRequest request : requests) {
            long requestEnd = request.offset + request.size;
            if (offset >= request.offset && offset + size <= requestEnd)
                return true;
        }

        return false;
    }



    @Override
    protected void closeReadSource() throws IOException {

        while (!chunks.isEmpty()) {
            DataChunk chunk = chunks.pop();
            chunk.buffer.close();
        }

        requests.clear();
    }




    public static class DataRequest {

        private final long offset;
        private final long size;

        public DataRequest(long offset, long size) {
            this.offset = offset;
            this.size = size;
        }

        public long getOffset() {
            return offset;
        }

        public long getSize() {
            return size;
        }
    }

    private static class DataChunk {
        long offset;
        ArrowBuf buffer;
    }





    @Override
    public long bytesRead() {
        return bytesConsumed;
    }

    @Override
    public void initialize() throws IOException {

        super.initialize();

        // empty stream, has no dictionaries in IPC.
        if (footer.getRecordBatches().size() == 0) {
            return;
        }

        // Read and load all dictionaries from schema
        for (int i = 0; i < dictionaries.size(); i++) {
            ArrowDictionaryBatch dictionaryBatch = readDictionary();
            loadDictionary(dictionaryBatch);
        }
    }

    @Override
    protected Schema readSchema() throws IOException {

        if (footer != null)
            return footer.getSchema();

        if (!hasFooter())
            throw new IllegalStateException("")

        if (footer == null) {
            if (in.size() <= (ArrowMagic.MAGIC_LENGTH * 2 + 4)) {
                throw new InvalidArrowFileException("file too small: " + in.size());
            }
            ByteBuffer buffer = ByteBuffer.allocate(4 + ArrowMagic.MAGIC_LENGTH);
            long footerLengthOffset = in.size() - buffer.remaining();
            in.setPosition(footerLengthOffset);
            in.readFully(buffer);
            buffer.flip();
            byte[] array = buffer.array();
            if (!ArrowMagic.validateMagic(Arrays.copyOfRange(array, 4, array.length))) {
                throw new InvalidArrowFileException("missing Magic number " + Arrays.toString(buffer.array()));
            }
            int footerLength = MessageSerializer.bytesToInt(array);
            if (footerLength <= 0 || footerLength + ArrowMagic.MAGIC_LENGTH * 2 + 4 > in.size() ||
                    footerLength > footerLengthOffset) {
                throw new InvalidArrowFileException("invalid footer length: " + footerLength);
            }
            long footerOffset = footerLengthOffset - footerLength;
            LOGGER.debug("Footer starts at {}, length: {}", footerOffset, footerLength);
            ByteBuffer footerBuffer = ByteBuffer.allocate(footerLength);
            in.setPosition(footerOffset);
            in.readFully(footerBuffer);
            footerBuffer.flip();
            Footer footerFB = Footer.getRootAsFooter(footerBuffer);
            this.footer = new ArrowFooter(footerFB);
        }
        MetadataV4UnionChecker.checkRead(footer.getSchema(), footer.getMetadataVersion());
        return footer.getSchema();
    }

    /** Returns true if a batch was read, false if no more batches. */
    @Override
    public boolean loadNextBatch() throws IOException {

        prepareLoadNextBatch();

        if (currentRecordBatch < footer.getRecordBatches().size()) {
            ArrowBlock block = footer.getRecordBatches().get(currentRecordBatch++);
            ArrowRecordBatch batch = readRecordBatch(block);
            loadRecordBatch(batch);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Loads record batch for the given block.
     */
    public boolean loadRecordBatch(ArrowBlock block) throws IOException {
        ensureInitialized();
        int blockIndex = footer.getRecordBatches().indexOf(block);
        if (blockIndex == -1) {
            throw new IllegalArgumentException("Arrow block does not exist in record batches: " + block);
        }
        currentRecordBatch = blockIndex;
        return loadNextBatch();
    }

    @VisibleForTesting
    ArrowFooter getFooter() {
        return footer;
    }

    public List<ArrowBlock> getDictionaryBlocks() throws IOException {
        ensureInitialized();
        return footer.getDictionaries();
    }

    /**
     * Returns the {@link ArrowBlock} metadata from the file.
     */
    public List<ArrowBlock> getRecordBlocks() throws IOException {
        ensureInitialized();
        return footer.getRecordBatches();
    }

    /**
     * Get custom metadata.
     */
    public Map<String, String> getMetaData() {
        if (footer != null) {
            return footer.getMetaData();
        }
        return new HashMap<>();
    }

    /**
     * Read a dictionary batch from the source, will be invoked after the schema has been read and
     * called N times, where N is the number of dictionaries indicated by the schema Fields.
     *
     * @return the read ArrowDictionaryBatch
     * @throws IOException on error
     */
    public ArrowDictionaryBatch readDictionary() throws IOException {
        if (currentDictionaryBatch >= footer.getDictionaries().size()) {
            throw new IOException("Requested more dictionaries than defined in footer: " + currentDictionaryBatch);
        }
        ArrowBlock block = footer.getDictionaries().get(currentDictionaryBatch++);
        return readDictionaryBatch(in, block, allocator);
    }




    private void readFooterLength() {

    }

    private void readFooter() {

    }



    private ArrowDictionaryBatch readDictionaryBatch(ArrowBlock block) throws IOException {

        LOGGER.debug("DictionaryRecordBatch at {}, metadata: {}, body: {}",
                block.getOffset(), block.getMetadataLength(), block.getBodyLength());

        DataChunk chunk = chunks.peek();
        ArrowBuf buffer = chunk.buffer;
        ArrowBuffer bufferInfo = new ArrowBuffer(chunk.offset, buffer.readableBytes());
        MessageResult result = MessageChunkReader.readMessage(buffer, bufferInfo, block);

        ArrowDictionaryBatch batch = MessageSerializer.deserializeDictionaryBatch(result.getMessage(), result.getBodyBuffer());

        if (batch == null) {
            throw new IOException("Invalid file. No batch at offset: " + block.getOffset());
        }

        return batch;
    }

    private ArrowRecordBatch readRecordBatch(ArrowBlock block) throws IOException {

        LOGGER.debug("RecordBatch at {}, metadata: {}, body: {}",
                block.getOffset(), block.getMetadataLength(), block.getBodyLength());

        DataChunk chunk = chunks.peek();
        ArrowBuf buffer = chunk.buffer;
        ArrowBuffer bufferInfo = new ArrowBuffer(chunk.offset, buffer.readableBytes());
        MessageResult result = MessageChunkReader.readMessage(buffer, bufferInfo, block);

        ArrowRecordBatch batch = MessageSerializer.deserializeRecordBatch(result.getMessage(), result.getBodyBuffer());

        if (batch == null) {
            throw new IOException("Invalid file. No batch at offset: " + block.getOffset());
        }

        return batch;
    }

    private boolean chunkContainsBlock(long chunkOffset, long chunkSize, long blockOffset, long blockSize) {

        if (blockOffset < chunkOffset)
            return false;

        long chunkEnd = chunkOffset + chunkSize;
        long blockEnd = blockOffset + blockSize;

        return blockEnd <= chunkEnd;
    }

}
