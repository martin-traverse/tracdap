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

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Map;

public interface ISecretLoader {

    boolean hasSecret(String tenant, String secretName);
    SecretHolder loadSecret(String tenant, String secretName);

    boolean hasResourceSecret(String tenant, String resourceName, String secretName);
    SecretHolder loadResourceSecret(String tenant, String resourceName, String secretName);

    String loadPassword(String tenant, String secretName);
    PublicKey loadPublicKey(String tenant, String secretName);
    PrivateKey loadPrivateKey(String tenant, String secretName);
}


class Example {

    public void processAuth() {

        // http://localhost:8080/trac-auth/api/v1/acme_corp/repo1/authorize

        // http://localhost:8080/trac-auth/browser/v1/sandbox/github/authorize

        // http://localhost:8080/trac-auth/rest/v1/acme_corp/repo1/authorize


        var request = Map.<String, String>of();

        var protocolHandler = protocols.get("browser/v1");
        var tenantHandler = tenants.get("sandbox");

        if (request.get("path").contains("web-flow")) {

        }

        else if (request.get("path").contains("callback")) {


        }


    }


    private class GitHubApp {

    }

    private class GitHubOAuth {

    }

    private class StaticToken {

    }

    private class StaticPassword {

    }
}