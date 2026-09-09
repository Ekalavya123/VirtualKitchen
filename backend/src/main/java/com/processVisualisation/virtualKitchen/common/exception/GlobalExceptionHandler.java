package com.processVisualisation.virtualKitchen.common.exception;

import com.processVisualisation.virtualKitchen.restclient.exception.AIAuthenticationException;
import com.processVisualisation.virtualKitchen.restclient.exception.AIClientException;
import com.processVisualisation.virtualKitchen.restclient.exception.AICommunicationException;
import com.processVisualisation.virtualKitchen.restclient.exception.AIInvalidResponseException;
import com.processVisualisation.virtualKitchen.restclient.exception.AITimeoutException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

/**
 * Global exception handler for all REST controllers ({@code @RestControllerAdvice}).
 * Centralizes translation of both framework exceptions (Mongo duplicate keys,
 * bean-validation failures) and this application's custom exceptions
 * (AI-client errors, recipe flow/access errors, auth errors) into a
 * consistent {@link ErrorResponse} JSON body with an appropriate HTTP status.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Catches a MongoDB unique-index violation and reports it as a conflict.
     *
     * @param ex the duplicate-key exception thrown by the Mongo driver
     * @return an {@link ErrorResponse} with a generic "duplicate entry" message, at HTTP 409 Conflict
     */
    // Handle MongoDB Duplicate Key (Unique Constraint)
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKey(DuplicateKeyException ex) {
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Conflict",
                "This record already exists (Duplicate Entry)"
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    /**
     * Catches a failed user lookup. Note the response body's {@code status}/
     * {@code error} fields report {@link HttpStatus#CONFLICT} while the
     * actual HTTP response status returned is {@link HttpStatus#BAD_REQUEST}.
     *
     * @param ex the exception thrown when a user could not be found
     * @return an {@link ErrorResponse} carrying {@code ex}'s message, sent with HTTP 400 Bad Request
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFoundException(UserNotFoundException ex) {
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Conflict",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Catches bean-validation failures on {@code @Valid} request bodies
     * (e.g. {@code @Email}, {@code @NotBlank} constraint violations) and
     * reports the first field error's default message.
     *
     * @param ex the validation exception raised by Spring's method argument binding
     * @return an {@link ErrorResponse} with the first field validation error's message, at HTTP 400 Bad Request
     */
    // Handle Validation Errors (e.g., @Email, @NotBlank)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                msg
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Catches an authentication failure when calling the external AI service.
     *
     * @param ex the AI authentication exception
     * @return an {@link ErrorResponse} with {@code ex}'s message, at HTTP 401 Unauthorized
     */
    @ExceptionHandler(AIAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAIAuthentication(AIAuthenticationException ex) {
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "AI Authentication Failed",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Catches a timeout while waiting on the external AI service.
     *
     * @param ex the AI timeout exception
     * @return an {@link ErrorResponse} with {@code ex}'s message, at HTTP 504 Gateway Timeout
     */
    @ExceptionHandler(AITimeoutException.class)
    public ResponseEntity<ErrorResponse> handleAITimeout(AITimeoutException ex) {
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.GATEWAY_TIMEOUT.value(),
                "AI Timeout",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.GATEWAY_TIMEOUT);
    }

    /**
     * Catches a malformed or unexpected response from the external AI service.
     *
     * @param ex the AI invalid-response exception
     * @return an {@link ErrorResponse} with {@code ex}'s message, at HTTP 502 Bad Gateway
     */
    @ExceptionHandler(AIInvalidResponseException.class)
    public ResponseEntity<ErrorResponse> handleAIInvalidResponse(AIInvalidResponseException ex) {
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_GATEWAY.value(),
                "AI Invalid Response",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_GATEWAY);
    }

    /**
     * Catches a network/communication failure while talking to the external
     * AI service.
     *
     * @param ex the AI communication exception
     * @return an {@link ErrorResponse} with {@code ex}'s message, at HTTP 502 Bad Gateway
     */
    @ExceptionHandler(AICommunicationException.class)
    public ResponseEntity<ErrorResponse> handleAICommunication(AICommunicationException ex) {
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_GATEWAY.value(),
                "AI Communication Failed",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_GATEWAY);
    }

    /**
     * Catch-all for any other AI-client failure not covered by the more
     * specific AI exception handlers above.
     *
     * @param ex the generic AI client exception
     * @return an {@link ErrorResponse} with {@code ex}'s message, at HTTP 500 Internal Server Error
     */
    @ExceptionHandler(AIClientException.class)
    public ResponseEntity<ErrorResponse> handleAIGeneric(AIClientException ex) {
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "AI Client Error",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Catches a failure to build a recipe's process-visualization flow.
     *
     * @param ex the recipe flow generation exception
     * @return an {@link ErrorResponse} with {@code ex}'s message, at HTTP 422 Unprocessable Entity
     */
    @ExceptionHandler(RecipeFlowGenerationException.class)
    public ResponseEntity<ErrorResponse> handleRecipeGeneration(RecipeFlowGenerationException ex) {
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "Recipe Flow Generation Failed",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * Catches an attempt to access a recipe/recipe resource the caller is
     * not authorized to use.
     *
     * @param ex the recipe access-denied exception
     * @return an {@link ErrorResponse} with {@code ex}'s message, at HTTP 403 Forbidden
     */
    @ExceptionHandler(RecipeAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleRecipeAccessDenied(RecipeAccessDeniedException ex) {
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    /**
     * Catches an authentication/authorization failure raised by the auth
     * flows. Unlike the other handlers, the response status and reason
     * phrase are taken dynamically from {@code ex.getStatus()} rather than a
     * status fixed to this handler.
     *
     * @param ex the auth exception, carrying its own intended HTTP status
     * @return an {@link ErrorResponse} with {@code ex}'s message, sent with {@code ex.getStatus()}
     */
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuthException(AuthException ex) {
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                ex.getStatus().value(),
                ex.getStatus().getReasonPhrase(),
                ex.getMessage()
        );
        return new ResponseEntity<>(error, ex.getStatus());
    }
}
