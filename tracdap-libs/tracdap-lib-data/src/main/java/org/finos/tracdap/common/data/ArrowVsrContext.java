/*
 * Licensed to the Fintech Open Source Foundation (FINOS) under one or
 * more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * FINOS licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.finos.tracdap.common.data;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.*;
import org.apache.arrow.vector.dictionary.DictionaryProvider;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;

import java.util.*;
import java.util.stream.Collectors;


/// A working context for Arrow data being processed through the VSR framework in a data pipeline
public class ArrowVsrContext {

    private final ArrowVsrSchema schema;

    private final BufferAllocator allocator;
    private final VectorSchemaRoot front;
    private final VectorSchemaRoot back;
    private final DictionaryProvider dictionaries;

    private final boolean ownership;

    private boolean backBufferLoaded;
    private boolean frontBufferAvailable;
    private boolean frontBufferUnloaded;

    public static ArrowVsrContext forSource(VectorSchemaRoot source, DictionaryProvider dictionaries, BufferAllocator allocator) {

        // Do not take ownership of external sources by default
        return new ArrowVsrContext(source, dictionaries, allocator, false);
    }

    public static ArrowVsrContext forSource(VectorSchemaRoot source, DictionaryProvider dictionaries, BufferAllocator allocator, boolean takeOwnership) {

        return new ArrowVsrContext(source, dictionaries, allocator, takeOwnership);
    }

    private ArrowVsrContext(VectorSchemaRoot source, DictionaryProvider dictionaries, BufferAllocator allocator, boolean takeOwnership) {

        this.schema = inferFullSchema(source.getSchema(), dictionaries);

        this.allocator = allocator;
        this.back = source;
        this.front = back;  // No double buffering yet
        this.dictionaries = dictionaries;

        this.ownership = takeOwnership;
    }

    private ArrowVsrSchema inferFullSchema(Schema primarySchema, DictionaryProvider dictionaries) {

        var dictionaryFields = new HashMap<Long, Field>();

        for (var field : primarySchema.getFields()) {
            buildDictionaryFields(field ,dictionaries, dictionaryFields);
        }

        return new ArrowVsrSchema(primarySchema, dictionaryFields, dictionaries);
    }

    private void buildDictionaryFields(Field field, DictionaryProvider dictionaries, Map<Long, Field> dictionaryFields) {

        if (field.getDictionary() != null) {

            var dictionaryId = field.getDictionary().getId();
            var dictionary = dictionaries.lookup(dictionaryId);
            var dictionaryField = dictionary.getVector().getField();

            dictionaryFields.put(dictionaryId, dictionaryField);
        }

        if (field.getChildren() != null) {
            for (var child : field.getChildren()) {
                buildDictionaryFields(child, dictionaries, dictionaryFields);
            }
        }
    }

    public static ArrowVsrContext forSchema(ArrowVsrSchema schema, BufferAllocator allocator) {

        return new ArrowVsrContext(schema, allocator);
    }

    private ArrowVsrContext(ArrowVsrSchema schema, BufferAllocator allocator) {

        this.schema = schema;
        this.allocator = allocator;

        var fields = schema.physical().getFields();
        var vectors = fields.stream().map(f -> f.createVector(allocator)).collect(Collectors.toList());

        this.back = new VectorSchemaRoot(vectors);
        this.back.allocateNew();
        this.front = back;  // No double buffering yet

        // TODO: Only pre-defined dictionaries are added
        var dictionaries = new DictionaryProvider.MapDictionaryProvider();

        for (var dictId : schema.dictionaries().getDictionaryIds())
            dictionaries.put(schema.dictionaries().lookup(dictId));

        this.dictionaries = dictionaries;

        // Always take ownership if the VSR has been constructed internally
        this.ownership = true;
    }

    public ArrowVsrSchema getSchema() {
        return schema;
    }

    public BufferAllocator getAllocator() {
        return allocator;
    }

    public VectorSchemaRoot getBackBuffer() {
        return back;
    }

    public VectorSchemaRoot getFrontBuffer() {
        return front;
    }

    public DictionaryProvider getDictionaries() {
        return dictionaries;
    }

    public boolean readyToLoad() {
        return ! backBufferLoaded;
    }

    public void setRowCount(int nRows) {

        back.setRowCount(nRows);
    }

    public void setLoaded() {
        backBufferLoaded = true;
    }

    public boolean readyToFlip() {
        return backBufferLoaded && (frontBufferUnloaded || !frontBufferAvailable);
    }

    public void flip() {
        frontBufferAvailable = true;
        frontBufferUnloaded = false;
    }

    public boolean readyToUnload() {
        return frontBufferAvailable && !frontBufferUnloaded;
    }

    public void setUnloaded() {
        frontBufferUnloaded = true;
        frontBufferAvailable = false;
        backBufferLoaded = false;
    }

    public void close() {

        if (ownership) {

            back.close();

            if (dictionaries != null) {
                for (var dictionaryId : dictionaries.getDictionaryIds()) {
                    var dictionary = dictionaries.lookup(dictionaryId);
                    dictionary.getVector().close();
                }
            }
        }
    }
}
