package dev.jagadeesh.shortlink.service;

public class LinkNotFoundException extends RuntimeException {
    public LinkNotFoundException(String code) {
        super("No short link with code '" + code + "'");
    }
}
