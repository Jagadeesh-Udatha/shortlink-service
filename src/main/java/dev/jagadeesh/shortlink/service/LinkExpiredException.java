package dev.jagadeesh.shortlink.service;

public class LinkExpiredException extends RuntimeException {
    public LinkExpiredException(String code) {
        super("Short link '" + code + "' has expired");
    }
}
