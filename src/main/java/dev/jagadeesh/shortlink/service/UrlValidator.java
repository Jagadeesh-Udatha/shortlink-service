package dev.jagadeesh.shortlink.service;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/** Accepts only absolute http(s) URLs with a host, and refuses links that point back at this service. */
@Component
public class UrlValidator {

    private static final Set<String> SCHEMES = Set.of("http", "https");

    public URI validate(String raw, String ownBaseUrl) {
        URI uri;
        try {
            uri = new URI(raw.trim());
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("Not a valid URL: " + e.getReason());
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!SCHEMES.contains(scheme)) {
            throw new InvalidUrlException("Only http and https URLs can be shortened");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException("The URL must include a host");
        }
        String ownHost = URI.create(ownBaseUrl).getHost();
        if (uri.getHost().equalsIgnoreCase(ownHost)) {
            throw new InvalidUrlException("A short link cannot point to another short link");
        }
        return uri;
    }
}
