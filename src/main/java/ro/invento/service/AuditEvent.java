package ro.invento.service;

import java.time.Instant;
import java.util.UUID;

public record AuditEvent(Instant occurredAt, UUID actorId, String action, UUID entityId) { }
