package ro.invento;

import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ro.invento.service.WorkflowService;
import ro.invento.service.ComputationService;
import ro.invento.factory.DeliveryFactory;
import ro.invento.factory.SaleFactory;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class InventoApplicationTest {

    @Autowired
    private Validator validator;

    @Autowired
    private WorkflowService workflow;

    @Autowired
    private ComputationService computation;

    @Autowired
    private DeliveryFactory deliveryFactory;

    @Autowired
    private SaleFactory saleFactory;

    @Test
    void applicationStartsWithBeanValidationAvailable() {
        assertThat(validator).isNotNull();
        assertThat(workflow).isNotNull();
        assertThat(computation).isNotNull();
        assertThat(deliveryFactory).isNotNull();
        assertThat(saleFactory).isNotNull();
    }
}
