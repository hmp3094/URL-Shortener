package com.urlshortener.link;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {

    private final ShortLinkService shortLinkService;

    public RedirectController(ShortLinkService shortLinkService) {
        this.shortLinkService = shortLinkService;
    }

    // Constrained to exactly 6 alphanumeric characters so this catch-all-looking route can't
    // shadow other root-level paths such as /swagger-ui.html or /actuator.
    @GetMapping("/{code:[a-zA-Z0-9]{6}}")
    public ResponseEntity<Void> redirectToLongUrl(@PathVariable String code) {
        ShortLink shortLink = shortLinkService.resolve(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, shortLink.getLongUrl())
                .build();
    }
}
