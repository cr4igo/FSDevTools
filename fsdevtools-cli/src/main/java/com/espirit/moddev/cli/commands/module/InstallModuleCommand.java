package com.espirit.moddev.cli.commands.module;

import com.espirit.moddev.cli.ConnectionBuilder;
import com.espirit.moddev.cli.commands.SimpleCommand;
import com.espirit.moddev.cli.results.SimpleResult;
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

@Command(name = "install", groupNames = {"module"}, description = "Installs a FirstSpirit module into a FirstSpirit Server.")
public class InstallModuleCommand extends SimpleCommand<SimpleResult<Boolean>> {

    @Option(type = OptionType.COMMAND, name = {"-fsm", "--fsm"}, description = "Path to the module fsm file file that should be installed")
    @Required
    private String fsm;

    @Option(type = OptionType.COMMAND, name = {"-mpn", "--moduleProjectName"}, description = "Name of the FirstSpirit target project where the applications should be installed to")
    @Required
    private String projectName;

    @Override
    public SimpleResult<Boolean> call() {
        try(Connection connection = create()) {
            connection.connect();
            if(connection instanceof ServerConnection) {
                ServerConnection serverConnection = (ServerConnection) connection;
                boolean installed = new ModuleInstaller().install(fsm, serverConnection, projectName);
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
