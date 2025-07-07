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

import org.apache.arrow.algorithm.dictionary.DictionaryBuilder;
import org.apache.arrow.algorithm.dictionary.DictionaryEncoder;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.*;
import org.apache.arrow.vector.dictionary.Dictionary;
import org.apache.arrow.vector.dictionary.DictionaryProvider;
import org.apache.arrow.vector.types.pojo.DictionaryEncoding;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.Schema;
import org.finos.tracdap.metadata.SchemaDefinition;

import java.nio.charset.StandardCharsets;
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

        var concreteFields = new ArrayList<Field>(primarySchema.getFields().size());

        for (var field : primarySchema.getFields()) {
            if (field.getDictionary() != null) {

                var dictionaryId = field.getDictionary().getId();
                var dictionary = dictionaries.lookup(dictionaryId);
                var dictionaryField = dictionary.getVector().getField();

                // Concrete field has the name of the primary field and type of the dictionary field
                // Arrow gives dictionary fields internal names, e.g. DICT0
                var concreteField = new Field(
                        field.getName(),
                        dictionaryField.getFieldType(),
                        dictionaryField.getChildren());

                concreteFields.add(concreteField);
            }
            else {
                concreteFields.add(field);
            }
        }

        return new ArrowVsrSchema(primarySchema, new Schema(concreteFields));
    }

    public static ArrowVsrContext forSchema(SchemaDefinition tracSchema, BufferAllocator allocator) {

        var schema = SchemaMapping.tracToArrow(tracSchema);

        return new ArrowVsrContext(schema, allocator, tracSchema);
    }

    public static ArrowVsrContext forSchema(ArrowVsrSchema schema, BufferAllocator allocator) {

        return new ArrowVsrContext(schema, allocator, null);
    }

    @SuppressWarnings("unchecked")
    private ArrowVsrContext(ArrowVsrSchema schema, BufferAllocator allocator, SchemaDefinition tracSchema) {

        this.schema = schema;
        this.allocator = allocator;

        var dictionaries = new DictionaryProvider.MapDictionaryProvider();

        if (tracSchema != null)
            this.namedSnums = addNamedEnumDictionaries(dictionaries, tracSchema);
        else
            this.namedSnums = null;

        var fields = schema.physical().getFields();
        var vectors = fields.stream().map(f -> f.createVector(allocator)).collect(Collectors.toList());

        this.back = new VectorSchemaRoot(vectors);
        this.back.allocateNew();
        this.front = back;  // No double buffering yet

        // Set up dictionary encoding (no a-priori knowledge)
        this.staging = new FieldVector[vectors.size()];
        this.builders = new DictionaryBuilder[vectors.size()];
        this.encoders = new DictionaryEncoder[vectors.size()];
        this.dictionaries = dictionaries;

        // Always take ownership if the VSR has been constructed internally
        this.ownership = true;
    }

    private Map<String, Long> addNamedEnumDictionaries(DictionaryProvider.MapDictionaryProvider dictionaries, SchemaDefinition tracSchema) {

        var namedEnumMap = new HashMap<String, Long>();

        long nextId = dictionaries.getDictionaryIds().size();

        for (var entry : tracSchema.getNamedEnumsMap().entrySet()) {

            var name = entry.getKey();
            var enum_ = entry.getValue();

            var dictionaryVector = new VarCharVector(name, allocator);
            dictionaryVector.allocateNew(enum_.getValuesCount());

            for (int i = 0; i < enum_.getValuesCount(); i++) {
                dictionaryVector.setSafe(i, enum_.getValues(i).getStringValue().getBytes(StandardCharsets.UTF_8));
            }

            dictionaryVector.setValueCount(enum_.getValuesCount());

            var encoding = new DictionaryEncoding(nextId++, false, null);
            var dictionary = new Dictionary(dictionaryVector, encoding);

            namedEnumMap.put(name, encoding.getId());
            dictionaries.put(dictionary);
        }

        return namedEnumMap;
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
