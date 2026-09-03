package com.urlshortener.link;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShortLinkRepository extends JpaRepository<ShortLink, Long> {

    Optional<ShortLink> findByShortCode(String shortCode);

    Optional<ShortLink> findByLongUrl(String longUrl);
}
