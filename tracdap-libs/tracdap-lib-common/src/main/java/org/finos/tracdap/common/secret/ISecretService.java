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
import java.util.List;

public interface ISecretService extends ISecretLoader {

    List<String> listSecretKeys(String tenant);

    void storePassword(String tenant, String secretName, String password);
    void storePublicKey(String tenant, String secretName, PublicKey publicKey);
    void storeKeyPair(String tenant, String secretName, KeyPair keyPair);
}
