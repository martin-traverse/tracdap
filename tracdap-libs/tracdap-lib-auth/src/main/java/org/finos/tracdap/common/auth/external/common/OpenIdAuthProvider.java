/*
 * Copyright 2023 Accenture Global Solutions Limited
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

package org.finos.tracdap.common.auth.external.common;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import org.finos.tracdap.common.auth.external.AuthRequest;
import org.finos.tracdap.common.auth.external.AuthResponse;
import org.finos.tracdap.common.auth.external.AuthResult;
import org.finos.tracdap.common.auth.external.IAuthProvider;
import org.finos.tracdap.common.auth.internal.UserInfo;

import java.util.Properties;


public class OpenIdAuthProvider implements IAuthProvider {

    // OpenID Reference:         https://openid.net/specs/openid-connect-core-1_0.html
    // OpenID Reference (Okta):  https://developer.okta.com/docs/reference/api/oidc/
    // Implementation guide:     https://developer.okta.com/docs/guides/implement-grant-type/authcode/main/

    public static final String BASE_URL_KEY = "baseUrl";
    public static final String CLIENT_ID_KEY = "clientId";
    public static final String CLIENT_SECRET_KEY = "clientSecret";

    public static final String MAIN_PAGE_KEY = "mainPage";

    private static final String TRAC_AUTH_ROOT = "/trac-auth/openid/";
    private static final String TRAC_AUTH_LOGIN_PAGE = "/trac-auth/openid/login";
    private static final String TRAC_AUTH_LOGOUT_PAGE = "/trac-auth/openid/logout";

    public OpenIdAuthProvider(Properties properties) {

    }

    @Override
    public AuthResult attemptAuth(AuthRequest request) {

        var headers = request.getHeaders();
        var isApi =
                headers.contains(HttpHeaderNames.CONTENT_TYPE) &&
                headers.get(HttpHeaderNames.CONTENT_TYPE).toString().startsWith("application/");

        if (isApi)
            return AuthResult.FAILED("Session expired or not available");

        if (!request.getUrl().startsWith(TRAC_AUTH_ROOT))
            return redirectToLogin(request);

        if (request.getUrl().equals(TRAC_AUTH_LOGIN_PAGE))
            return processLoginCallback(request);

        if (request.getUrl().equals(TRAC_AUTH_LOGOUT_PAGE))
            return processLogoutCallback(request)



        if (request.getMethod().equals(HttpMethod.POST.name()) &&
                request.getUrl().equals(BUILT_IN_AUTH_PAGE)) {

            if (request.getContent() == null)
                return AuthResult.NEED_CONTENT();

            return checkLoginRequest(request);
        }
        else {

            return serveLoginContent(request, false);
        }
    }

    @Override
    public boolean postAuthMatch(String method, String uri) {
        return false;
    }

    @Override
    public AuthResponse postAuth(AuthRequest authRequest, UserInfo userInfo) {
        return null;
    }

    private AuthResult redirectToLogin(AuthRequest request) {

    }

    private AuthResult processLoginCallback(AuthRequest request) {

    }

    private AuthResult processLogoutCallback(AuthRequest request) {

    }
}
