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

package org.finos.tracdap.common.secret;

import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Instant;

public class SecretHolder {

    private final String password;
    private final PublicKey publicKey;
    private final KeyPair keyPair;

    private final Instant expiry;

    public SecretHolder(String password, Instant expiry) {
        this.password = password;
        this.expiry = expiry;
        this.publicKey = null;
        this.keyPair = null;
    }

    public SecretHolder(PublicKey publicKey, Instant expiry) {
        this.publicKey = publicKey;
        this.expiry = expiry;
        this.password = null;
        this.keyPair = null;
    }

    public SecretHolder(KeyPair keyPair, Instant expiry) {
        this.keyPair = keyPair;
        this.expiry = expiry;
        this.password = null;
        this.publicKey = keyPair.getPublic();
    }

    public String password() {
        return password;
    }

    public PublicKey publicKey() {
        return publicKey;
    }

    public KeyPair keyPair() {
        return keyPair;
    }

    public Instant expiry() {
        return expiry;
    }
}
