package com.urlshortener.link;

import com.urlshortener.link.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    // Constrained to 3-32 characters from the alias/code charset so this catch-all-looking route
    // can't shadow other root-level paths such as /swagger-ui.html (contains a dot) or /actuator
    // (blocked at alias-creation time by CustomAliasValidator's reserved-name list, so no row for
    // "actuator" can ever exist to resolve — the primary guard, not this regex; Spring's own
    // routing also ranks Actuator's literal mapping above this templated one as defense-in-depth).
    @Operation(summary = "Resolve a short code and redirect to its long URL")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Redirect to the long URL associated with this short code"),
            @ApiResponse(responseCode = "404", description = "No short link exists for this code",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{code:[a-zA-Z0-9_-]{3,32}}")
    public ResponseEntity<Void> redirectToLongUrl(
            @Parameter(description = "Short code or custom alias (case-insensitive)") @PathVariable String code) {
        ShortLink shortLink = shortLinkService.resolve(code);
        // Checked here, not inside resolve(): resolve() is @Cacheable and its body is skipped
        // entirely on a cache hit, so an expiry check placed inside it would silently stop firing
        // once a code is cached. expiresAt itself never changes after creation, so comparing the
        // (possibly cached) entity's expiresAt against "now" here is always correct regardless of
        // when it was cached.
        if (shortLink.isExpired()) {
            throw new ShortLinkNotFoundException(code);
        }
        shortLinkService.recordClick(code);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, shortLink.getLongUrl())
                .build();
    }
}
