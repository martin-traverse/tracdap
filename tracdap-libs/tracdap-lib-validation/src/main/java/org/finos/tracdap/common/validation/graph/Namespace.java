/*
 * Copyright 2024 Accenture Global Solutions Limited
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

package org.finos.tracdap.common.validation.graph;

import java.util.Objects;
import java.util.Optional;


public class Namespace {

    public static final Namespace ROOT = new Namespace("", /* isRoot = */ true);

    private final String name;
    private final Optional<Namespace> parent;

    private Namespace(String name, boolean isRoot) {
        this.name = name;
        this.parent = isRoot ? Optional.empty() : Optional.of(ROOT);
    }

    public Namespace(String name) {
        this(name, ROOT);
    }

    public Namespace(String name, Namespace parent) {
        this.name = name;
        this.parent = parent != null ? Optional.of(parent) : Optional.of(ROOT);
    }

    public String name() {
        return name;
    }

    public Optional<Namespace> parent() {
        return parent;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Namespace that = (Namespace) o;
        return Objects.equals(name, that.name) && Objects.equals(parent, that.parent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, parent);
    }
}
