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

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.util.TimeValue;

import com.damnhandy.uri.template.UriTemplate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.nfalco79.sonarqube.client.Credentials.UserPassword;
import com.github.nfalco79.sonarqube.client.internal.rest.PaginatedResponse;
import com.github.nfalco79.sonarqube.client.internal.rest.PaginatedResponse.Paging;
import com.github.nfalco79.sonarqube.client.internal.rest.ProjectLinks;
import com.github.nfalco79.sonarqube.client.internal.rest.ProjectSearchResponse;
import com.github.nfalco79.sonarqube.client.internal.rest.WebhookResponse;
import com.github.nfalco79.sonarqube.client.model.ALMSettings;
import com.github.nfalco79.sonarqube.client.model.Authentication;
import com.github.nfalco79.sonarqube.client.model.Project;
import com.github.nfalco79.sonarqube.client.model.ProjectLink;
import com.github.nfalco79.sonarqube.client.model.Webhook;

/**
 * Client of Bitbucket Cloud.
 * 
 * @author Nikolas Falco
 */
public class SonarqubeServerClient implements Closeable {

    // private static final String DEFAULT_PAGE_LEN = "100";

    private static final String QUERY_PARAM_QUERY = "q";
    private static final String QUERY_PARAM_PRJS = "projects";
    private static final String QUERY_PARAM_PRJ = "project";
    private static final String QUERY_PARAM_PRJ_KEY = "projectKey";
    // private static final String QUERY_PARAM_PRJ_ID = "projectId";
    private static final String QUERY_PARAM_REPO_KEY = "repository";
    private static final String QUERY_PARAM_SECRET = "secret";
    private static final String QUERY_PARAM_WEBHOOK_URL = "url";
    private static final String QUERY_PARAM_WEBHOOK_NAME = "name";
    private static final String QUERY_PARAM_WEBHOOK_KEY = "webhook";
    private static final String QUERY_PARAM_ALM_KEY = "almSetting";
    private static final String QUERY_PARAM_PAGE = "p";
    private static final String QUERY_PARAM_PAGESIZE = "ps";
    
    // REST APIs
    private static final String AUTHENTICATION_VALIDATE = "/api/authentication/validate";
    private static final String AUTHENTICATION_LOGIN = "/api/authentication/login{?login,password}";
    private static final String PROJECTS_SEARCH = "/api/projects/search{?projects,qualifiers,q,p,ps}";
    private static final String PROJECT_LINKS = "/api/project_links/search{?projectId,projectKey}";
    private static final String ALM_SETTINGS = "/api/alm_settings/get_binding{?project}";
    private static final String ALM_SETTINGS_BB = "/api/alm_settings/set_bitbucketcloud_binding{?almSetting,project,repository}";
    private static final String WEBHOOK_GET = "/api/webhooks/list{?project}";
    private static final String WEBHOOK_CREATE = "/api/webhooks/create{?name,project,secret,url}";
    private static final String WEBHOOK_DELETE = "/api/webhooks/delete{?webhook}";

    protected final Logger logger = Logger.getLogger("BitcketCloudClient");

    protected ObjectMapper objectMapper;
    private Credentials credentials;
    private int retry = 3;
    private boolean dryRun;
    private CloseableHttpClient client;
    private final String serverURL;

    /**
     * BBClient constructor which requires server info.
     *
     * @param serverURL sonarqube URL
     * @param credentials the object containing the server info
     */
    public SonarqubeServerClient(String serverURL, Credentials credentials) {
        this.serverURL = serverURL;
        this.credentials = credentials;
        buildJSONConverter();
        buildClient();
    }

    private <T> List<T> getPaginated(UriTemplate template, Class<? extends PaginatedResponse<T>> type) throws ClientException {
        List<T> result = new ArrayList<T>();
        String uri = template.expand();
        while (uri != null) {
            PaginatedResponse<T> response = process(new HttpGet(uri), type);
            Paging page = response.getPaging();
            int last = page.getPageSize() * page.getPageIndex();
            if (last < page.getTotal()) {
                uri = template.set(QUERY_PARAM_PAGE, page.getPageIndex() + 1) //
                        .set(QUERY_PARAM_PAGESIZE, page.getPageSize()) //
                        .expand();
            } else {
                uri = null;
            }
            result.addAll(response.getComponents());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    protected <T> T process(HttpUriRequest request, Object type) throws ClientException {
        CloseableHttpResponse response = null;
        try {
            setupRequest(request);
            response = client.execute(request);
        } catch (IOException e) {
            throw new ClientException("Client fails on URL " + request.getRequestUri(), e);
        }

        if (response.getCode() == HttpStatus.SC_NO_CONTENT) {
            return null;
        } else if (response.getCode() >= HttpStatus.SC_OK && response.getCode() < 300) {
            try {
                if (type instanceof Class) {
                    return objectMapper.readValue(response.getEntity().getContent(), (Class<T>) type);
                } else if (type instanceof TypeReference) {
                    return objectMapper.readValue(response.getEntity().getContent(), (TypeReference<T>) type);
                } else {
                    return null;
                }
            } catch (UnsupportedOperationException | IOException e) {
                throw new ClientException("Fail to deserialize response.", e);
            }
        } else {
            throw new ClientException(response);
        }
    }

    private <T> T process(HttpUriRequest request) throws ClientException {
        return process(request, null);
    }

    /**
     * Login user with provided credentials.
     *
     * @throws ClientException in case of HTTP response from server different
     *         than 20x codes
     */
    public void login() throws ClientException {
        String requestURI = UriTemplate.buildFromTemplate(serverURL + AUTHENTICATION_LOGIN) //
                .build() //
                .set("login", credentials.getUser()) //
                .set("password", credentials.getPassword()) //
                .expand();
        if (credentials instanceof UserPassword) {
            process(new HttpPost(requestURI));
        } else {
            throw new ClientException("Unsupported programmatic logic provided credentials", null);
        }
    }

    /**
     * Validate provided credentials.
     *
     * @return {@code true} is connection and credentials are verified with
     *         success, {@code false} otherwise.
     * @throws ClientException in case of HTTP response from server different
     *         than 20x codes
     */
    public boolean testConnection() throws ClientException {
        try {
            String requestURI = UriTemplate.fromTemplate(serverURL + AUTHENTICATION_VALIDATE).expand();
            Authentication result = process(new HttpGet(requestURI), Authentication.class);
            return result.isValid();
        } catch (ClientException e) {
            return false;
        }
    }

    /**
     * Gets all projects the use have access to.
     * 
     * @return list of Sonarqube project
     * @throws ClientException in case of HTTP response from server different
     *         than 20x codes
     */
    public List<Project> getProjects() throws ClientException {
        UriTemplate template = UriTemplate.fromTemplate(serverURL + PROJECTS_SEARCH);
        return getPaginated(template, ProjectSearchResponse.class);
    }

    /**
     * Gets all projects the use have access to.
     * 
     * @param searchKey Limit search to component names that contain the
     *        supplied string or component keys that contain the supplied string
     * @return list of Sonarqube project
     * @throws ClientException in case of HTTP response from server different
     *         than 20x codes
     */
    public List<Project> getProjects(String searchKey) throws ClientException {
        UriTemplate template = UriTemplate.fromTemplate(serverURL + PROJECTS_SEARCH) //
                .set(QUERY_PARAM_QUERY, searchKey);
        return getPaginated(template, ProjectSearchResponse.class);
    }

    /**
     * Get project associated with the given key.
     * 
     * @param key sonar project key
     * @return a Sonarqube project
     * @throws ClientException in case of HTTP response from server different
     *         than 20x codes
     */
    public List<Project> getProject(String key) throws ClientException {
        UriTemplate template = UriTemplate.fromTemplate(serverURL + PROJECTS_SEARCH) //
                .set(QUERY_PARAM_PRJS, key);
        return getPaginated(template, ProjectSearchResponse.class);
    }

    /**
     * Gets ALM settings associate to the given project key.
     * 
     * @param key sonar project key
     * @return ALM settings
     * @throws ClientException in case of HTTP response from server different
     *         than 20x codes
     */
    public ALMSettings getALMSettings(String key) throws ClientException {
        String requestURI = UriTemplate.fromTemplate(serverURL + ALM_SETTINGS) //
                .set(QUERY_PARAM_PRJ, key) //
                .expand();
        return process(new HttpGet(requestURI), ALMSettings.class);
    }

    /**
     * Sets ALM settings associate to the given project key.
     * 
     * @param projectKey sonar project key
     * @param almName settings
     * @param repository name that contains sources
     * @return alm settings
     * @throws ClientException in case of HTTP response from server different
     *         than 20x codes
     */
    public ALMSettings setALMSettings(String projectKey, String almName, String repository) throws ClientException {
        String requestURI = UriTemplate.fromTemplate(serverURL + ALM_SETTINGS_BB) //
                .set(QUERY_PARAM_PRJ, projectKey) //
                .set(QUERY_PARAM_ALM_KEY, almName) //
                .set(QUERY_PARAM_REPO_KEY, repository) //
                .expand();
        return process(new HttpGet(requestURI), ALMSettings.class);
    }

    /**
     * Returns all project links of the given project.
     * 
     * @param projectKey sonar project key
     * @return list of project link
     * @throws ClientException in case of HTTP response from server different
     *         than 20x codes
     */
    public List<ProjectLink> getProjectLinks(String projectKey) throws ClientException {
        String requestURI = UriTemplate.fromTemplate(serverURL + PROJECT_LINKS) //
                .set(QUERY_PARAM_PRJ_KEY, projectKey) //
                .expand();
        ProjectLinks result = process(new HttpGet(requestURI), ProjectLinks.class);
        return result.getLinks();
    }

    /**
     * Gets global web hooks.
     * 
     * @return all configured web hooks
     * @throws ClientException in case of HTTP response from server different
     *         than 20x codes
     */
    public List<Webhook> getWebhooks() throws ClientException {
        return getWebhooks(null);
    }

    /**
     * Gets web hooks associated to the given project.
     * 
     * @param projectKey of the project to search for
     * @return all configured web hooks
     * @throws ClientException in case of HTTP response from server different
     *         than 20x codes
     */
    public List<Webhook> getWebhooks(String projectKey) throws ClientException {
        String requestURI = UriTemplate.fromTemplate(serverURL + WEBHOOK_GET) //
                .set(QUERY_PARAM_PRJ, projectKey) //
                .expand();
        WebhookResponse result = process(new HttpGet(requestURI), WebhookResponse.class);
        return result.getWebhooks();
    }

    /**
     * Creates a new web hook to the specified project.
     * 
     * @param projectKey of the project to search for
     * @param webhook to create
     * @return created web hook 
     * @throws ClientException in case of HTTP response from server different
     *         than 20x codes
     */
    public Webhook addWebhook(String projectKey, Webhook webhook) throws ClientException {
        String requestURI = UriTemplate.fromTemplate(serverURL + WEBHOOK_CREATE) //
                .set(QUERY_PARAM_PRJ, projectKey) //
                .set(QUERY_PARAM_WEBHOOK_NAME, webhook.getName()) //
                .set(QUERY_PARAM_WEBHOOK_URL, webhook.getURL()) //
                .set(QUERY_PARAM_SECRET, webhook.getSecret()) //
                .expand();
        WebhookResponse result = process(new HttpPost(requestURI), WebhookResponse.class);
        return result.getWebhook();
    }

    /**
     * Creates a new global web hook.
     * 
     * @param webhook to create
     * @return created web hook 
     * @throws ClientException in case of HTTP response from server different
     *         than 20x codes
     */
    public Webhook addWebhook(Webhook webhook) throws ClientException {
        return addWebhook(null, webhook);
    }

    /**
     * Deletes a web hook that matches the given identifier.
     * 
     * @param webhookKey webhook identifier
     * @throws ClientException in case of HTTP response from server different
     *         than 20x codes
     */
    public void deleteWebhook(String webhookKey) throws ClientException {
        String requestURI = UriTemplate.fromTemplate(serverURL + WEBHOOK_DELETE) //
                .set(QUERY_PARAM_WEBHOOK_KEY, webhookKey) //
                .expand();
        process(new HttpPost(requestURI));
    }

    private void setupRequest(HttpUriRequest request) throws ClientException {
        addHeader(request, HttpHeaders.ACCEPT, "application/json;charset=utf-8");
        credentials.apply(request);
    }

    private void addHeader(HttpUriRequest request, String key, String value) {
        if (request.getFirstHeader(key) == null) {
            request.addHeader(key, value);
        }
    }

    protected void buildClient() {
        client = HttpClients.custom() //
                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(retry, TimeValue.ofSeconds(2))) //
                .build();
    }

    private void buildJSONConverter() {
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    public int getRetry() {
        return retry;
    }

    public void setRetry(int retry) {
        this.retry = retry;
        buildClient();
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

}
