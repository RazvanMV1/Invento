package ro.invento.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class InMemoryAuditService implements AuditService {
    private final Clock clock;
    private final List<AuditEvent> events = new ArrayList<>();

    public InMemoryAuditService(Clock clock) { this.clock = clock; }

    public synchronized void record(UUID actorId, String action, UUID entityId) {
        events.add(new AuditEvent(clock.instant(), actorId, action, entityId));
    }

    public synchronized List<AuditEvent> events() { return List.copyOf(events); }
}
