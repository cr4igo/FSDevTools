package com.espirit.moddev.moduleuninstaller;

import de.espirit.firstspirit.io.ServerConnection;
import de.espirit.firstspirit.manager.ModuleManager;
import de.espirit.firstspirit.manager.ProjectAppManager;
import de.espirit.firstspirit.manager.WebAppManager;
import de.espirit.firstspirit.module.WebEnvironment;
import de.espirit.firstspirit.module.descriptor.ComponentDescriptor;
import de.espirit.firstspirit.module.descriptor.ModuleDescriptor;
import de.espirit.firstspirit.server.module.WebAppType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static de.espirit.firstspirit.module.descriptor.ComponentDescriptor.Type.WEBAPP;

public class ModuleUninstaller {
    protected static final Logger LOGGER = LoggerFactory.getLogger(ModuleUninstaller.class);


    public ModuleUninstaller() {
    }

    public void uninstall(ServerConnection connection, long projectId, String moduleName) {
        if (connection == null || !connection.isConnected()) {
            throw new IllegalStateException("Connection is null or not connected!");
        }
        ModuleManager moduleManager = connection.getManager(ModuleManager.class);
        ModuleDescriptor moduleDescriptor = moduleManager.getModule(moduleName);

        uninstallProjectWebApps(projectId, moduleDescriptor, connection.getManager(WebAppManager.class));
        uninstallProjectApps(projectId, moduleDescriptor, connection.getManager(ProjectAppManager.class));
        uninstallModule(moduleName, moduleManager);
    }

    /**
     * Method for uninstalling the web applications of a given module from a given project
     *
     * @param projectId  The id of the project the web applications shall be uninstalled from
     * @param descriptor The descriptor of the module
     * @param webAppManager a FirstSpirit {@link WebAppManager}
     */
    public static void uninstallProjectWebApps(final long projectId, ModuleDescriptor descriptor, WebAppManager webAppManager) {
        if (descriptor == null) {
            throw new IllegalArgumentException("Module descriptor is null!");
        }
        if (webAppManager == null) {
            throw new IllegalArgumentException("WebAppManager is null!");
        }

        LOGGER.info("Uninstalling project webapps");
        Arrays.stream(descriptor.getComponents()).filter(it -> WEBAPP.equals(it.getScope())).forEach(componentDescriptor -> {
            for (WebEnvironment.WebScope scope : WebEnvironment.WebScope.values()) {
                if (!scope.equals(WebEnvironment.WebScope.GLOBAL)) {
                    WebAppType webAppType = new WebAppType(projectId, scope);
                    webAppManager.uninstallWebApp(descriptor.getModuleName(), componentDescriptor.getName(), webAppType);
                    LOGGER.info("Uninstalled webapp of type " + webAppType);
                }
            }
        });
    }

    /**
     * Method for uninstalling the project applications of a given module from a given project
     *
     * @param projectId  The id of the project the project applications shall be uninstalled from
     * @param descriptor The descriptor of the module
     * @param projectAppManager a FirstSpirit {@link ProjectAppManager}
     */
    public static void uninstallProjectApps(final long projectId, ModuleDescriptor descriptor, ProjectAppManager projectAppManager) {
        if (descriptor == null) {
            throw new IllegalArgumentException("Module descriptor is null! Module not installed on server?");
        }
        if (projectAppManager == null) {
            throw new IllegalArgumentException("WebAppManager is null!");
        }

        LOGGER.info("Uninstalling project apps");
        final ComponentDescriptor[] componentDescriptors = descriptor.getComponents();
        for (final ComponentDescriptor componentDescriptor : componentDescriptors) {
            if (componentDescriptor != null) {
                if (componentDescriptor.getType().equals(ComponentDescriptor.Type.PROJECTAPP)) {
                    projectAppManager.uninstallProjectApp(descriptor.getModuleName(), componentDescriptor.getName(), projectId);
                    LOGGER.info("Uninstalled project app for project with id " + projectId);
                }
            }
        }
    }

    /**
     * Method for uninstalling a module. Make sure to first uninstall the project and web applications belonging to the given module before calling
     * this method.
     *
     * @param moduleName The module to be uninstalled
     * @param moduleManager a FirstSpirit {@link ModuleManager}
     */
    public static void uninstallModule(final String moduleName, ModuleManager moduleManager) {
        if (moduleName == null || moduleName.isEmpty()) {
            throw new IllegalArgumentException("Module name is null or empty!");
        }
        if (moduleManager == null) {
            throw new IllegalArgumentException("WebAppManager is null!");
        }
        LOGGER.info("Uninstalling module");
        moduleManager.uninstall(moduleName);
    }

}
