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


import org.finos.tracdap.common.exception.EStartup;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * Tech stack abstraction interface for loading secrets, certificates and other sensitive configuration items.
 *
 * <p>Cloud platforms provide dedicated services for managing, holding and retrieving secrets.
 * In traditional setups, secrets are managed using encrypted key store files.
 * The secret loader interface is an abstraction that allows sensitive configuration to be accessed
 * using the appropriate mechanism for a given deployment target. ConfigManager will select the
 * correct secret loader depending on the supplied configuration and available plugins.</p>
 *
 * <p>A secret loader that uses JKS files is included in the common library. Implementations for the
 * major cloud platforms are included in the cloud platform plugins.</p>
 *
 * @see ConfigManager
 */
public interface ISecretLoader {

    /**
     * Initialize the secret loader.
     *
     * <p>This method is called before any secrets are requested.
     * If necessary, the supplied ConfigManager can be used to load supporting configuration,
     * such as encrypted key stores or other secret files.</p>
     *
     * @param configManager A ConfigManager instance that can be used to load supporting configuration
     */
    void init(ConfigManager configManager);

    /**
     * Reload secrets from the secret source.
     *
     * <p>This method will invalidate any stale / cached secrets so they will be re-read
     * from the secret source. The exact timing of reads may vary between implementations,
     * some implementations may not read secrets until they are requested.
     */
    void reload();

    /**
     * Provide a child secret loader restricted to the given scope.
     *
     * @param scope The scope for the child secret loader
     * @return A child secret loader restricted to the given scope
     */
    ISecretLoader scope(String scope);

    /**
     * Convenience method for working with named scopes.
     *
     * <p>For example, /tenants/ACME is a named tenant scope
     *
     * @param scopeName Name of the scope (a static outer scope)
     * @param scope Named scope
     *
     * @return A child secret loader restricted to the named scope
     */
    default ISecretLoader namedScope(String scopeName, String scope) {
        return scope(scopeName).scope(scope);
    }

    /**
     * Test whether a secret with the given name exists in the secret store
     *
     * @param secretName The name of the secret to check for
     * @return True if the secret exists, false otherwise
     */
    boolean hasSecret(String secretName);

    /**
     * Load a password from the secret loader as a text string.
     *
     * @param secretName The unique name of the secret to load
     * @return The decoded password, as a string
     * @throws EStartup There was a problem loading the secret
     */
    String loadPassword(String secretName);

    /**
     * Load a public key, which may be part of a public / private key pair
     *
     * <p>Public and private keys are stored separately, rather than as part of a key pair.
     * This is to deal with limitations in several secret services (including JKS) that do
     * not have a good way of dealing with structured secrets. Instead the key is encoded as
     * bytes and stored as a blob.</p>
     *
     * @param secretName Name of the secret holding the public key
     * @return A PublicKey object
     */
    PublicKey loadPublicKey(String secretName);

    /**
     * Load a private key, which may be part of a public / private key pair
     *
     * <p>Public and private keys are stored separately, rather than as part of a key pair.
     * This is to deal with limitations in several secret services (including JKS) that do
     * not have a good way of dealing with structured secrets. Instead the key is encoded as
     * bytes and stored as a blob.</p>
     *
     * @param secretName Name of the secret holding the private key
     * @return A PrivateKey object
     */
    PrivateKey loadPrivateKey(String secretName);
}
