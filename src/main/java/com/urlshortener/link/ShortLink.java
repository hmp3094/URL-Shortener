package com.urlshortener.link;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Maps to the {@code short_links} table. Immutable once created — no setters beyond what JPA
 * needs for hydration, since a short link's mapping is never updated after creation.
 */
@Entity
@Table(name = "short_links")
public class ShortLink {

    @Id
    private Long id;

    @Column(name = "short_code", nullable = false, unique = true, length = 6)
    private String shortCode;

    @Column(name = "long_url", nullable = false, unique = true)
    private String longUrl;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ShortLink() {
        // required by JPA
    }

    public ShortLink(Long id, String shortCode, String longUrl, OffsetDateTime createdAt) {
        this.id = id;
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
