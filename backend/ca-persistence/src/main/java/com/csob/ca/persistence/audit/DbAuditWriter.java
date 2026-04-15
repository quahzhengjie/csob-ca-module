package com.csob.ca.persistence.audit;

import com.csob.ca.persistence.entity.AuditLogEntity;
import com.csob.ca.shared.dto.AuditEventDto;

import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only, hash-chained audit writer.
 *
 * Chain formula (deterministic):
 *   prev_hash = last-persisted event.hash for this packId (null for the
 *               first event; serialised as the 64-char ZERO string on write)
 *   hash      = lowercase-hex SHA-256( prev_hash || event.detailsJson )
 *
 * The string concatenation is done byte-for-byte in UTF-8. For the very
 * first event on a pack, prev_hash is null in the row and the SHA input
 * uses the 64-char ZERO string so the hash remains computed and verifiable.
 *
 * NOT thread-safe under concurrent writes to the same packId. For v1
 * (single-node, per-pack pipeline runs that call this serially inside
 * PersistStep) this is not an issue. A row-level lock or version column
 * would be added before we allow parallel audit writers.
 */
public class DbAuditWriter implements AuditWriter {
    // non-final: Spring CGLIB subclasses this type to proxy @Transactional methods.

    /** Serialised form of the "no previous hash" state. */
    public static final String ZERO_64 =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private final AuditLogJpaRepository auditLogRepository;

    public DbAuditWriter(AuditLogJpaRepository auditLogRepository) {
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository");
    }

    @Override
    @Transactional
    public void recordEvent(AuditEventDto event) {
        Objects.requireNonNull(event, "event");

        String prevHash = auditLogRepository
                .findTopByPackIdOrderByOccurredAtDesc(event.packId())
                .map(AuditLogEntity::getHash)
                .orElse(null);

        String hashInputPrev = (prevHash == null) ? ZERO_64 : prevHash;
        String hash = sha256Hex(hashInputPrev + safe(event.detailsJson()));

        AuditLogEntity row = new AuditLogEntity();
        row.setEventId(UUID.randomUUID().toString());
        row.setPackId(event.packId());
        row.setEventType(event.eventType());
        row.setEventJson(safe(event.detailsJson()));
        row.setPrevHash(prevHash);        // null for first event per pack
        row.setHash(hash);
        row.setOccurredAt(event.occurredAt());

        auditLogRepository.save(row);
    }

    // ---- helpers ----

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available on this JVM", e);
        }
    }
}
