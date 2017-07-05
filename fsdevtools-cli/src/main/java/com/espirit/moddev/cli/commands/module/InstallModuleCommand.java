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
import com.github.rvesse.airline.annotations.restrictions.Required;
import de.espirit.firstspirit.access.Connection;
import de.espirit.firstspirit.common.MaximumNumberOfSessionsExceededException;
import de.espirit.firstspirit.io.ServerConnection;
import de.espirit.firstspirit.server.authentication.AuthenticationException;

import java.io.IOException;
import java.util.HashMap;

@Command(name = "install", groupNames = {"module"}, description = "Installs a FirstSpirit module into a FirstSpirit Server.")
public class InstallModuleCommand extends SimpleCommand<SimpleResult<Boolean>> {

    @Option(type = OptionType.COMMAND, name = {"-fsm", "--fsm"}, description = "Path to the module fsm file file that should be installed")
    @Required
    private String fsm;

    @Option(type = OptionType.COMMAND, name = {"-mpn", "--moduleProjectName"}, description = "Name of the FirstSpirit target project where the applications should be installed to")
    @Required
    private String projectName;

    @Option(type = OptionType.COMMAND, name = {"-sc", "--serviceConfigurations"}, description = "Define a map-like configuration for services of the given module - with comma-separated key-value pairs by : or =; .")
    private String serviceConfigurations;
    @Option(type = OptionType.COMMAND, name = {"-pac", "--projectAppConfigurations"}, description = "Define a map-like configuration for services of the given module - with comma-separated key-value pairs by : or =; .")
    private String projectAppConfigurations;
    @Option(type = OptionType.COMMAND, name = {"-was", "--webAppScopes"}, description = "Define a map-like configuration for webapp scopes of the given module - with comma-separated key-value pairs by : or =; .")
    private String webAppScopes;
    @Option(type = OptionType.COMMAND, name = {"-wac", "--webAppConfigurations"}, description = "Define a map-like configuration for the webapps of the given module - with comma-separated key-value pairs by : or =; .")
    private String webAppConfigurations;

    @Override
    public SimpleResult<Boolean> call() {
        try(Connection connection = create()) {
            connection.connect();
            if(connection instanceof ServerConnection) {
                ServerConnection serverConnection = (ServerConnection) connection;
                final ModuleInstallationParameters parameters = new ModuleInstallationParameters(projectName, fsm, new StringPropertiesMap(serviceConfigurations), new StringPropertiesMap(projectAppConfigurations), new StringPropertiesMap(webAppScopes), new StringPropertiesMap(webAppConfigurations));
                boolean installed = new ModuleInstaller().install(serverConnection, parameters);
                return new SimpleResult<>(installed);
            }
        } catch (IOException | AuthenticationException | MaximumNumberOfSessionsExceededException e) {
            return new SimpleResult<>(e);
        }
        return new SimpleResult<>(new IllegalStateException("Provided connection is not a server connection!"));
    }


    protected Connection create() {
        return ConnectionBuilder.with(this).build();
    }

    @Override
    public boolean needsContext() {
        return false;
    }
}
