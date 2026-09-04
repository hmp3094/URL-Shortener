package com.urlshortener.link;

import jakarta.persistence.EntityManager;
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

    @Transactional
    public ShortLink create(String longUrl) {
        String trimmed = longUrl.trim();
        long id = nextSequenceValue();
        String shortCode = ShortCodeEncoder.encode(id);
        ShortLink shortLink = new ShortLink(id, shortCode, trimmed, OffsetDateTime.now());
        return shortLinkRepository.save(shortLink);
    }

    private long nextSequenceValue() {
        Object result = entityManager.createNativeQuery("SELECT nextval('short_link_seq')").getSingleResult();
        return ((Number) result).longValue();
    }
}
