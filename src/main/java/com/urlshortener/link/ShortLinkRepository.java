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
     * Deletes {@code longUrl}'s existing row if (and only if) it has expired — a no-op otherwise.
     * Deliberately a separate statement from {@link #insertIfLongUrlAbsent}, not combined into one
     * {@code WITH ... DELETE ... INSERT} statement: Postgres runs every data-modifying clause of a
     * single statement's WITH block against the *same* snapshot, so an INSERT in the same
     * statement as this DELETE would not see the DELETE's own effect and would still conflict on
     * {@code long_url} — this was tried, and failed exactly that way against a real database (not
     * a mocked one), which is why it's two statements now. Two separate statements in the same
     * {@code @Transactional} method each get a fresh read of the latest committed data (Postgres's
     * default READ COMMITTED isolation), so the INSERT that follows this call correctly sees that
     * the row is gone.
     */
    @Modifying
    @Query(value = "DELETE FROM short_links "
            + "WHERE long_url = :longUrl AND expires_at IS NOT NULL AND expires_at <= now()",
            nativeQuery = true)
    void deleteIfExpired(@Param("longUrl") String longUrl);

    /**
     * Atomically inserts a new row unless {@code longUrl} already has one, without needing any
     * application-level locking. Returns empty when a row for this {@code longUrl} already
     * existed — the caller should then re-select it via {@link #findByLongUrl(String)}. Callers
     * that support expiry call {@link #deleteIfExpired(String)} first (same transaction) so an
     * expired row doesn't block a fresh one from being created for the same URL.
     */
    @Query(value = "INSERT INTO short_links (id, short_code, long_url, created_at, expires_at) "
            + "VALUES (:id, :shortCode, :longUrl, :createdAt, :expiresAt) "
            + "ON CONFLICT (long_url) DO NOTHING RETURNING *",
            nativeQuery = true)
    Optional<ShortLink> insertIfLongUrlAbsent(
            @Param("id") Long id,
            @Param("shortCode") String shortCode,
            @Param("longUrl") String longUrl,
            @Param("createdAt") OffsetDateTime createdAt,
            @Param("expiresAt") OffsetDateTime expiresAt);

    /**
     * Atomically inserts a new row for a caller-chosen alias unless {@code shortCode} is already
     * taken, without needing any application-level locking. Returns empty when the alias was
     * taken by a concurrent request. Deliberately conflict-targets {@code short_code} rather than
     * {@code long_url}: unlike {@link #insertIfLongUrlAbsent}, a long_url conflict here must NOT
     * be silently swallowed — the caller explicitly asked for a specific alias, so if the URL
     * already has a live link under a different code, that conflict is left to raise a genuine
     * {@code DataIntegrityViolationException} (on the {@code uq_short_links_long_url} constraint)
     * for the service layer to translate into a distinct, honest rejection instead of silently
     * discarding the requested alias.
     */
    @Query(value = "INSERT INTO short_links (id, short_code, long_url, created_at, expires_at) "
            + "VALUES (:id, :shortCode, :longUrl, :createdAt, :expiresAt) "
            + "ON CONFLICT (short_code) DO NOTHING RETURNING *",
            nativeQuery = true)
    Optional<ShortLink> insertWithAlias(
            @Param("id") Long id,
            @Param("shortCode") String shortCode,
            @Param("longUrl") String longUrl,
            @Param("createdAt") OffsetDateTime createdAt,
            @Param("expiresAt") OffsetDateTime expiresAt);

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
