package org.orbitfs.common.exception;

/**
 * Base for all OrbitFS domain exceptions — unchecked.
 */
public class OrbitFSException extends RuntimeException {
    public OrbitFSException(String message) { super(message); }
    public OrbitFSException(String message, Throwable cause) { super(message, cause); }
}
