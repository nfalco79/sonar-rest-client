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
package com.github.nfalco79.bitbucket.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.nfalco79.sonarqube.client.ClientException;
import com.github.nfalco79.sonarqube.client.Credentials.CredentialsBuilder;
import com.github.nfalco79.sonarqube.client.SonarqubeServerClient;
import com.github.nfalco79.sonarqube.client.model.ALMSettings;
import com.github.nfalco79.sonarqube.client.model.Project;
import com.github.nfalco79.sonarqube.client.model.ProjectLink;
import com.github.nfalco79.sonarqube.client.model.Webhook;

public class CloudClientTest {

    private SonarqubeServerClient client;
    private Collection<URL> uriCalls;

    @Before
    public void setupClient() throws Exception {
        // LogManager.getLogManager().readConfiguration(getClass().getResourceAsStream("/logging.properties"));

        uriCalls = new LinkedList<>();
        client = new SonarqubeServerClient("http://localhost:9000", CredentialsBuilder.basic("user", "password")) {
            @SuppressWarnings("unchecked")
            @Override
            protected <T> T process(HttpUriRequest request, Object type) throws ClientException {
                try {
                    URL requestURI = request.getUri().toURL();
                    uriCalls.add(requestURI);
                    String query = requestURI.getQuery();
                    String resource = requestURI.getPath() //
                            + "/response" + (query != null ? "_" + query : "") + ".json";
                    try (InputStream is = CloudClientTest.class.getResourceAsStream(resource)) {
                        if (type instanceof Class) {
                            return objectMapper.readValue(is, (Class<T>) type);
                        } else if (type instanceof TypeReference) {
                            return objectMapper.readValue(is, (TypeReference<T>) type);
                        } else {
                            return null;
                        }
                    } catch (UnsupportedOperationException | IOException e) {
                        throw new ClientException("Fail to deserialize response.", e);
                    }
                } catch (MalformedURLException | URISyntaxException e) {
                    throw new RuntimeException("", e);
                }
            }
        };
    }

    @Test(expected = ClientException.class)
    public void test_unsupported_login_credentials() throws Exception {
        client = new SonarqubeServerClient("http://localhost:9000", CredentialsBuilder.apiToken("0123456789"));
        client.login();
    }

    @Test
    public void login() throws Exception {
        client.login();
    }

    @Test
    public void projects() throws Exception {
        List<Project> projects = client.getProjects();
        assertThat(projects).isNotEmpty().hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    public void projects_with_filter() throws Exception {
        List<Project> projects = client.getProjects("calendar.parent");
        assertThat(projects).isNotEmpty().hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    public void specific_project() throws Exception {
        List<Project> projects = client.getProjects("calendar.parent");
        assertThat(projects).isNotEmpty().hasSize(1);
    }

    @Test
    public void alm_settings() throws Exception {
        ALMSettings almSettings = client.getALMSettings("calendar.parent");
        assertThat(almSettings).isNotNull();
    }

    @Test
    public void project_links() throws Exception {
        List<ProjectLink> links = client.getProjectLinks("calendar.parent");
        assertThat(links).isNotNull();
    }

    @Test
    public void global_webhooks() throws Exception {
        List<Webhook> webhooks = client.getWebhooks();
        assertThat(webhooks).isNotEmpty().hasSize(1);
    }

    @Test
    public void new_global_webhooks() throws Exception {
        Webhook webhook = new Webhook();
        webhook.setName("test");
        webhook.setURL("http://www.google.com/sonarqube-webhook");
        webhook.setSecret("mysecret");
        Webhook created = client.addWebhook(webhook);
        assertThat(created.getName()).isEqualTo(webhook.getName());
        assertThat(created.getSecret()).isEqualTo(webhook.getSecret());
        assertThat(created.getURL()).isEqualTo(webhook.getURL());
        assertThat(created.getKey()).isNotNull();
    }

    @Test
    public void delete_global_webhooks() throws Exception {
        String webhookKey = "AX8MRJ99IQ7HBt-oGaf8";
        client.deleteWebhook(webhookKey);
        assertThat(uriCalls).contains(new URL("http://localhost:9000/api/webhooks/delete?webhook=" + webhookKey));
    }

    @Test
    public void project_webhooks() throws Exception {
        List<Webhook> webhooks = client.getWebhooks("calendar.parent");
        assertThat(webhooks).isNotEmpty().hasSize(1);
    }
}