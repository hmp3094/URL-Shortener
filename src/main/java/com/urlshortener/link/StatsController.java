package com.urlshortener.link;

import com.urlshortener.link.dto.ErrorResponse;
import com.urlshortener.link.dto.LinkStatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatsController {

    private final ShortLinkService shortLinkService;

    public StatsController(ShortLinkService shortLinkService) {
        this.shortLinkService = shortLinkService;
    }

    @Operation(summary = "Get click statistics for a short link")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current click count and access time for this short code",
                    content = @Content(schema = @Schema(implementation = LinkStatsResponse.class))),
            @ApiResponse(responseCode = "404", description = "No short link exists for this code",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/api/links/{code}/stats")
    public ResponseEntity<LinkStatsResponse> getStats(
            @Parameter(description = "6-character short code (case-insensitive)") @PathVariable String code) {
        ShortLink shortLink = shortLinkService.getStatsSnapshot(code);
        return ResponseEntity.ok(LinkStatsResponse.from(shortLink));
    }
}
