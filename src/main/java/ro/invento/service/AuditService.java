package ro.invento.service;

import java.util.List;
import java.util.UUID;

public interface AuditService {
    void record(UUID actorId, String action, UUID entityId);
    List<AuditEvent> events();
}
