package com.espirit.moddev.cli.commands.module;

import com.espirit.moddev.cli.ConnectionBuilder;
import com.espirit.moddev.cli.commands.SimpleCommand;
import com.espirit.moddev.cli.results.SimpleResult;
import com.espirit.moddev.core.StringPropertiesMap;
import com.espirit.moddev.moduleinstaller.ModuleInstallationParameters;
import com.espirit.moddev.moduleinstaller.ModuleInstaller;
import com.espirit.moddev.moduleuninstaller.ModuleUninstaller;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.OptionType;
import com.github.rvesse.airline.annotations.restrictions.Required;
import de.espirit.firstspirit.access.Connection;
import de.espirit.firstspirit.common.MaximumNumberOfSessionsExceededException;
import de.espirit.firstspirit.io.ServerConnection;
import de.espirit.firstspirit.server.authentication.AuthenticationException;

import java.io.IOException;

@Command(name = "uninstall", groupNames = {"module"}, description = "Uninstalls a FirstSpirit module from a FirstSpirit Server.")
public class UninstallModuleCommand extends SimpleCommand<SimpleResult<Boolean>> {

    @Option(type = OptionType.COMMAND, name = {"-m", "--moduleName"}, description = "Name of the module that should be deleted")
    @Required
    private String moduleName;

    @Option(type = OptionType.COMMAND, name = {"-pn", "--projectName"}, description = "Name of the project, where module components should be deleted")
    @Required
    private String projectName;

    @Override
    public SimpleResult<Boolean> call() {
        try(Connection connection = create()) {
            connection.connect();
            if(connection instanceof ServerConnection) {
                ServerConnection serverConnection = (ServerConnection) connection;
                new ModuleUninstaller().uninstall(serverConnection, serverConnection.getProjectByName(projectName).getId(), moduleName);
                return new SimpleResult<>(true);
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
