package com.espirit.moddev.moduleinstaller;

import de.espirit.firstspirit.access.project.Project;
import de.espirit.firstspirit.access.store.LockException;
import de.espirit.firstspirit.agency.ModuleAdminAgent;
import de.espirit.firstspirit.agency.ModuleAdminAgent.ModuleResult;
import de.espirit.firstspirit.io.FileHandle;
import de.espirit.firstspirit.io.FileSystem;
import de.espirit.firstspirit.io.ServerConnection;
import de.espirit.firstspirit.manager.*;
import de.espirit.firstspirit.module.*;
import de.espirit.firstspirit.module.WebEnvironment.WebScope;
import de.espirit.firstspirit.module.descriptor.*;
import de.espirit.firstspirit.server.module.WebAppType;
import de.espirit.firstspirit.server.module.WebAppUtil;
import de.espirit.firstspirit.server.projectmanagement.ProjectDTO;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

import static de.espirit.firstspirit.module.WebEnvironment.WebScope.PREVIEW;
import static de.espirit.firstspirit.module.WebEnvironment.WebScope.STAGING;
import static de.espirit.firstspirit.module.WebEnvironment.WebScope.WEBEDIT;
import static de.espirit.firstspirit.module.descriptor.ComponentDescriptor.Type.SERVICE;

public class ModuleInstaller {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ModuleInstaller.class);


    /**
     * Instantiates a {@link ModuleInstaller}. Doesn't do anything else.
     */
    public ModuleInstaller() {
        // Nothing to do here
    }

    /**
     * Method for installing a given FirstSpirit module (only the module itself will be installed, no components will be added to any project).
     *
     * @param fsm        The path to the FirstSpirit module file (fsm) to be installed
     * @param connection A {@link ServerConnection} to the server the module shall be installed to
     * @return An optional ModuleResult. Result might be absent when there's an exception with the fsm file stream.
     */
    private static Optional<ModuleResult> installModule(final File fsm, final ServerConnection connection) {
        LOGGER.info("Starting module installation");
        ModuleAdminAgent moduleAdminAgent = connection.getBroker().requireSpecialist(ModuleAdminAgent.TYPE);
        boolean updateUsages = false;

        try (FileInputStream fsmStream = new FileInputStream(fsm)) {
            ModuleResult result = moduleAdminAgent.install(fsmStream, updateUsages);
            moduleAdminAgent.setTrusted(result.getDescriptor().getName(), true);
            return Optional.of(result);
        } catch (IOException e) {
            LOGGER.error("Exception during module installation!", e);
        }
        return Optional.empty();
    }

    /**
     * Method for activating auto start of services of a given module
     *  @param connection A {@link ServerConnection} to the server
     * @param parameters
     * @param descriptor the module descriptor
     */
    private static void activateServices(final ServerConnection connection, ModuleInstallationParameters parameters, ModuleDescriptor descriptor) {
        String moduleName = descriptor.getModuleName();
        ProjectDTO project = connection.getManager(ProjectManager.class).getProjectByName(parameters.getProjectName());
        if(project == null) {
            throw new IllegalArgumentException("Project " + parameters.getProjectName() + " not found!");
        }
        long projectId = project.getID();
        LOGGER.info("ModuleInstaller activateServices ...");

        ModuleAdminAgent moduleAdminAgent = connection.getBroker().requireSpecialist(ModuleAdminAgent.TYPE);
        Optional<ModuleDescriptor> moduleDescriptor = moduleAdminAgent.getModules().stream().filter(it -> it.getName().equals(moduleName)).findFirst();

        if(!moduleDescriptor.isPresent()) {
            LOGGER.info("ModuleDescriptor not present!");
        }

        final ComponentDescriptor[] componentDescriptors = descriptor.getComponents();
        if (componentDescriptors == null) {
            LOGGER.error("No components found for module: " + moduleName);
        } else {
            Arrays.stream(componentDescriptors).filter(it -> it.getType().equals(SERVICE)).forEach(serviceDescriptor -> {
                LOGGER.info("Found service " + serviceDescriptor.getName());
                File configuration = parameters.getServiceConfigurations().get(serviceDescriptor.getName());
                if(configuration != null) {
                    createConfigurationFile(SERVICE, connection, serviceDescriptor, configuration, moduleName, projectId, null);
                    setAutostartAndRestartService(connection, moduleAdminAgent, serviceDescriptor);
                } else {
                    LOGGER.info("No configuration found for service " + serviceDescriptor.getName());
                }
            });
        }
    }

    private static void setAutostartAndRestartService(ServerConnection connection, ModuleAdminAgent moduleAdminAgent, ComponentDescriptor componentDescriptor) {
        String componentDescriptorName = componentDescriptor.getName();
        moduleAdminAgent.setAutostart(componentDescriptorName, true);
        LOGGER.info("Stopping service {}", componentDescriptorName);
        moduleAdminAgent.stopService(componentDescriptorName);
        LOGGER.info("Starting service {}", componentDescriptorName);
        connection.getManager(ServiceManager.class).startService(componentDescriptorName);
        LOGGER.info("Service {} running: {}", componentDescriptorName, moduleAdminAgent.isRunning(componentDescriptorName));
    }

    /**
     * Convenience method for copying the configuration files from the module to the server-dirs
     * @param type                  Type of the module whose configuration should be written e.g. Service, ProjectApp
     * @param connection            A {@link ServerConnection} to the server
     * @param componentDescriptor   The component from the module.xml to use
     * @param configurationFile The map from the pom.xml that includes the configuration files
     * @param moduleName            The name of the module whose configuration should be written (nullable)
     * @param projectId             The id of the project the project applications shall be installed to
     * @param scope                 The scope to use - only used by webapp configurations
     */
    private static void createConfigurationFile(ComponentDescriptor.Type type,
                                                ServerConnection connection,
                                                ComponentDescriptor componentDescriptor,
                                                File configurationFile,
                                                String moduleName,
                                                long projectId, WebScope scope) {
        LOGGER.info("Config created, preparing for saving");
        Optional<FileSystem<?>> fsOptional = getFileSystemForConfigurationType(type, connection, componentDescriptor, moduleName, projectId, scope);
        fsOptional.ifPresent(fs -> {
            LOGGER.info("Obtaining handle");
            FileHandle handle = null;
            try {
                handle = fs.obtain(getConfigFileName(componentDescriptor) + ".ini");
                LOGGER.info("Saving handle to " + handle.getPath());
                handle.save(new FileInputStream(configurationFile));
            } catch (IOException e) {
                LOGGER.error("Cannot obtain and save file handle!", e);
            }
        });

        LOGGER.info("Configuration files created");
    }

    private static Optional<FileSystem<?>> getFileSystemForConfigurationType(ComponentDescriptor.Type type, ServerConnection connection, ComponentDescriptor componentDescriptor, String moduleName, long projectId, WebScope scope) {
        FileSystem<?> fs = null;
        if (type.equals(SERVICE)) {
            final ModuleManager mm = connection.getManager(ModuleManager.class);
            fs = mm.getFileSystem(EnvironmentDescriptor.create((ServiceDescriptor) componentDescriptor), ModuleManager.DirType.CONF);
        } else if (type.equals(ComponentDescriptor.Type.PROJECTAPP)) {
            final ProjectAppManager pm = connection.getManager(ProjectAppManager.class);
            fs = pm.getConfDir(moduleName, componentDescriptor.getName(), projectId);
        } else if (type.equals(ComponentDescriptor.Type.WEBAPP)) {
            WebAppManager mm = connection.getManager(WebAppManager.class);

            ComponentHandler<WebAppDescriptor, WebEnvironment> wHandle = mm.getWebAppHandler(
                    componentDescriptor.getModuleName(),
                    componentDescriptor.getName(),
                    new WebAppType(projectId, scope),
                    connection);

            LOGGER.info("ComponentDescriptor: " + wHandle.getDescriptor().getName());

            fs = wHandle.getEnvironment().getConfDir();
        }
        return Optional.ofNullable(fs);
    }

    /**
     * Method for installing the project applications of a given module into a given project
     *
     * @param connection A {@link ServerConnection} to the server
     * @param moduleName The name of the module whose project applications shall be installed
     * @param parameters
     */
    private static void installProjectApps(final ServerConnection connection, final String moduleName, final ModuleInstallationParameters parameters) {
        Project project = connection.getProjectByName(parameters.getProjectName());
        LOGGER.info("Installing project apps for {} project {}", moduleName, project.getName());
        ModuleAdminAgent moduleAdminAgent = connection.getBroker().requireSpecialist(ModuleAdminAgent.TYPE);
        Optional<ModuleDescriptor> moduleDescriptor = getModuleDescriptor(moduleAdminAgent, moduleName);
        if (moduleDescriptor.isPresent()) {
            Arrays.asList(moduleDescriptor.get().getComponents()).stream().filter(it -> it instanceof ProjectAppDescriptor).forEach(projectAppDescriptor -> {
                LOGGER.info("ProjectDescriptor {} is processed", projectAppDescriptor.getName());

                FileSystem<?> projectAppConfig = null;
                try {
                    projectAppConfig = moduleAdminAgent.getProjectAppConfig(moduleName, projectAppDescriptor.getName(), project);
                } catch (IllegalArgumentException e) {
                    LOGGER.info("projectAppConfig can not be obtained so it is created");
                    LOGGER.debug("", e);
                }
                if (projectAppConfig != null) {
                    LOGGER.info("existing project: {} app config, please install configuration for {} changes manually", project.getName(), moduleName);
                } else {
                    LOGGER.info("Install ProjectApp");
                    moduleAdminAgent.installProjectApp(moduleName, projectAppDescriptor.getName(), project);
                    LOGGER.info("Create configuration files");
                    parameters.getProjectAppConfiguration().ifPresent(projectAppFile -> {
                        createConfigurationFile(ComponentDescriptor.Type.PROJECTAPP, connection, projectAppDescriptor, projectAppFile, moduleName, project.getId(), null);
                    });
                }
            });
        } else {
            LOGGER.error("No descriptor for {} found!", moduleName);
        }
        LOGGER.info("Installing project apps finished");
    }

    protected static Optional<ModuleDescriptor> getModuleDescriptor(ModuleAdminAgent moduleAdminAgent, String moduleName) {
        for (ModuleDescriptor moduleDescriptor : moduleAdminAgent.getModules()) {
            if (moduleDescriptor.getModuleName().equals(moduleName)) {
                return Optional.of(moduleDescriptor);
            }
        }
        return Optional.empty();
    }

    /**
     * Method for installing the web applications of a given module into a given project
     *  @param connection A {@link ServerConnection} to the server
     * @param parameters
     * @param moduleName The name of the module whose web applications shall be installed
     */
    public static boolean installProjectWebApps(final ServerConnection connection, ModuleInstallationParameters parameters, final String moduleName) {
        ProjectDTO project = connection.getManager(ProjectManager.class).getProjectByName(parameters.getProjectName());
        long projectId = project.getID();
        final ModuleManager mm = connection.getManager(ModuleManager.class);
        final ModuleDescriptor descriptor = mm.getModule(moduleName);
        final WebAppManager wm = connection.getManager(WebAppManager.class);

        createWebAppConfigurationFiles(connection, moduleName, projectId, wm, descriptor.getComponents(), parameters.getWebAppScopes(), parameters.getWebAppConfigurations());
        return installWebAppsAndActivateWebServer(connection, projectId);
    }

    private static boolean installWebAppsAndActivateWebServer(ServerConnection connection, long projectId) {
        LOGGER.info("Installing Project WebApps");
        WebServerHandler activeWebServer = connection.getManager(WebServerManager.class).getWebServerHandler(connection, WebServerManager.WEBSERVER_INTERNAL_INSTANCE_NAME);

        if (activeWebServer != null) {
            return installWebAppAndActivateWebServerForScopes(connection, projectId, activeWebServer);
        } else {
            LOGGER.error("Cannot get WebServer!");
            return false;
        }
    }

    private static boolean installWebAppAndActivateWebServerForScopes(ServerConnection connection, long projectId, WebServerHandler activeWebServer) {
        WebScope[] webScopes = {PREVIEW, STAGING, WEBEDIT};
        Optional<Boolean> failed = Arrays.stream(webScopes).map(scope -> installWebAppAndActivateWebServer(scope, projectId, connection, activeWebServer)).filter(it -> !it).findAny();
        return !failed.isPresent();
    }

    private static void createWebAppConfigurationFiles(ServerConnection connection, String moduleName, long projectId, WebAppManager wm, ComponentDescriptor[] componentDescriptors, List<WebScope> webAppScopes, Map<WebScope, File> webAppConfigurations) {
        LOGGER.info("Creating WebApp configuration files");
        Arrays.stream(componentDescriptors).filter(it -> ComponentDescriptor.Type.WEBAPP.equals(it.getType())).forEach(componentDescriptor -> {
            for (WebScope scope : webAppScopes) {
                if(webAppConfigurations.containsKey(scope)) {
                    try {
                        WebAppType webAppType = new WebAppType(projectId, scope);

                        wm.installWebApp(moduleName, componentDescriptor.getName(), webAppType, true);
                        createConfigurationFile(ComponentDescriptor.Type.WEBAPP,
                                connection,
                                componentDescriptor,
                                webAppConfigurations.get(scope),
                                moduleName, projectId,
                                scope);

                        LOGGER.info("WebAppScope: " + scope);
                    } catch (IOException | IllegalArgumentException e) {
                        LOGGER.error("Invalid Scope " + scope, e);
                    }
                }
            }
        });
    }

    private static String getConfigFileName(ComponentDescriptor componentDescriptor) {
        String componentDescriptorName = null;
        if (componentDescriptor.getName() != null) {
            componentDescriptorName = componentDescriptor.getName().replace(" ", "_").replaceFirst("-fsm-projectApp", "");
        }
        return componentDescriptorName;
    }

    private static boolean installWebAppAndActivateWebServer(final WebScope webScope, final long projectId,
                                                          final ServerConnection connection, final WebServerHandler activeWebServer) {
        final WebAppType webAppType = new WebAppType(projectId, webScope);
        final String contextPath = connection.getManager(WebServerManager.class).getContextPath(projectId, webScope);
        final Project project = connection.getProjectById(projectId);

        String warFileName = WebAppUtil.getWarFileName(webAppType);
        String appName = warFileName.substring(0, warFileName.length() - 4);
        String contextName = WebAppUtil.getWebAppContextName(projectId, webScope);

        try {
            connection.getManager(WebServerManager.class).deployWar(webAppType, activeWebServer, appName, contextName, contextPath, true);
            if (!setActiveWebServerForProject(webScope, activeWebServer, project)){
                return false;
            }

        } catch (WebApplicationXmlMerge.IllegalWebXmlDataException e) {
            LOGGER.error("Cannot deploy war file!", e);
            return false;
        }
        return true;
    }

    private static boolean setActiveWebServerForProject(WebScope webScope, WebServerHandler activeWebServer, Project project) {
        try {
            project.lock();
            project.setActiveWebServer(webScope.toString(), activeWebServer.getInstanceName());
            project.save();
            project.unlock();
            return true;
        } catch (LockException e) {
            LOGGER.error("Cannot lock and save project!", e);
            return false;
        }
    }

    /**
     * Installs a module on a FirstSpirit server. Uses the given connection.
     *
     * @param connection a connected FirstSpirit connection that is used to install the module
     * @param parameters a parameter bean that defines how the module should be installed
     */
    public boolean install(ServerConnection connection, ModuleInstallationParameters parameters) {
        if (connection == null || !connection.isConnected()) {
            throw new IllegalStateException("Connection is null or not connected!");
        }
        if (parameters.getProjectName() == null || parameters.getProjectName().isEmpty()) {
            throw new IllegalArgumentException("Project name is null or not empty!");
        }

        Optional<ModuleResult> moduleResultOption = installModule(parameters.getFsm(), connection);
        if(moduleResultOption.isPresent()) {
            activateServices(connection, parameters, moduleResultOption.get().getDescriptor());

            String moduleName = moduleResultOption.get().getDescriptor().getName();
            LOGGER.info("Finished module installation for {}", moduleName);

            installProjectApps(connection, moduleName, parameters);
            boolean webAppsSuccessfullyInstalled = installProjectWebApps(connection, parameters, moduleName);
            if(!webAppsSuccessfullyInstalled) {
                LOGGER.error("WebApp installation and activation not successful for module {}", moduleName);
                return false;
            }
            return true;
        }
        return false;
    }
}
