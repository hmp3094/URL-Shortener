package com.urlshortener.link;

import com.urlshortener.config.CacheConfig;
import jakarta.persistence.EntityManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class ShortLinkService {

    private final ShortLinkRepository shortLinkRepository;
    private final EntityManager entityManager;

    public ShortLinkService(ShortLinkRepository shortLinkRepository, EntityManager entityManager) {
        this.shortLinkRepository = shortLinkRepository;
        this.entityManager = entityManager;
    }

    /**
     * Creates a short link for {@code longUrl} with no expiration. See
     * {@link #create(String, Long)}.
     */
    public ShortLink create(String longUrl) {
        return create(longUrl, null);
    }

    /**
     * Creates a short link for {@code longUrl}, or returns the existing live one if this exact
     * URL (after whitespace trimming) already has a non-expired short link. If the existing short
     * link for this URL has expired, {@link ShortLinkRepository#deleteIfExpired} retires it first
     * (same transaction) so a brand-new short code can be issued instead of reactivating the old
     * one — the old code is retired for good, which is the whole point of it having expired (see
     * {@code docs/scenarios/ambiguous-link-expiration.md}). The insert itself is still atomic and
     * lock-free under concurrent requests via {@link ShortLinkRepository#insertIfLongUrlAbsent}.
     *
     * @param expiresInSeconds how long the new short link should remain resolvable, or
     *                         {@code null} for no expiration (the default — expiry is opt-in)
     */
    @Transactional
    public ShortLink create(String longUrl, Long expiresInSeconds) {
        String trimmed = longUrl.trim();
        shortLinkRepository.deleteIfExpired(trimmed);

        long id = nextSequenceValue();
        String shortCode = ShortCodeEncoder.encode(id);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = expiresInSeconds == null ? null : now.plusSeconds(expiresInSeconds);

        return shortLinkRepository
                .insertIfLongUrlAbsent(id, shortCode, trimmed, now, expiresAt)
                .orElseGet(() -> shortLinkRepository.findByLongUrl(trimmed)
                        .orElseThrow(() -> new IllegalStateException(
                                "Insert conflicted on long_url but no existing row was found: " + trimmed)));
    }

    /**
     * Resolves a short code to its {@link ShortLink}, cache-aside: on a cache miss, falls
     * through to Postgres and populates the cache for next time. Matching is case-insensitive —
     * both the cache key and the lookup are lowercased.
     */
    @Cacheable(cacheNames = CacheConfig.SHORT_LINKS_CACHE, key = "#code.toLowerCase()")
    public ShortLink resolve(String code) {
        String normalized = code.toLowerCase();
        return shortLinkRepository.findByShortCode(normalized)
                .orElseThrow(() -> new ShortLinkNotFoundException(code));
    }

    /**
     * Records a click against a short code. Deliberately not cached and not folded into
     * {@link #resolve(String)} — that method is skipped entirely on a cache hit, so a counter
     * update placed inside it would silently stop firing once a code warms up. Called once per
     * redirect regardless of whether the destination lookup was served from cache.
     */
    @Transactional
    public void recordClick(String code) {
        shortLinkRepository.recordClick(code.toLowerCase());
    }

    /**
     * Fetches a short link's current stats directly from Postgres, bypassing the redirect cache
     * so the click count and last-accessed time are always current, never a stale cached snapshot
     * from whenever the link was first resolved.
     */
    public ShortLink getStatsSnapshot(String code) {
        String normalized = code.toLowerCase();
        return shortLinkRepository.findByShortCode(normalized)
                .orElseThrow(() -> new ShortLinkNotFoundException(code));
    }

    private long nextSequenceValue() {
        Object result = entityManager.createNativeQuery("SELECT nextval('short_link_seq')").getSingleResult();
        return ((Number) result).longValue();
    }
}
