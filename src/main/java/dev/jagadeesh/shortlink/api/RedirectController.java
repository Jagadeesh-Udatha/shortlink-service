package dev.jagadeesh.shortlink.api;

import dev.jagadeesh.shortlink.domain.Link;
import dev.jagadeesh.shortlink.service.LinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "Redirect")
@RestController
public class RedirectController {

    private final LinkService service;

    public RedirectController(LinkService service) {
        this.service = service;
    }

    /**
     * 302 (not 301) plus no-store, so browsers come back through the service on every visit
     * and every click is counted.
     */
    @Operation(summary = "Follow a short link")
    @GetMapping("/{code:[A-Za-z0-9_-]{4,32}}")
    public ResponseEntity<Void> redirect(@PathVariable String code,
                                         @RequestHeader(value = HttpHeaders.REFERER, required = false) String referer) {
        Link link = service.resolve(code, hostOf(referer));
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(link.getLongUrl()))
                .cacheControl(CacheControl.noStore())
                .build();
    }

    static String hostOf(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            return URI.create(referer).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
