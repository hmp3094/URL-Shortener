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
     * Creates a short link for {@code longUrl}, or returns the existing one if this exact URL
     * (after whitespace trimming) already has a short link — atomically, even under concurrent
     * requests, via an {@code INSERT ... ON CONFLICT (long_url) DO NOTHING}. The sequence value
     * consumed for a "losing" insert is simply never used again, which is expected/harmless
     * (sequences are allowed to have gaps).
     */
    @Transactional
    public ShortLink create(String longUrl) {
        String trimmed = longUrl.trim();
        long id = nextSequenceValue();
        String shortCode = ShortCodeEncoder.encode(id);

        return shortLinkRepository
                .insertIfLongUrlAbsent(id, shortCode, trimmed, OffsetDateTime.now())
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

    private long nextSequenceValue() {
        Object result = entityManager.createNativeQuery("SELECT nextval('short_link_seq')").getSingleResult();
        return ((Number) result).longValue();
    }
}
