package com.urlshortener.link;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {

    Optional<ShortLink> findByShortCode(String shortCode);

    Optional<ShortLink> findByLongUrl(String longUrl);

    /**
     * Atomically inserts a new row unless {@code longUrl} already has one, without needing any
     * application-level locking. Returns empty when a row for this {@code longUrl} already
     * existed — the caller should then re-select it via {@link #findByLongUrl(String)}.
     */
    @Query(value = "INSERT INTO short_links (id, short_code, long_url, created_at) "
            + "VALUES (:id, :shortCode, :longUrl, :createdAt) "
            + "ON CONFLICT (long_url) DO NOTHING RETURNING *",
            nativeQuery = true)
    Optional<ShortLink> insertIfLongUrlAbsent(
            @Param("id") Long id,
            @Param("shortCode") String shortCode,
            @Param("longUrl") String longUrl,
            @Param("createdAt") OffsetDateTime createdAt);

    /**
     * Atomically increments the click counter and stamps the access time in a single row-level
     * update, so concurrent redirects for the same code can't race each other into an
     * under-count. Runs on every redirect (see {@code ShortLinkService.recordClick}), independent
     * of the cache-aside lookup used to resolve the destination URL — an accepted trade-off of
     * exact counts over keeping every redirect off the database.
     */
    @Modifying
    @Query(value = "UPDATE short_links SET click_count = click_count + 1, last_accessed_at = now() "
            + "WHERE short_code = :shortCode",
            nativeQuery = true)
    void recordClick(@Param("shortCode") String shortCode);
}
