/*
 *
 * *********************************************************************
 * fsdevtools
 * %%
 * Copyright (C) 2016 e-Spirit AG
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * *********************************************************************
 *
 */

package com.espirit.moddev.projectservice.projectimport;

import java.util.Map;

public class ProjectImportParametersBuilder {
    private String projectName;
    private String projectFile;
    private Map<String, String> databases;
    private String projectDescription;
    private boolean fsForceProjectActivation;

    public ProjectImportParametersBuilder setProjectName(String projectName) {
        this.projectName = projectName;
        return this;
    }

    public ProjectImportParametersBuilder setProjectFile(String projectFile) {
        this.projectFile = projectFile;
        return this;
    }

    public ProjectImportParametersBuilder setDatabases(Map<String, String> databases) {
        this.databases = databases;
        return this;
    }

    public ProjectImportParametersBuilder setProjectDescription(String projectDescription) {
        this.projectDescription = projectDescription;
        return this;
    }

    public ProjectImportParametersBuilder setFsForceProjectActivation(boolean fsForceProjectActivation) {
        this.fsForceProjectActivation = fsForceProjectActivation;
        return this;
    }

    public ProjectImportParameters create() {
        return new ProjectImportParameters(projectName, projectDescription, projectFile, databases, fsForceProjectActivation);
    }
}
