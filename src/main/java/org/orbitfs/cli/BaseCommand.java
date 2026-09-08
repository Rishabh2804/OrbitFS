package org.orbitfs.cli;

import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

/**
 * Base class for subcommands that need access to the parent OrbitCli's connection parameters.
 */
public abstract class BaseCommand implements Callable<Integer> {

    @ParentCommand
    protected OrbitCli parent;
}
