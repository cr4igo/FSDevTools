package com.espirit.moddev.cli.commands.module;

import com.espirit.moddev.cli.ConnectionBuilder;
import com.espirit.moddev.cli.commands.SimpleCommand;
import com.espirit.moddev.cli.results.SimpleResult;
import com.espirit.moddev.core.StringPropertiesMap;
import com.espirit.moddev.moduleinstaller.ModuleInstallationParameters;
import com.espirit.moddev.moduleinstaller.ModuleInstaller;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.OptionType;
import com.github.rvesse.airline.annotations.help.Examples;
import com.github.rvesse.airline.annotations.restrictions.Required;
import de.espirit.firstspirit.access.Connection;
import de.espirit.firstspirit.common.MaximumNumberOfSessionsExceededException;
import de.espirit.firstspirit.io.ServerConnection;
import de.espirit.firstspirit.module.WebEnvironment.WebScope;
import de.espirit.firstspirit.server.authentication.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * Installs a module on a FirstSpirit server. Provides mechanisms to configure project apps, webapps
 * and corresponding scopes.
 */
@Command(name = "install", groupNames = {"module"}, description = "Installs a FirstSpirit module into a FirstSpirit Server.")
@Examples(examples = "module install -mpn \"Mithras Energy\" -fsm \"solder\\videomanagementpro.fsm\" -pacf \"resources\\projectApp.ini\" " +
        "-scf \"MyServiceName=resources\\serviceConfig.ini\" -wacf \"preview=resources\\previewAppConfig.ini\"",
        descriptions = "module install -mpn \"module.fsm\" -pac \"user:myUserName,password:secret,alwaysLogin:true\"")
public class InstallModuleCommand extends SimpleCommand<SimpleResult<Boolean>> {

    protected static final Logger LOGGER = LoggerFactory.getLogger(InstallModuleCommand.class);

    @Option(type = OptionType.COMMAND, name = {"-fsm", "--fsm"}, description = "Path to the module fsm file file that should be installed")
    @Required
    private String fsm;

    @Option(type = OptionType.COMMAND, name = {"-mpn", "--moduleProjectName"}, description = "Name of the FirstSpirit target project where the applications should be installed to")
    @Required
    private String projectName;

    @Option(type = OptionType.COMMAND, name = {"-scf", "--serviceConfigurationFiles"}, description = "Define a map-like configuration for services of the given module - comma-separated value paris with service name and configuration path file.")
    private String serviceConfigurations;
    @Option(type = OptionType.COMMAND, name = {"-pacf", "--projectAppConfigurationFile"}, description = "Configuration file path for project app")
    private String projectAppConfiguration;
    @Option(type = OptionType.COMMAND, name = {"-was", "--webAppScopes"}, description = "Define a map-like configuration for webapp scopes of the given module - comma-separated values from the FirstSpirit WebScope enum.")
    private String webAppScopes;
    @Option(type = OptionType.COMMAND, name = {"-wacf", "--webAppConfigurationFiles"}, description = "Define a map-like configuration for the webapps of the given module - with comma-separated key-values.")
    private String webAppConfigurations;

    @Override
    public SimpleResult<Boolean> call() {
        try(Connection connection = create()) {
            connection.connect();
            if(connection instanceof ServerConnection) {
                ServerConnection serverConnection = (ServerConnection) connection;

                File projectAppConfigurationFile = getOptionalProjectAppConfigurationFile();
                List<WebScope> splittedWebAppScopes = extractWebScopes();
                Map<WebScope, File> webappConfigurationFilesForWebScopes = getWebScopeFileMap();
                Map<String, File> configurationFileForServiceName = getStringFilesMap(serviceConfigurations);

                final ModuleInstallationParameters parameters = new ModuleInstallationParameters(projectName, new File(fsm), configurationFileForServiceName, projectAppConfigurationFile, splittedWebAppScopes, webappConfigurationFilesForWebScopes);
                boolean installed = new ModuleInstaller().install(serverConnection, parameters);
                return new SimpleResult<>(installed);
            }
        } catch (IOException | AuthenticationException | MaximumNumberOfSessionsExceededException | IllegalArgumentException e) {
            return new SimpleResult<>(e);
        }
        return new SimpleResult<>(new IllegalStateException("Provided connection is not a server connection!"));
    }

    private Map<WebScope, File> getWebScopeFileMap() {
        return getStringFilesMap(webAppConfigurations).entrySet().stream().collect(Collectors.toMap(entry -> WebScope.valueOf(entry.getKey().trim().toUpperCase()), entry -> {
            if(!entry.getValue().isFile() || !entry.getValue().exists()) {
                throw new IllegalArgumentException("File for webapp configuration with scope " + entry.getKey() + " doesn't exist or is not a file.");
            }
            return entry.getValue();
        }));
    }

    private File getOptionalProjectAppConfigurationFile() {
        File projectAppConfigurationFile = null;
        if(projectAppConfiguration != null) {
            projectAppConfigurationFile = new File(projectAppConfiguration);
            if(!projectAppConfigurationFile.isFile() || !projectAppConfigurationFile.exists()) {
                throw new IllegalArgumentException("Project app configuration file doesn't exist or is not a file!");
            }
        }
        return projectAppConfigurationFile;
    }

    private Map<String, File> getStringFilesMap(String webAppConfigurations) {
        if(webAppConfigurations == null) {
            return new HashMap<>();
        }
        Map<String, File> result = Arrays.stream(webAppConfigurations.split(",")).map(propertyString -> propertyString.split("=")).collect(Collectors.toMap(entry -> entry[0], entry -> {
            File file = new File(entry[1]);
            if(!file.exists() || !file.isFile()) {
                throw new IllegalArgumentException("File doesn't exist for key " + entry[0]);
            }
            return file;
        }));
        return result;
    }

    private List<WebScope> extractWebScopes() {
        List<WebScope> splittedWebAppScopes;
        try {
            splittedWebAppScopes= this.webAppScopes != null ? Arrays.asList( this.webAppScopes.split(",")).stream().map(scope -> scope.trim().toUpperCase()).map(WebScope::valueOf).collect(toList()) : new ArrayList<>();
        } catch (IllegalArgumentException e) {
            LOGGER.error("You passed an illegal argument as webapp scope", e);
            splittedWebAppScopes = new ArrayList<>();
        }
        return splittedWebAppScopes;
    }


    protected Connection create() {
        return ConnectionBuilder.with(this).build();
    }

    @Override
    public boolean needsContext() {
        return false;
    }

    public String getFsm() {
        return fsm;
    }

    public void setFsm(String fsm) {
        this.fsm = fsm;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getServiceConfigurations() {
        return serviceConfigurations;
    }

    public void setServiceConfigurations(String serviceConfigurations) {
        this.serviceConfigurations = serviceConfigurations;
    }

    public String getProjectAppConfiguration() {
        return projectAppConfiguration;
    }

    public void setProjectAppConfiguration(String projectAppConfiguration) {
        this.projectAppConfiguration = projectAppConfiguration;
    }

    public String getWebAppScopes() {
        return webAppScopes;
    }

    public void setWebAppScopes(String webAppScopes) {
        this.webAppScopes = webAppScopes;
    }

    public String getWebAppConfigurations() {
        return webAppConfigurations;
    }

    public void setWebAppConfigurations(String webAppConfigurations) {
        this.webAppConfigurations = webAppConfigurations;
    }
}
