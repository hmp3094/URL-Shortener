package com.urlshortener.link;

import com.urlshortener.link.dto.CreateLinkRequest;
import com.urlshortener.link.dto.ErrorResponse;
import com.urlshortener.link.dto.LinkResponse;
import com.urlshortener.validation.DestinationUrlValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
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

    @Operation(summary = "Create a short link for a long URL")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Short link created (or an existing matching one returned)",
                    content = @Content(schema = @Schema(implementation = LinkResponse.class))),
            @ApiResponse(responseCode = "400", description = "The submitted URL failed validation",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Too many creation requests from this caller",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/api/links")
    public ResponseEntity<LinkResponse> createShortLink(@Valid @RequestBody CreateLinkRequest request) {
        destinationUrlValidator.validate(request.url());

        ShortLink created = shortLinkService.create(request.url(), request.expiresInSeconds());

        String shortUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/{code}")
                .buildAndExpand(created.getShortCode())
                .toUriString();

        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create(shortUrl))
                .body(LinkResponse.from(created, shortUrl));
    }
}
