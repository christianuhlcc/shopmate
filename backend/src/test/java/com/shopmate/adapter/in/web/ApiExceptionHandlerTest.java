package com.shopmate.adapter.in.web;

import com.shopmate.domain.model.AccessForbiddenException;
import com.shopmate.domain.model.InvalidItemException;
import com.shopmate.domain.model.ListCapacityExceededException;
import com.shopmate.domain.model.ListNotFoundException;
import com.shopmate.domain.model.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void listNotFoundMapsTo404() {
        var response = handler.handleListNotFound(new ListNotFoundException(UUID.randomUUID()));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().getCode()).isEqualTo("LIST_NOT_FOUND");
    }

    @Test
    void accessForbiddenMapsTo403() {
        var response = handler.handleAccessForbidden(new AccessForbiddenException("nope"));
        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().getCode()).isEqualTo("ACCESS_FORBIDDEN");
        assertThat(response.getBody().getMessage()).isEqualTo("nope");
    }

    @Test
    void listCapacityExceededMapsTo422() {
        var response = handler.handleListCapacityExceeded(new ListCapacityExceededException());
        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().getCode()).isEqualTo("LIST_CAPACITY_EXCEEDED");
    }

    @Test
    void invalidItemMapsTo400() {
        var response = handler.handleInvalidItem(new InvalidItemException("bad name"));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo("INVALID_ITEM");
    }

    @Test
    void userNotFoundMapsTo404() {
        var response = handler.handleUserNotFound(new UserNotFoundException("who?"));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().getCode()).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void validationErrorMapsTo400WithFirstFieldError() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "name", "must not be blank"));
        var ex = new MethodArgumentNotValidException(dummyParameter(), bindingResult);

        var response = handler.handleValidation(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getMessage()).isEqualTo("name: must not be blank");
    }

    @Test
    void validationErrorWithoutFieldErrorsFallsBackToExceptionMessage() throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        var ex = new MethodArgumentNotValidException(dummyParameter(), bindingResult);

        var response = handler.handleValidation(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getMessage()).isNotBlank();
    }

    @Test
    void unexpectedExceptionMapsTo500WithoutLeakingDetails() {
        var response = handler.handleGeneral(new IllegalStateException("internal secret detail"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getMessage()).doesNotContain("secret");
    }

    @SuppressWarnings("unused")
    private void sampleMethod(String name) {
    }

    private MethodParameter dummyParameter() throws NoSuchMethodException {
        return new MethodParameter(
            ApiExceptionHandlerTest.class.getDeclaredMethod("sampleMethod", String.class), 0);
    }
}
