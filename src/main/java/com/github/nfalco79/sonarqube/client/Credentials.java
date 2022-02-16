/*
 * Copyright 2022 Falco Nikolas
 * Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.github.nfalco79.sonarqube.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpRequest;

/**
 * This object represent the credentials to use in {@link SonarqubeServerClient}.
 * <p>
 * The implementations also setup a request with proper authentication.
 * 
 * @author Nikolas Falco
 */
public abstract class Credentials {

    public static class CredentialsBuilder {
        public static Credentials apiToken(String secret) {
            return new ApiToken(secret);
        }

        public static Credentials basic(String user, String password) {
            return new UserPassword(user, password);
        }
    }

    /* package */ static class UserPassword extends Credentials {
        private UserPassword(String user, String password) {
            super(user, password);
        }

        @Override
        public void apply(HttpRequest request) {
            request.setHeader(HttpHeaders.AUTHORIZATION, getBasicAuth(this));
        }
    }

    /* package */ static class ApiToken extends Credentials {
        private ApiToken(String password) {
            super(null, password);
        }

        @Override
        public void apply(HttpRequest request) {
            String auth = Base64.getEncoder().encodeToString((getPassword() + ":").getBytes(StandardCharsets.UTF_8));
            request.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + auth);
        }
    }

    private static String getBasicAuth(Credentials credentials) {
        return "Basic " + Base64.getEncoder().encodeToString((credentials.getUser() + ":"
                + credentials.getPassword()).getBytes(StandardCharsets.UTF_8));
    }

    private String user;
    private String password;

    private Credentials(String user, String password) {
        this.user = user;
        this.password = password;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public abstract void apply(HttpRequest request);
}