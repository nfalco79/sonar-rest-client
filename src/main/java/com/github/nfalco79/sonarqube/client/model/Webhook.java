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
package com.github.nfalco79.sonarqube.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Webhook {
    private String key;
    private String name;
    @JsonProperty("url")
    private String url;
    private String secret;

    public String getKey() {
        return key;
    }

    public void setKey(String id) {
        this.key = id;
    }

    /**
     * The displayed in the administration console of web hooks.
     * 
     * @return web hook name
     */
    public String getName() {
        return name;
    }

    public void setName(String type) {
        this.name = type;
    }

    /**
     * The endpoint that will receive the web hook payload.
     * 
     * @return web hook url
     */
    public String getURL() {
        return url;
    }

    public void setURL(String url) {
        this.url = url;
    }

    /**
     * Secret will be used as the key to generate the HMAC hex (lowercase)
     * digest value in the 'X-Sonar-Webhook-HMAC-SHA256' header.
     * 
     * @return web hook secret
     */
    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    @Override
    public String toString() {
        return name;
    }
}