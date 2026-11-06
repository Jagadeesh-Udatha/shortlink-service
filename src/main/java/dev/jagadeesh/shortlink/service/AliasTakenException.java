package dev.jagadeesh.shortlink.service;

public class AliasTakenException extends RuntimeException {
    public AliasTakenException(String alias) {
        super("The alias '" + alias + "' is already in use");
    }
}
