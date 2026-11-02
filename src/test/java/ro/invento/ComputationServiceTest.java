package ro.invento;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ro.invento.domain.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ComputationServiceTest extends DomainTestSupport {
    @Test
    void calculatesLineTotalWithoutFloatingPointLoss() {
        assertThat(computation.lineTotal(new SaleLine(coffee, 3, Money.of("0.10"))).amount())
                .isEqualByComparingTo("0.30");
    }

    @Test
    void calculatesTotalFromAllLinesAndTheirAppliedPrices() {
        Sale sale = saleFactory.create(cashierA, storeA,
                List.of(new SaleLine(coffee, 2, Money.of("20.00")), new SaleLine(tea, 3, tea.price())));
        assertThat(computation.saleTotal(sale).amount()).isEqualByComparingTo("94.00");
    }

    @ParameterizedTest
    @CsvSource({"0, 0, true", "2, 3, true", "3, 3, true", "4, 3, false"})
    void replenishmentIncludesThresholdBoundary(int quantity, int minimum, boolean included) {
        var stock = new StoreStock(storeA, coffee, quantity, minimum);
        assertThat(computation.replenishmentView(List.of(stock)).contains(stock)).isEqualTo(included);
    }

    @Test
    void rejectsInvalidLineBeforeComputing() {
        assertThatThrownBy(() -> computation.lineTotal(new SaleLine(coffee, -2, coffee.price())))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void replenishmentViewIsImmutableAndDoesNotModifyInput() {
        var stock = new StoreStock(storeA, coffee, 0, 1);
        var input = new java.util.ArrayList<>(List.of(stock));
        var result = computation.replenishmentView(input);
        assertThatThrownBy(result::clear).isInstanceOf(UnsupportedOperationException.class);
        assertThat(input).containsExactly(stock);
    }
}
