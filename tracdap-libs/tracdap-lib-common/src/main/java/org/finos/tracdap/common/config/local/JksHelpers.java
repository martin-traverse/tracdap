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

package org.finos.tracdap.common.config.local;

import org.finos.tracdap.common.exception.EConfigLoad;
import org.finos.tracdap.common.startup.StartupLog;

import org.slf4j.event.Level;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;


public class JksHelpers {

    public static void writeTextEntry(
            KeyStore keystore, String secretKey,
            String alias, String text)
            throws EConfigLoad {

        try {

            var protection = new KeyStore.PasswordProtection(secretKey.toCharArray());
            var factory = SecretKeyFactory.getInstance("PBE");

            var spec = new PBEKeySpec(text.toCharArray());
            var secret = factory.generateSecret(spec);
            var entry = new KeyStore.SecretKeyEntry(secret);

            keystore.setEntry(alias, entry, protection);
        }
        catch (IllegalArgumentException | GeneralSecurityException e) {
            var message = String.format("Failed to write secret [%s]: %s", alias, e.getMessage());
            throw new EConfigLoad(message, e);
        }
    }

    public static void deleteEntry(
            KeyStore keystore, String alias)
            throws EConfigLoad {

        try {

            if (keystore.containsAlias(alias)) {
                keystore.deleteEntry(alias);
            }
        }
        catch (GeneralSecurityException e) {
            var message = String.format("Failed to read secret [%s]: %s", alias, e.getMessage());
            throw new EConfigLoad(message, e);
        }
    }

    public static boolean containsEntry(
            KeyStore keystore, String alias)
            throws EConfigLoad {

        try {
            return keystore.containsAlias(alias);
        }
        catch (GeneralSecurityException e) {
            var message = String.format("Failed to read secret [%s]: %s", alias, e.getMessage());
            throw new EConfigLoad(message, e);
        }
    }

    public static String readTextEntry(
            KeyStore keystore, String secretKey, String alias)
            throws EConfigLoad {

        try {

            var entry = keystore.getEntry(alias, new KeyStore.PasswordProtection(secretKey.toCharArray()));

            if (entry == null) {
                var message = String.format("Secret is not present in the store: [%s]", alias);
                StartupLog.log(JksHelpers.class, Level.ERROR, message);
                throw new EConfigLoad(message);
            }

            if (!(entry instanceof KeyStore.SecretKeyEntry)) {
                var message = String.format("Secret is not a secret key: [%s] is %s", alias, entry.getClass().getSimpleName());
                StartupLog.log(JksHelpers.class, Level.ERROR, message);
                throw new EConfigLoad(message);
            }

            var secret = (KeyStore.SecretKeyEntry) entry;
            var algorithm = secret.getSecretKey().getAlgorithm();
            var factory = SecretKeyFactory.getInstance(algorithm);

            // Decode using password based encryption
            var keySpecType = PBEKeySpec.class;
            var keySpec = (PBEKeySpec) factory.getKeySpec(secret.getSecretKey(), keySpecType);

            var password = keySpec.getPassword();

            return new String(password);
        }
        catch (IllegalArgumentException | GeneralSecurityException e) {
            var message = String.format("Failed to read secret [%s]: %s", alias, e.getMessage());
            throw new EConfigLoad(message, e);
        }
    }

    public static String encodePublicKey(PublicKey key, boolean mime) {

        try {

            var factory = KeyFactory.getInstance(key.getAlgorithm());

            var spec = factory.getKeySpec(key, X509EncodedKeySpec.class);
            var encoded = spec.getEncoded();

            if (mime) {
                var base64 = Base64.getMimeEncoder(80, "\n".getBytes()).encodeToString(encoded);
                return "-----BEGIN PUBLIC KEY-----\n" +
                        base64 + "\n" +
                        "-----END PUBLIC KEY-----\n";
            }
            else {
                return Base64.getEncoder().encodeToString(encoded);
            }
        }
        catch (IllegalArgumentException | GeneralSecurityException e) {
            var message = String.format("Failed to encode public key: " + e.getMessage());
            throw new EConfigLoad(message, e);
        }
    }

    public static String encodePrivateKey(PrivateKey key, boolean mime) {

        try {

            var factory = KeyFactory.getInstance(key.getAlgorithm());

            var spec = factory.getKeySpec(key, PKCS8EncodedKeySpec.class);
            var encoded = spec.getEncoded();

            if (mime) {
                var base64 = Base64.getMimeEncoder(80, "\n".getBytes()).encodeToString(encoded);
                return "-----BEGIN PRIVATE KEY-----\n" +
                        base64 + "\n" +
                        "-----END PRIVATE KEY-----\n";
            } else {
                return Base64.getEncoder().encodeToString(encoded);
            }
        }
        catch (IllegalArgumentException | GeneralSecurityException e) {
            var message = String.format("Failed to encode private key: " + e.getMessage());
            throw new EConfigLoad(message, e);
        }
    }

    public static PublicKey decodePublicKey(String key, boolean mime) {

        try {

            byte[] encoded;

            if (mime) {

                var base64 = key
                        .replaceAll("-----BEGIN (.*)-----", "")
                        .replaceAll("-----END (.*)-----", "")
                        .replaceAll("\\s+", "");

                encoded = Base64.getMimeDecoder().decode(base64);
            } else {
                encoded = Base64.getDecoder().decode(key);
            }

            // Search for the algorithm to decode the key
            // This might be slow, but the assumption is keys are loaded rarely (normally just on startup)
            // Putting in config for algorithms to match keys, with the right naming convention...
            // Auto-detecting will give a much easier deployment experience

            for (var algorithm : KEY_FACTORY_ALGORITHMS) {

                try {

                    var spec = new X509EncodedKeySpec(encoded);
                    var factory = KeyFactory.getInstance(algorithm);

                    return factory.generatePublic(spec);
                } catch (Exception e) {
                    if (e instanceof InvalidKeySpecException)
                        continue;
                    throw e;
                }
            }

            var algos = String.join(", ", KEY_FACTORY_ALGORITHMS);
            var message = String.format("Failed to decode public key: No suitable algorithm (available algorithms are %s)", algos);

            throw new EConfigLoad(message);
        }
        catch (IllegalArgumentException | GeneralSecurityException e) {
            var message = String.format("Failed to decode public key: " + e.getMessage());
            throw new EConfigLoad(message, e);
        }
    }

    public static PrivateKey decodePrivateKey(String key, boolean mime) {

        try {
            byte[] encoded;

            if (mime) {

                var base64 = key
                        .replaceAll("-----BEGIN (.*)-----", "")
                        .replaceAll("-----END (.*)-----", "")
                        .replaceAll("\\s+", "");

                encoded = Base64.getMimeDecoder().decode(base64);
            } else {
                encoded = Base64.getDecoder().decode(key);
            }

            // Search for the algorithm to decode the key
            // This might be slow, but the assumption is keys are loaded rarely (normally just on startup)
            // Putting in config for algorithms to match keys, with the right naming convention...
            // Auto-detecting will give a much easier deployment experience

            for (var algorithm : KEY_FACTORY_ALGORITHMS) {

                try {

                    var spec = new PKCS8EncodedKeySpec(encoded);
                    var factory = KeyFactory.getInstance(algorithm);

                    return factory.generatePrivate(spec);
                } catch (Exception e) {
                    if (e instanceof InvalidKeySpecException)
                        continue;
                    throw e;
                }
            }

            var algos = String.join(", ", KEY_FACTORY_ALGORITHMS);
            var message = String.format("Failed to decode public key: No suitable algorithm (available algorithms are %s)", algos);

            throw new EConfigLoad(message);
        }
        catch (IllegalArgumentException | GeneralSecurityException e) {
            var message = String.format("Failed to decode private key: " + e.getMessage());
            throw new EConfigLoad(message, e);
        }
    }

    private static final List<String> KEY_FACTORY_ALGORITHMS = List.of("EC", "RSA", "DSA", "DiffieHellman");
}
