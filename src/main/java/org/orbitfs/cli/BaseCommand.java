package org.orbitfs.cli;

import picocli.CommandLine;

import java.util.concurrent.Callable;

/**
 * Base class for subcommands that need access to the parent OrbitCli's connection parameters.
 * Each subcommand gets a reference to the parent via Picocli's @ParentCommand.
 */
public abstract class BaseCommand implements Callable<Integer> {

    @CommandLine.ParentCommand
    protected OrbitCli parent;
}
