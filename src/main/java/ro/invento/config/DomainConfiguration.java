package ro.invento.config;

import jakarta.validation.Validator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ro.invento.factory.*;
import ro.invento.service.*;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class DomainConfiguration {
    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean
    ValidationService validationService(Validator validator) { return new BeanValidationService(validator); }

    @Bean
    ComputationService computationService(ValidationService validation) { return new DefaultComputationService(validation); }

    @Bean
    DeliveryFactory deliveryFactory(ValidationService validation, Clock clock) {
        return new DefaultDeliveryFactory(validation, clock);
    }

    @Bean
    SaleFactory saleFactory(ValidationService validation, Clock clock) {
        return new DefaultSaleFactory(validation, clock);
    }

    @Bean
    AuditService auditService(Clock clock) { return new InMemoryAuditService(clock); }

    @Bean
    WorkflowService workflowService(ValidationService validation, ComputationService computation,
            DeliveryFactory deliveryFactory, SaleFactory saleFactory, AuditService audit, Clock clock) {
        return new InMemoryWorkflowService(validation, computation, deliveryFactory, saleFactory, audit, clock);
    }
}
