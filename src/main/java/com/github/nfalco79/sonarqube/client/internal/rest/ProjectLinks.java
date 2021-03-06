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
package com.github.nfalco79.sonarqube.client.internal.rest;

import java.util.ArrayList;
import java.util.List;

import com.github.nfalco79.sonarqube.client.model.ProjectLink;

public class ProjectLinks {

    private List<ProjectLink> links = new ArrayList<>();

    public List<ProjectLink> getLinks() {
        return links;
    }

    public void setLinks(List<ProjectLink> links) {
        this.links = links;
    }

}