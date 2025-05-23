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

package org.finos.tracdap.common.config;


public class ScopedSecretService extends ScopedSecretLoader implements ISecretService {

    private final ISecretService delegate;

    public static ISecretService rootScope(ISecretService delegate) {
        return new ScopedSecretService(delegate, ROOT_SCOPE);
    }

    protected ScopedSecretService(ISecretService delegate, String scope) {
        super(delegate, scope);
        this.delegate = delegate;
    }

    @Override
    public void init(ConfigManager configManager, boolean createIfMissing) {
        delegate.init(configManager, createIfMissing);
    }

    @Override
    public ISecretService scope(String scope) {
        var childScope = translateScope(scope);
        return new ScopedSecretService(delegate, childScope);
    }

    @Override
    public String storePassword(String secretName, String password) {
        return delegate.storePassword(translateScope(secretName), password);
    }

    @Override
    public void deleteSecret(String secretName) {
        delegate.deleteSecret(translateScope(secretName));
    }

    @Override
    public void commit() {
        delegate.commit();
    }
}
