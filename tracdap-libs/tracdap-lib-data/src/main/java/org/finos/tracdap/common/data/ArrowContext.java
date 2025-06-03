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
import org.apache.arrow.algorithm.dictionary.HashTableBasedDictionaryBuilder;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.FixedWidthVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.dictionary.Dictionary;
import org.apache.arrow.vector.dictionary.DictionaryEncoder;
import org.apache.arrow.vector.dictionary.DictionaryProvider;
import org.apache.arrow.vector.types.pojo.*;
import org.apache.arrow.vector.util.TransferPair;
import org.finos.tracdap.metadata.SchemaDefinition;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

public class ArrowContext {

    private static final Set<Class<? extends ArrowType>> FIXED_WIDTH_TYPES = Set.of(
            ArrowType.Int.class,
            ArrowType.FloatingPoint.class,
            ArrowType.Bool.class,
            ArrowType.Decimal.class,
            ArrowType.Date.class,
            ArrowType.Time.class,
            ArrowType.Timestamp.class,
            ArrowType.Interval.class,
            ArrowType.Duration.class,
            ArrowType.FixedSizeBinary.class
    );

    private ArrowSchema arrowSchema;

    private BufferAllocator allocator;
    private VectorSchemaRoot front;
    private VectorSchemaRoot back;
    private DictionaryProvider dictionaries;
    private DictionaryBuilder<?>[] builders;

    private boolean frontReady;
    private boolean backReady;

    private TransferPair[] flipVectors;

    private ArrowContext(VectorSchemaRoot front, VectorSchemaRoot back, DictionaryProvider dictionaries, DictionaryBuilder<?>[] builders) {
        this.front = front;
        this.back = back;
        this.dictionaries = dictionaries;
        this.builders = builders;
    }

    public static ArrowContext createFromSchema(ArrowSchema schema, BufferAllocator allocator, int batchSizeHint) {

    }

    public static ArrowContext createFromVSR(VectorSchemaRoot vsr, DictionaryProvider dictionaries) {

    }

    public ArrowSchema getArrowSchema() {
        return this.arrowSchema;
    }

    public static ArrowContext prepare(SchemaDefinition tracSchema) {

        var fields = new ArrayList<>(ArrowSchema.tracToArrow(tracSchema).getFields());

        var dictionaries = new DictionaryProvider.MapDictionaryProvider();
        var dictionaryBuilders = new DictionaryBuilder<?>[fields.size()];
        long nextDictionaryId = 0;

        for (int i = 0; i < fields.size(); ++i) {
            var tracField = tracSchema.getTable().getFields(i);
            var arrowField = fields.get(i);
            if (tracField.getCategorical()) {

                if (!FIXED_WIDTH_TYPES.contains(arrowField.getType().getClass())) {
                    continue;  // TODO: Cannot dictionary encode
                }

                var indexType = new ArrowType.Int(32, true);
                var encoding = new DictionaryEncoding(nextDictionaryId++, false, indexType);

                var indexField = new Field(arrowField.getName(), new FieldType(arrowField.isNullable(),indexType, encoding), null);
                fields.set(i, indexField);

                var dictionaryVector = arrowField.createVector(allocator);
                var dictionary = new Dictionary(dictionaryVector, encoding);
                var dictionaryBuilder = new HashTableBasedDictionaryBuilder<>((FixedWidthVector) dictionaryVector);
                dictionaries.put(dictionary);
                dictionaryBuilders[i] = dictionaryBuilder;
            }
        }

        var frontVectors = fields.stream().map(f -> f.createVector(allocator)).collect(Collectors.toList());
        var backVectors = fields.stream().map(f -> f.createVector(allocator)).collect(Collectors.toList());

        var front = new VectorSchemaRoot(frontVectors);
        var back = new VectorSchemaRoot(backVectors);

        return new ArrowContext(front, back, dictionaries, dictionaryBuilders);
    }


    public static ArrowContext prepare(SchemaDefinition tracSchema, BufferAllocator allocator) {

        var fields = new ArrayList<>(ArrowSchema.tracToArrow(tracSchema).getFields());

        var dictionaries = new DictionaryProvider.MapDictionaryProvider();
        var dictionaryBuilders = new DictionaryBuilder<?>[fields.size()];
        long nextDictionaryId = 0;

        for (int i = 0; i < fields.size(); ++i) {
            var tracField = tracSchema.getTable().getFields(i);
            var arrowField = fields.get(i);
            if (tracField.getCategorical()) {

                if (!FIXED_WIDTH_TYPES.contains(arrowField.getType().getClass())) {
                    continue;  // TODO: Cannot dictionary encode
                }

                var indexType = new ArrowType.Int(32, true);
                var encoding = new DictionaryEncoding(nextDictionaryId++, false, indexType);

                var indexField = new Field(arrowField.getName(), new FieldType(arrowField.isNullable(),indexType, encoding), null);
                fields.set(i, indexField);

                var dictionaryVector = arrowField.createVector(allocator);
                var dictionary = new Dictionary(dictionaryVector, encoding);
                var dictionaryBuilder = new HashTableBasedDictionaryBuilder<>((FixedWidthVector) dictionaryVector);
                dictionaries.put(dictionary);
                dictionaryBuilders[i] = dictionaryBuilder;
            }
        }

        var frontVectors = fields.stream().map(f -> f.createVector(allocator)).collect(Collectors.toList());
        var backVectors = fields.stream().map(f -> f.createVector(allocator)).collect(Collectors.toList());

        var front = new VectorSchemaRoot(frontVectors);
        var back = new VectorSchemaRoot(backVectors);

        return new ArrowContext(front, back, dictionaries, dictionaryBuilders);
    }

    public static  void prepare(VectorSchemaRoot back, DictionaryProvider dictionaries) {
        this.back = back;
        this.dictionaries = dictionaries;
    }

    void blah() {

        builders = new DictionaryBuilder[back.getSchema().getFields().size()];
        for (int i = 0; i < back.getSchema().getFields().size(); i++) {
            var field = back.getVector(i).getField();
            var dictionaryEncoding = field.getDictionary();
            if (dictionaryEncoding != null) {
                var dictionary = field.createVector(allocator);
                if (dictionary instanceof FixedWidthVector) {
                    builders[i] = new HashTableBasedDictionaryBuilder<>((FixedWidthVector) dictionary);
                }
                else {
                    // TODO
                    throw new RuntimeException();
                }
            }
        }

        var v = DictionaryEncoder.encode(null, null);
        back.getVector(0).tra
    }

    public ArrowContext(BufferAllocator allocator) {
        this.allocator = allocator;
    }

    public ArrowContext(BufferAllocator allocator, Schema arrowSchema) {
        this.arrowSchema = arrowSchema;
        this.allocator = allocator;
        this.dictionaries = new DictionaryProvider.MapDictionaryProvider();
    }



    public void flip() {
        if (front == null) {
            prepareFlip();
        }
        for (int i = 0; i < flipVectors.length; i++) {
            flipVectors[i].transfer();
        }
        frontReady = false;
        backReady = false;
    }

    public VectorSchemaRoot getFront() {
        return front;
    }

    public VectorSchemaRoot getBack() {
        return back;
    }

    public Schema arrowSchema() {
        return arrowSchema;
    }

    public DictionaryProvider dictionaries() {
        return dictionaries;
    }

    public BufferAllocator allocator() {
        return allocator;
    }

    private void prepareFlip() {
        arrowSchema = back.getSchema();
        front = VectorSchemaRoot.create(arrowSchema, allocator);
        frontReady = true;
        flipVectors = new TransferPair[arrowSchema.getFields().size()];
        for (int i = 0; i < flipVectors.length; i++) {
            var frontVector = front.getVector(i);
            var backVector = back.getVector(i);
            flipVectors[i] = frontVector.makeTransferPair(backVector);
        }
    }

    public boolean ready() {
        return frontReady && backReady;
    }

    public boolean frontReady() {
        return frontReady;
    }

    public boolean backReady() {
        return backReady;
    }

    public void setFrontReady() {
        this.frontReady = true;
    }

    public void setBackReady() {
        this.backReady = true;
    }

    public void close() {

        if (front != null) {
            front.close();
            front = null;
        }
        if (back != null) {
            back.close();
            back = null;
        }

        if (dictionaries != null) {
            for (var id : dictionaries.getDictionaryIds()) {
                var dictionary = dictionaries.lookup(id);
                dictionary.getVector().close();
            }
            dictionaries = null;
        }
    }



    public static VectorSchemaRoot createRoot(Schema arrowSchema, BufferAllocator arrowAllocator) {

        return createRoot(arrowSchema, arrowAllocator, 0);
    }

    public static VectorSchemaRoot createRoot(Schema arrowSchema, BufferAllocator arrowAllocator, int initialCapacity) {

        var fields = arrowSchema.getFields();
        var vectors = new ArrayList<FieldVector>(fields.size());

        for (var field : fields) {

            var vector = field.createVector(arrowAllocator);

            if (initialCapacity > 0)
                vector.setInitialCapacity(initialCapacity);

            vectors.add(vector);
        }

        return new VectorSchemaRoot(fields, vectors);
    }
}
