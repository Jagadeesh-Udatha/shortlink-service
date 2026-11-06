package dev.jagadeesh.shortlink.api;

import dev.jagadeesh.shortlink.config.ShortlinkProperties;
import dev.jagadeesh.shortlink.domain.Link;
import dev.jagadeesh.shortlink.service.LinkService;
import dev.jagadeesh.shortlink.service.LinkStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@Tag(name = "Links", description = "Create, inspect and delete short links")
@Validated
@RestController
@RequestMapping("/api/links")
public class LinkController {

    private final LinkService service;
    private final ShortlinkProperties props;

    public LinkController(LinkService service, ShortlinkProperties props) {
        this.service = service;
        this.props = props;
    }

    @Operation(summary = "Shorten a URL, optionally with a custom alias and lifetime")
    @PostMapping
    public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest req) {
        Link link = service.create(req.url(), req.alias(), req.ttlDays());
        return ResponseEntity.created(URI.create("/api/links/" + link.getCode()))
                .body(LinkResponse.of(link, props.baseUrl()));
    }

    @Operation(summary = "Get one link")
    @GetMapping("/{code}")
    public LinkResponse get(@PathVariable String code) {
        return LinkResponse.of(service.get(code), props.baseUrl());
    }

    @Operation(summary = "Clicks per day and top referrers for the last N days")
    @GetMapping("/{code}/stats")
    public LinkStats stats(@PathVariable String code,
                           @RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return service.stats(code, days);
    }

    @Operation(summary = "Most clicked links")
    @GetMapping("/top")
    public List<LinkResponse> top(@RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {
        return service.top(limit).stream().map(l -> LinkResponse.of(l, props.baseUrl())).toList();
    }

    @Operation(summary = "Delete a link and its click history")
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        service.delete(code);
        return ResponseEntity.noContent().build();
    }
}
