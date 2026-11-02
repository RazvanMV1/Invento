package ro.invento;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class AuditServiceTest extends DomainTestSupport {
    @Test
    void recordsWhoDidWhatToWhichEntityAndWhen() {
        UUID entityId = UUID.randomUUID();
        audit.record(managerA.id(), "DELIVERY_ACCEPTED", entityId);
        var event = audit.events().getFirst();
        assertThat(event.actorId()).isEqualTo(managerA.id());
        assertThat(event.action()).isEqualTo("DELIVERY_ACCEPTED");
        assertThat(event.entityId()).isEqualTo(entityId);
        assertThat(event.occurredAt()).isEqualTo(clock.instant());
    }

    @Test
    void returnedHistoryCannotBeChangedAndIsASnapshot() {
        audit.record(administrator.id(), "FIRST", storeA.id());
        var snapshot = audit.events();
        audit.record(administrator.id(), "SECOND", storeB.id());
        assertThat(snapshot).hasSize(1);
        assertThat(audit.events()).hasSize(2);
        assertThatThrownBy(snapshot::clear).isInstanceOf(UnsupportedOperationException.class);
    }
}
