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
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.compression.CompressionCodec;
import org.apache.arrow.vector.compression.NoCompressionCodec;
import org.apache.arrow.vector.ipc.message.*;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.validate.MetadataV4UnionChecker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;


public class ArrowFileAsyncReader extends ArrowReader {

    public static final byte[] MAGIC = "ARROW1".getBytes(StandardCharsets.UTF_8);
    public static final int MAGIC_LENGTH = MAGIC.length;

    private static final Logger LOGGER = LoggerFactory.getLogger(org.apache.arrow.vector.ipc.ArrowFileReader.class);

    private long fileSze;
    private long bytesRead;

    private int footerBytesRequired;
    private ArrowFooter footer;
    private boolean[] dictionariesLoaded;

    public ArrowFileAsyncReader(BufferAllocator allocator, CompressionCodec.Factory compressionFactory) {
        super(allocator, compressionFactory);
    }

    public ArrowFileAsyncReader(BufferAllocator allocator) {
        this(allocator, NoCompressionCodec.Factory.INSTANCE);
    }

    @Override
    public void initialize() throws IOException {

        if (footer == null) {
            throw new RuntimeException("");  // todo you must call feedFooter
        }

        super.initialize();
    }

    @Override
    protected Schema readSchema() throws IOException {

        if (footer == null) {
            throw new IOException("");  // todo you must call feedFooter
        }

        return footer.getSchema();
    }

    /** Returns true if a batch was read, false if no more batches. */
    @Override
    public boolean loadNextBatch() throws IOException {

        throw new RuntimeException("");  // todo you must call feedRecordBatch
    }

    @Override
    public long bytesRead() {
        return bytesRead;  // not really relevant
    }

    @Override
    protected void closeReadSource() {

        // no-op
    }

    public boolean feedFooter(ByteBuffer buffer) throws IOException {

        if (footer != null) {
            throw new RuntimeException();  // todo
        }

        if (footerBytesRequired <= 0) {
            feedFooterLength(buffer);
        }

        if (buffer.limit() < footerBytesRequired) {
            return false;
        }

        var footerOffset = Integer.BYTES + MAGIC_LENGTH;
        var footerLength = footerBytesRequired - footerOffset;
        var footerSlice = buffer.slice(buffer.limit() - footerBytesRequired, footerLength);

        var footerFB = Footer.getRootAsFooter(footerSlice);
        var footer = new ArrowFooter(footerFB);

        MetadataV4UnionChecker.checkRead(footer.getSchema(), footer.getMetadataVersion());

        this.footer = footer;
        this.dictionariesLoaded = new boolean[footer.getDictionaries().size()];

        // Set up the VSR, loader etc. now the schema is available
        super.ensureInitialized();

        bytesRead += footerLength + MAGIC_LENGTH * 2L + Integer.BYTES;

        return true;
    }

    private void feedFooterLength(ByteBuffer buffer) throws IOException {

        var footerOffset = Integer.BYTES + MAGIC_LENGTH;

        if (buffer.limit() < footerOffset) {
            throw new IOException("file too small: " + buffer.limit());  // todo
        }

        var array = new byte[footerOffset];
        buffer.get(buffer.limit() - footerOffset, array);

        if (!ArrowMagic.validateMagic(Arrays.copyOfRange(array, 4, array.length))) {
            throw new InvalidArrowFileException("missing Magic number " + Arrays.toString(buffer.array()));
        }

        var footerLength = MessageSerializer.bytesToInt(array);

        if (footerLength <= 0 || footerLength > fileSze - ((long) MAGIC_LENGTH * 2 + Integer.BYTES)) {
            throw new InvalidArrowFileException("invalid footer length: " + footerLength);
        }

        this.footerBytesRequired = footerLength + footerOffset;
    }

    public void feedDictionaryBatch(int dictionaryIndex, ByteBuffer rawBytes) throws IOException {

        if (footer == null || footer.getDictionaries() == null) {
            throw new RuntimeException("");  // todo
        }

        if (dictionaryIndex < 0 || dictionaryIndex >= footer.getDictionaries().size()) {
            throw new RuntimeException("");  // todo
        }

        if (dictionariesLoaded[dictionaryIndex]) {
            throw new RuntimeException("");  // todo
        }

        var block = footer.getDictionaries().get(dictionaryIndex);

        LOGGER.debug("DictionaryRecordBatch at {}, metadata: {}, body: {}",
                block.getOffset(),
                block.getMetadataLength(),
                block.getBodyLength());

        try (var stream = new ByteBufferChannel(rawBytes); var reader = new ReadChannel(stream)) {

            var batch = MessageSerializer.deserializeDictionaryBatch(reader, block, allocator);
            loadDictionary(batch);

            dictionariesLoaded[dictionaryIndex] = true;
        }

        bytesRead += block.getMetadataLength() + block.getBodyLength();
    }

    public void feedRecordBatch(int batchIndex, ByteBuffer rawBytes) throws IOException {

        if (footer == null || footer.getRecordBatches() == null) {
            throw new RuntimeException("");  // todo
        }

        if (batchIndex < 0 || batchIndex >= footer.getRecordBatches().size()) {
            throw new RuntimeException("");  // todo
        }

        if (!allDictionariesLoaded()) {
            throw new RuntimeException("");  // todo
        }

        var block = footer.getRecordBatches().get(batchIndex);

        LOGGER.debug("RecordBatch at {}, metadata: {}, body: {}",
                block.getOffset(),
                block.getMetadataLength(),
                block.getBodyLength());

        try (var stream = new ByteBufferChannel(rawBytes); var reader = new ReadChannel(stream)) {

            prepareLoadNextBatch();

            var batch = MessageSerializer.deserializeRecordBatch(reader, block, allocator);
            loadRecordBatch(batch);
        }

        bytesRead += block.getMetadataLength() + block.getBodyLength();
    }

    public ArrowBlock getFooterBlock() {

        if (footerBytesRequired <= 0) {
            throw new RuntimeException("");  // todo
        }

        return new ArrowBlock(fileSze - footerBytesRequired, footerBytesRequired, 0);
    }

    /**
     * Get custom metadata.
     */
    public Map<String, String> getMetaData() {

        if (footer == null) {
            throw new RuntimeException("");  // todo
        }

        return footer.getMetaData();
    }

    public List<ArrowBlock> getDictionaryBlocks() {

        if (footer == null) {
            throw new RuntimeException("");  // todo
        }

        return footer.getDictionaries();
    }

    /**
     * Returns the {@link ArrowBlock} metadata from the file.
     */
    public List<ArrowBlock> getRecordBlocks() {

        if (footer == null) {
            throw new RuntimeException("");  // todo
        }

        return footer.getRecordBatches();
    }

    private boolean allDictionariesLoaded() {

        if (dictionariesLoaded == null)
            return false;

        for (var dictionaryLoaded : dictionariesLoaded)
            if (!dictionaryLoaded)
                return false;

        return true;
    }

}
