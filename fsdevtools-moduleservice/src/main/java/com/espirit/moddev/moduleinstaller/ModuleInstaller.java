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
import de.espirit.firstspirit.module.descriptor.*;
import de.espirit.firstspirit.server.module.WebAppType;
import de.espirit.firstspirit.server.module.WebAppUtil;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.*;

public class ModuleInstaller {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ModuleInstaller.class);


    public ModuleInstaller() {}

    /**
     * Method for installing a given FirstSpirit module (only the module itself will be installed, no components will be added to any project).
     *
     * @param fsm        The path to the FirstSpirit module to be installed
     * @param connection A {@link ServerConnection} to the server the module shall be installed to
     * @return A String representing the name of the installed module
     * @throws Exception Exception
     */
    public static Optional<ModuleResult> installModule(final String fsm, final ServerConnection connection) {
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
     *
     * @param moduleName            The name of the module whose services shall be activated
     * @param serviceConfigurations A map of service configurations files
     * @param connection            A {@link de.espirit.firstspirit.io.ServerConnection} to the server
     */
    public static void activateServices(final String moduleName, Map<String, String> serviceConfigurations,
                                        final ServerConnection connection) {

        LOGGER.info("ModuleInstaller activateServices ...");
        ModuleDescriptor descriptor = null;

        ModuleAdminAgent moduleAdminAgent = connection.getBroker().requireSpecialist(ModuleAdminAgent.TYPE);
        for (ModuleDescriptor moduleDescriptor : moduleAdminAgent.getModules()) {
            if (moduleDescriptor.getName().equals(moduleName)) {
                descriptor = moduleDescriptor;
                break;
            }
        }


        if (moduleName != null && descriptor != null) {
            try {
                final ComponentDescriptor[] componentDescriptors = descriptor
                        .getComponents();
                for (final ComponentDescriptor componentDescriptor : componentDescriptors) {
                    if (componentDescriptor.getType().equals(ComponentDescriptor.Type.SERVICE)) {
                        LOGGER.info("Found service " + componentDescriptor.getName());
                        if (serviceConfigurations != null) {
                            createConfigurationFiles(ComponentDescriptor.Type.SERVICE, connection,
                                    componentDescriptor, serviceConfigurations,
                                    null, 0, null);
                        }
                        String componentDescriptorName = componentDescriptor.getName();
                        moduleAdminAgent.setAutostart(componentDescriptorName, true);
                        LOGGER.info("Stopping service " + componentDescriptorName);
                        moduleAdminAgent.stopService(componentDescriptorName);
                        LOGGER.info("Starting service " + componentDescriptorName);
                        connection.getManager(ServiceManager.class).startService(componentDescriptorName);
                        boolean serviceIsRunning = moduleAdminAgent.isRunning(componentDescriptorName);
                        LOGGER.info("Service " + componentDescriptorName + " running: " + serviceIsRunning);
                    }
                }
            } catch (NullPointerException e) {
                LOGGER.error("No components found for module: " + moduleName);
                e.printStackTrace();
                return;
            }
        } else {
            LOGGER.error("No modulename is set!");
        }
    }

    /**
     * Convenience method for copying the configuration files from the module to the server-dirs
     *
     * @param type                Type of the module whose configuration should be written e.g. Service, ProjectApp
     * @param connection          A {@link ServerConnection} to the server
     * @param componentDescriptor The component from the module.xml to use
     * @param configurations      The map from the pom.xml that includes the configuration files
     * @param moduleName          The name of the module whose configuration should be written (nullable)
     * @param projectId           The id of the project the project applications shall be installed to
     */
    private static void createConfigurationFiles(ComponentDescriptor.Type type,
                                                 ServerConnection connection,
                                                 ComponentDescriptor componentDescriptor,
                                                 Map<String, String> configurations, String moduleName,
                                                 long projectId, WebEnvironment.WebScope scope) {

        LOGGER.info("Creating configuration files");
        List<String> configurationFile = new ArrayList<String>();
        try {
            String replacement = componentDescriptor.getName().replace(" ", "_");
            String name = componentDescriptor.getName();
            if (configurations.get(name) != null || configurations.get(replacement) != null) {
                URL configurationFileUrl = null;
                if (componentDescriptor.getName().contains(" ")) {
                    configurationFileUrl = ModuleInstaller.class.getClassLoader().getResource(configurations.get(replacement));
                    if (configurationFileUrl != null) {
                        configurationFile.add(configurationFileUrl.getFile());
                    } else {
                        configurationFile = Arrays.asList(configurations.get(replacement).split(","));
                    }
                } else {
                    configurationFileUrl = ModuleInstaller.class.getClassLoader().getResource(configurations.get(componentDescriptor.getName()));
                    if (configurationFileUrl != null) {
                        configurationFile.add(configurationFileUrl.getFile());
                    } else {
                        configurationFile = Arrays.asList(configurations.get(componentDescriptor.getName()).split(","));
                    }
                }
            }

        } catch (final Exception e) {
            if (LOGGER != null && configurations != null && componentDescriptor != null) {
                LOGGER.info("CONFIGURATIONS: " + configurations);
                LOGGER.info("NAME: " + componentDescriptor.getName());
                LOGGER.info("Configurationfile '" + configurations.get(componentDescriptor.getName()) + "' not found", e);
            } else {
                LOGGER.info("No configuration file found !");
                LOGGER.info("Configurations: " + configurations);
                LOGGER.info("ComponentDescriptor: " + componentDescriptor);
            }
        }

        LOGGER.info("Config created, preparing for saving");
        FileSystem<?> fs = null;
        try {
            if (configurationFile != null) {
                for (String confFile : configurationFile) {
                    if (type.equals(ComponentDescriptor.Type.SERVICE)) {
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

                    final FileHandle handle;
                    if (fs != null) {
                        LOGGER.info("Obtaining handle");
                        handle = fs
                                .obtain(confFile.indexOf('/') >= 0 ? confFile
                                        .substring(confFile.lastIndexOf('/') + 1)
                                        : confFile);
                        LOGGER.info("Saving handle");
                        handle.save(new FileInputStream(confFile));

                    }
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        LOGGER.info("Configuration files created");
    }

    /**
     * Method for installing the project applications of a given module into a given project
     *
     * @param moduleName               The name of the module whose project applications shall be installed
     * @param projectId                The id of the project the project applications shall be installed to
     * @param projectAppConfigurations A map of projectApps and pathes to according configurations files
     * @param connection               A {@link ServerConnection} to the server
     * @throws IOException IOException
     */
    public static void installProjectApps(final String moduleName,
                                          final long projectId,
                                          final Map<String, String> projectAppConfigurations,
                                          final ServerConnection connection) {

        LOGGER.info("Installing project apps for " + moduleName + " projectId " + projectId);
        ModuleAdminAgent moduleAdminAgent = connection.getBroker().requireSpecialist(ModuleAdminAgent.TYPE);
        Optional<ModuleDescriptor> moduleDescriptor = getModuleDescriptor(moduleAdminAgent, moduleName);
        if (moduleDescriptor.isPresent()) {
            final ComponentDescriptor[] componentDescriptors = moduleDescriptor.get().getComponents();
            if(componentDescriptors.length > 0) {
                for (final ComponentDescriptor componentDescriptor : componentDescriptors) {
                    if (componentDescriptor instanceof ProjectAppDescriptor) {
                        ProjectAppDescriptor projectAppDescriptor = (ProjectAppDescriptor) componentDescriptor;
                        LOGGER.info("ProjectDescriptor " + projectAppDescriptor.getName() + " is processed");

                        Project project = connection.getProjectById(projectId);
                        FileSystem<?> projectAppConfig = null;
                        try {
                            projectAppConfig = moduleAdminAgent.getProjectAppConfig(moduleName, projectAppDescriptor.getName(), project);
                        } catch (IllegalArgumentException e) {
                            LOGGER.info("projectAppConfig can not be obtained so it is created");
                        }
                        if (projectAppConfig != null) {
                            LOGGER.info("existing project: " + project.getName() + " app config, please install configuration for " + moduleName + " changes manually");
                        } else {
                            LOGGER.info("Install ProjectApp");
                            moduleAdminAgent.installProjectApp(moduleName, projectAppDescriptor.getName(), project);
                            LOGGER.info("Create configuration files");
                            createConfigurationFiles(ComponentDescriptor.Type.PROJECTAPP, connection, componentDescriptor, projectAppConfigurations, moduleName, projectId, null);
                        }
                    }
                }
            }
        } else {
            LOGGER.error("no descriptor for " + moduleName + " found!");
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
     *
     * @param moduleName The name of the module whose web applications shall be installed
     * @param projectId  The id of the project the web applications shall be installed to
     * @param connection A {@link ServerConnection} to the server
     * @throws IOException IOException
     */
    public static void installProjectWebApps(final String moduleName,
                                             final long projectId, final Map<String, String> webappscopes,
                                             final Map<String, String> webAppConfigurations,
                                             final ServerConnection connection) {

        LOGGER.info("Installing Project WebApps");
        final ModuleManager mm = connection.getManager(ModuleManager.class);
        final ModuleDescriptor descriptor = mm.getModule(moduleName);
        final WebAppManager wm = connection.getManager(WebAppManager.class);

        final ComponentDescriptor[] componentDescriptors = descriptor.getComponents();

        final WebServerHandler activeWebServer;

        String componentDescriptorName = null;

        for (final ComponentDescriptor componentDescriptor : componentDescriptors) {
            if (ComponentDescriptor.Type.WEBAPP.equals(componentDescriptor.getType())) {
                WebAppType webAppType = null;
                if (componentDescriptor.getName() != null) {
                    componentDescriptorName = componentDescriptor.getName().replace(" ", "_");
                }

                if (webappscopes != null && webappscopes.get(componentDescriptorName) != null) {
                    String webScope = webappscopes.get(componentDescriptorName);

                    if (webScope.contains(",")) {
                        String[] scopes = webScope.split(",");
                        for (String scope : scopes) {
                            scope = scope.trim().toUpperCase();
                            try {
                                if (!scope.equalsIgnoreCase("global")) {
                                    webAppType = new WebAppType(projectId, WebEnvironment.WebScope.valueOf(scope.toUpperCase()));
                                    wm.installWebApp(moduleName, componentDescriptor.getName(), webAppType, true);
                                    createConfigurationFiles(ComponentDescriptor.Type.WEBAPP,
                                            connection,
                                            componentDescriptor,
                                            webAppConfigurations,
                                            moduleName, projectId,
                                            WebEnvironment.WebScope.valueOf(scope
                                                    .toUpperCase()));

                                    if (LOGGER != null && scope != null) {
                                        LOGGER.info("WebAppScope: " + scope);
                                    }
                                }
                            } catch (IOException | IllegalArgumentException e) {
                                LOGGER.error("Invalid Scope " + scope);
                                e.printStackTrace();
                                continue;
                            }

                        }

                    } else if (!webScope.equalsIgnoreCase("global")) {
                        webScope = webScope.trim().toUpperCase();
                        if (WebEnvironment.WebScope.valueOf(webScope) != null) {
                            webAppType = new WebAppType(projectId, WebEnvironment.WebScope.valueOf(webScope));
                        } else {
                            LOGGER.error("invalid scope: " + webScope);
                        }
                        try {
                            wm.installWebApp(moduleName, componentDescriptor.getName(), webAppType,true);
                            createConfigurationFiles(ComponentDescriptor.Type.WEBAPP, connection,
                                    componentDescriptor, webAppConfigurations,
                                    moduleName, projectId,
                                    WebEnvironment.WebScope.valueOf(webScope));

                            LOGGER.info("WebAppScope: " + webScope);
                        } catch (IOException e) {
                            LOGGER.error("Error during webapp installation!", e);
                        }
                    }


                }

                if (webAppType == null) {
                    LOGGER.warn("Missing or unknown webscope for webapp '"
                            + componentDescriptor.getName()
                            + "'. Using default webscope (preview).");
                }

            }

        }

        try {

            activeWebServer = connection.getManager(WebServerManager.class)
                    .getWebServerHandler(connection, WebServerManager.WEBSERVER_INTERNAL_INSTANCE_NAME);

            if (activeWebServer != null) {
                installWebAppAndActivateWebServer(WebEnvironment.WebScope.PREVIEW,
                        projectId, connection, activeWebServer);

                installWebAppAndActivateWebServer(WebEnvironment.WebScope.STAGING,
                        projectId, connection, activeWebServer);

                installWebAppAndActivateWebServer(WebEnvironment.WebScope.WEBEDIT,
                        projectId, connection, activeWebServer);

            }

        } catch (NullPointerException e) {
            LOGGER.error("WebServer not found!", e);
        } catch (WebApplicationXmlMerge.IllegalWebXmlDataException e) {
            LOGGER.error("Illegal web.xml data!", e);
        } catch (LockException e) {
            LOGGER.error("Project is locked. Activation of server failed!", e);
        }
    }

    private static void installWebAppAndActivateWebServer(
            final WebEnvironment.WebScope webScope, final long projectId,
            final ServerConnection connection,
            final WebServerHandler activeWebServer)
            throws WebApplicationXmlMerge.IllegalWebXmlDataException, LockException {
        final WebAppType webAppType = new WebAppType(projectId, webScope);
        final String contextPath = connection.getManager(WebServerManager.class).getContextPath(projectId, webScope);
        final Project project = connection.getProjectById(projectId);

        String warFileName = WebAppUtil.getWarFileName(webAppType);
        String appName = warFileName.substring(0, warFileName.length() - 4);
        String contextName = WebAppUtil.getWebAppContextName(projectId, webScope);

        connection.getManager(WebServerManager.class).deployWar(webAppType, activeWebServer, appName, contextName, contextPath, true);

        project.lock();
        project.setActiveWebServer(webScope.toString(), activeWebServer.getInstanceName());
        project.save();
        project.unlock();
    }

    /**
     * Method for uninstalling the web applications of a given module from a given project
     *
     * @param moduleName The name of the module whose web applications shall be uninstalled
     * @param projectId  The id of the project the web applications shall be uninstalled from
     * @param connection A {@link ServerConnection} to the server
     */
    public static void uninstallProjectWebApps(final String moduleName,
                                               final long projectId, final ServerConnection connection) {
        final ModuleManager mm = connection.getManager(ModuleManager.class);
        final ModuleDescriptor descriptor = mm.getModule(moduleName);
        final WebAppManager wm = connection.getManager(WebAppManager.class);

        if (descriptor != null) {
            final ComponentDescriptor[] componentDescriptors = descriptor.getComponents();

            for (final ComponentDescriptor componentDescriptor : componentDescriptors) {

                if (componentDescriptor != null && componentDescriptor.getType().equals(ComponentDescriptor.Type.WEBAPP)) {
                    for (WebEnvironment.WebScope scope : WebEnvironment.WebScope.values()) {
                        if (!scope.equals(WebEnvironment.WebScope.GLOBAL)) {
                            WebAppType webAppType = new WebAppType(projectId, scope);
                            wm.uninstallWebApp(moduleName, componentDescriptor.getName(), webAppType);
                        }
                    }
                }
            }
        }
    }

    /**
     * Method for uninstalling the project applications of a given module from a given project
     *
     * @param moduleName The name of the module whose project applications shall be uninstalled
     * @param projectId  The id of the project the project applications shall be uninstalled from
     * @param connection A {@link ServerConnection} to the server
     */
    public static void uninstallProjectApps(final String moduleName, final long projectId, final ServerConnection connection) {
        final ModuleManager mm = connection.getManager(ModuleManager.class);
        final ModuleDescriptor descriptor = mm.getModule(moduleName);
        final ProjectAppManager pm = connection.getManager(ProjectAppManager.class);
        if (descriptor != null) {
            final ComponentDescriptor[] componentDescriptors = descriptor.getComponents();
            for (final ComponentDescriptor componentDescriptor : componentDescriptors) {
                if (componentDescriptor != null) {
                    if (componentDescriptor.getType().equals(ComponentDescriptor.Type.PROJECTAPP)) {
                        pm.uninstallProjectApp(moduleName, componentDescriptor.getName(), projectId);
                    }
                }
            }
        }
    }

    /**
     * Method for uninstalling a module. Make sure to first uninstall the project and web applications belonging to the given module before calling
     * this method.
     *
     * @param moduleName The module to be uninstalled
     * @param connection A {@link ServerConnection} to the server
     */
    public static void uninstallModule(final String moduleName, final ServerConnection connection) {
        final ModuleManager mm = connection.getManager(ModuleManager.class);
        mm.uninstall(moduleName);
    }

    /**
     * Main method for installing and uninstalling a whole module.
     *
     * @param args See class description
     */
    public static void main(String[] args) {
        ModuleInstaller installer = new ModuleInstaller();
        ServerConnection connection = null;
        String fsm = "";
        long projectId = 0;
        installer.install(fsm, projectId, connection);
        String moduleName = "";
        installer.uninstall(projectId, connection, moduleName);
    }

    private static void uninstall(long projectId, ServerConnection connection, String moduleName) {
        if(connection == null || !connection.isConnected()) {
            throw new IllegalStateException("Connection is null or not connected!");
        }
        uninstallProjectWebApps(moduleName, projectId, connection);
        uninstallProjectApps(moduleName, projectId, connection);
        uninstallModule(moduleName, connection);
    }

    private boolean install(String fsm, long projectId, ServerConnection connection) {
        if(connection == null || !connection.isConnected()) {
            throw new IllegalStateException("Connection is null or not connected!");
        }

        Optional<ModuleResult> moduleResultOption = installModule(fsm, connection);
        moduleResultOption.ifPresent((result) -> {
            String moduleName = result.getDescriptor().getName();
            LOGGER.info("Finished module installation for " + moduleName);

            installProjectApps(moduleName, projectId, new HashMap<>(), connection);
            installProjectWebApps(moduleName, projectId, new HashMap<>(), new HashMap<>(), connection);

        });
        return true;
    }

    public static void install(ServerConnection connection, final String _modulename, final String projectname, final Map<String, String> _projectappconfigurations, final Map<String, String> _webappscopes, Map<String, String> _webappconfigurations, Map<String, String> _serviceconfigurations, final String _modulefile) throws Exception {
        installModule(_modulefile, connection);
        activateServices(_modulename, _serviceconfigurations, connection);

        final ProjectManager projectManager = connection.getManager(ProjectManager.class);
        long projectId = -1;
        if (projectname != null && !"".equals(projectname.trim())) {
            if (projectManager.getProjectByName(projectname) != null) {
                projectId = projectManager.getProjectByName(projectname)
                        .getID();

                installProjectApps(_modulename, projectId,
                        _projectappconfigurations, connection);
                installProjectWebApps(_modulename, projectId,
                        _webappscopes, _webappconfigurations, connection);
            } else {
                throw new IllegalStateException("There's no project named " + projectname + " on that server.");
            }
        } else {
            LOGGER.warn("No project specified. Module components won't be installed for any project!");
        }
    }
}
