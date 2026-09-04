package com.urlshortener.link;

import com.urlshortener.link.dto.CreateLinkRequest;
import com.urlshortener.link.dto.LinkResponse;
import com.urlshortener.validation.DestinationUrlValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
public class LinkController {

    private final DestinationUrlValidator destinationUrlValidator;
    private final ShortLinkService shortLinkService;

    public LinkController(DestinationUrlValidator destinationUrlValidator, ShortLinkService shortLinkService) {
        this.destinationUrlValidator = destinationUrlValidator;
        this.shortLinkService = shortLinkService;
    }

    @PostMapping("/api/links")
    public ResponseEntity<LinkResponse> createShortLink(@RequestBody CreateLinkRequest request) {
        destinationUrlValidator.validate(request.url());

        ShortLink created = shortLinkService.create(request.url());

        String shortUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/{code}")
                .buildAndExpand(created.getShortCode())
                .toUriString();

        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create(shortUrl))
                .body(LinkResponse.from(created, shortUrl));
    }
}
