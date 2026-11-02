package ro.invento.service;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Objects;

public final class BeanValidationService implements ValidationService {
    private final Validator validator;

    public BeanValidationService(Validator validator) {
        this.validator = validator;
    }

    @Override
    public <T> T validate(T object) {
        Objects.requireNonNull(object, "Object to validate cannot be null");
        var violations = validator.validate(object);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
        return object;
    }
}
