package com.espirit.moddev.moduleinstaller;

import java.util.HashMap;
import java.util.Map;

public class ModuleInstallationParameters {
    private final String projectName;
    private final String fsm;
    private final Map<String, String> serviceConfigurations;
    private final Map<String, String> projectAppConfigurations;
    private final Map<String, String> webAppScopes;
    private final Map<String, String> webAppConfigurations;

    /**
     * @param projectName the name of the FirstSpirit project the module's components should be installed to
     * @param fsm the module file's path
     * @param serviceConfigurations configurations for the module's services
     * @param projectAppConfigurations configurations for the module's project apps
     * @param webAppScopes scope configurations for the module's webapps
     * @param webAppConfigurations configurations for the module's webapps
     */
    public ModuleInstallationParameters(String projectName, String fsm, Map<String, String> serviceConfigurations, Map<String, String> projectAppConfigurations, Map<String, String> webAppScopes, Map<String, String> webAppConfigurations) {
        this.projectName = projectName;
        this.fsm = fsm;
        this.serviceConfigurations = serviceConfigurations != null ? serviceConfigurations : new HashMap<>();
        this.projectAppConfigurations = projectAppConfigurations != null ? projectAppConfigurations : new HashMap<>();
        this.webAppScopes = webAppScopes != null ? webAppScopes : new HashMap<>();
        this.webAppConfigurations = webAppConfigurations != null ? webAppConfigurations : new HashMap<>();
    }

    public String getProjectName() {
        return projectName;
    }

    public String getFsm() {
        return fsm;
    }

    public Map<String, String> getServiceConfigurations() {
        return serviceConfigurations;
    }

    public Map<String, String> getProjectAppConfigurations() {
        return projectAppConfigurations;
    }

    public Map<String, String> getWebAppScopes() {
        return webAppScopes;
    }

    public Map<String, String> getWebAppConfigurations() {
        return webAppConfigurations;
    }
}
