package com.shopmate.adapter.in.web;

import com.shopmate.domain.model.AccessForbiddenException;
import com.shopmate.domain.model.AlreadyInGroupException;
import com.shopmate.domain.model.GroupNameRequiredException;
import com.shopmate.domain.model.InvalidItemException;
import com.shopmate.domain.model.InviteExpiredException;
import com.shopmate.domain.model.InviteInvalidException;
import com.shopmate.domain.model.ListCapacityExceededException;
import com.shopmate.domain.model.ListNotFoundException;
import com.shopmate.domain.model.NoGroupException;
import com.shopmate.domain.model.PicnicCredentialsMissingException;
import com.shopmate.domain.model.PicnicLoginFailedException;
import com.shopmate.domain.model.PicnicSecondFactorRequiredException;
import com.shopmate.domain.model.PicnicSessionExpiredException;
import com.shopmate.domain.model.PicnicUnavailableException;
import com.shopmate.domain.model.UserNotFoundException;
import com.shopmate.generated.model.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ListNotFoundException.class)
    public ResponseEntity<ApiError> handleListNotFound(ListNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("LIST_NOT_FOUND", ex.getMessage(), OffsetDateTime.now()));
    }

    @ExceptionHandler(AccessForbiddenException.class)
    public ResponseEntity<ApiError> handleAccessForbidden(AccessForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("ACCESS_FORBIDDEN", ex.getMessage(), OffsetDateTime.now()));
    }

    @ExceptionHandler(ListCapacityExceededException.class)
    public ResponseEntity<ApiError> handleListCapacityExceeded(ListCapacityExceededException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("LIST_CAPACITY_EXCEEDED", ex.getMessage(), OffsetDateTime.now()));
    }

    @ExceptionHandler(InvalidItemException.class)
    public ResponseEntity<ApiError> handleInvalidItem(InvalidItemException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("INVALID_ITEM", ex.getMessage(), OffsetDateTime.now()));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("USER_NOT_FOUND", ex.getMessage(), OffsetDateTime.now()));
    }

    // Deliberately distinct from ACCESS_FORBIDDEN: the frontend routes a NO_GROUP
    // response to onboarding rather than treating it as a generic permission error.
    @ExceptionHandler(NoGroupException.class)
    public ResponseEntity<ApiError> handleNoGroup(NoGroupException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ApiError("NO_GROUP", ex.getMessage(), OffsetDateTime.now()));
    }

    @ExceptionHandler(GroupNameRequiredException.class)
    public ResponseEntity<ApiError> handleGroupNameRequired(GroupNameRequiredException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("GROUP_NAME_REQUIRED", ex.getMessage(), OffsetDateTime.now()));
    }

    @ExceptionHandler(AlreadyInGroupException.class)
    public ResponseEntity<ApiError> handleAlreadyInGroup(AlreadyInGroupException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("ALREADY_IN_GROUP", ex.getMessage(), OffsetDateTime.now()));
    }

    @ExceptionHandler(InviteInvalidException.class)
    public ResponseEntity<ApiError> handleInviteInvalid(InviteInvalidException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("INVITE_INVALID", ex.getMessage(), OffsetDateTime.now()));
    }

    @ExceptionHandler(InviteExpiredException.class)
    public ResponseEntity<ApiError> handleInviteExpired(InviteExpiredException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("INVITE_EXPIRED", ex.getMessage(), OffsetDateTime.now()));
    }

    @ExceptionHandler(PicnicCredentialsMissingException.class)
    public ResponseEntity<ApiError> handlePicnicCredentialsMissing(PicnicCredentialsMissingException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("PICNIC_CREDENTIALS_MISSING", ex.getMessage(), OffsetDateTime.now()));
    }

    @ExceptionHandler(PicnicLoginFailedException.class)
    public ResponseEntity<ApiError> handlePicnicLoginFailed(PicnicLoginFailedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("PICNIC_LOGIN_FAILED", ex.getMessage(), OffsetDateTime.now()));
    }

    @ExceptionHandler(PicnicSecondFactorRequiredException.class)
    public ResponseEntity<ApiError> handlePicnicSecondFactorRequired(PicnicSecondFactorRequiredException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("PICNIC_SECOND_FACTOR_REQUIRED", ex.getMessage(), OffsetDateTime.now()));
    }

    @ExceptionHandler(PicnicSessionExpiredException.class)
    public ResponseEntity<ApiError> handlePicnicSessionExpired(PicnicSessionExpiredException ex) {
        // Distinct from PICNIC_UNAVAILABLE on purpose: waiting fixes an outage, but only
        // re-linking fixes this, and the frontend has to send the user somewhere different.
        log.warn("Picnic refused a stored session: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("PICNIC_SESSION_EXPIRED", ex.getMessage(), OffsetDateTime.now()));
    }

    @ExceptionHandler(PicnicUnavailableException.class)
    public ResponseEntity<ApiError> handlePicnicUnavailable(PicnicUnavailableException ex) {
        // Picnic's API is unofficial and can change under us without notice (ADR-0014), so an
        // outage here is a signal about *them*, not a client mistake — it must leave a trace.
        // Logged at warn, not error: a third party being down is not our alert-worthy failure.
        log.warn("Picnic is unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("PICNIC_UNAVAILABLE", ex.getMessage(), OffsetDateTime.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError("VALIDATION_ERROR", message, OffsetDateTime.now()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception ex) {
        log.error("Unhandled exception while processing request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "An unexpected error occurred", OffsetDateTime.now()));
    }
}
