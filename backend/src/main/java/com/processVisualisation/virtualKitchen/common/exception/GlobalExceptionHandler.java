package com.processVisualisation.virtualKitchen.common.exception;

import com.processVisualisation.virtualKitchen.common.logging.FailureLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.processVisualisation.virtualKitchen.ai.queue.AiQueueFullException;
import com.processVisualisation.virtualKitchen.ai.queue.AiRequestTimeoutException;
import com.processVisualisation.virtualKitchen.ai.routing.NoAvailableModelException;
import com.processVisualisation.virtualKitchen.restclient.exception.AIAuthenticationException;
import com.processVisualisation.virtualKitchen.restclient.exception.AIClientException;
import com.processVisualisation.virtualKitchen.restclient.exception.AICommunicationException;
import com.processVisualisation.virtualKitchen.restclient.exception.AIInvalidResponseException;
import com.processVisualisation.virtualKitchen.restclient.exception.AITimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

/**
 * Global exception handler for all REST controllers ({@code @RestControllerAdvice}).
 * Centralizes translation of both framework exceptions (Mongo duplicate keys,
 * bean-validation failures) and this application's custom exceptions
 * (AI-client errors, recipe flow/access errors, auth errors) into a
 * consistent {@link ErrorResponse} JSON body with an appropriate HTTP status.
 * <p>
 * This is also the one place a failed synchronous request is logged, once: client errors (400/404) at DEBUG,
 * rejected requests (403/409/422/429, auth) at WARN, AI/provider failures at ERROR without a stack (the type
 * and message say what happened, and the AI client has already logged the call), and anything unexpected at
 * ERROR with the full stack trace. The request ID comes from the MDC.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Catches a MongoDB unique-index violation and reports it as a conflict.
     *
     * @param ex the duplicate-key exception thrown by the Mongo driver
     * @return an {@link ErrorResponse} with a generic "duplicate entry" message, at HTTP 409 Conflict
     */
    // Handle MongoDB Duplicate Key (Unique Constraint)
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateKey(DuplicateKeyException ex) {
        // The driver message quotes the duplicate value (often an email address), so it is not logged.
        // No message: a duplicate-key error quotes the duplicated value (e.g. a user's email).
        log.warn("event=request_rejected status=409 {} errorType={}", route(), ex.getClass().getSimpleName());
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
        logClientError(ex);
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
        logClientError(ex);
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
        logUpstreamFailure(ex);
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
        logUpstreamFailure(ex);
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
        logUpstreamFailure(ex);
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
        logUpstreamFailure(ex);
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
        logUpstreamFailure(ex);
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
    @ExceptionHandler(RecipeProcessAiException.class)
    public ResponseEntity<ErrorResponse> handleRecipeGeneration(RecipeProcessAiException ex) {
        logRejected(ex);
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
        logRejected(ex);
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    /**
     * Catches a recipe-level process save made from a stale revision (the recipe was saved
     * elsewhere since the client loaded it).
     *
     * @param ex the revision conflict exception
     * @return an {@link ErrorResponse} with {@code ex}'s message, at HTTP 409 Conflict
     */
    @ExceptionHandler(RecipeRevisionConflictException.class)
    public ResponseEntity<ErrorResponse> handleRecipeRevisionConflict(RecipeRevisionConflictException ex) {
        logRejected(ex);
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Conflict",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    /**
     * Catches an AI Recipe Creation request the workflow can't honour (invalid task selection, or an
     * action its current state doesn't allow).
     *
     * @param ex the workflow exception, carrying its own intended HTTP status (400 or 409)
     * @return an {@link ErrorResponse} with {@code ex}'s message, sent with {@code ex.getStatus()}
     */
    @ExceptionHandler(RecipeAiWorkflowException.class)
    public ResponseEntity<ErrorResponse> handleRecipeAiWorkflow(RecipeAiWorkflowException ex) {
        if (ex.getStatus().is4xxClientError() && ex.getStatus() != HttpStatus.BAD_REQUEST) {
            logRejected(ex);
        } else {
            logClientError(ex);
        }
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                ex.getStatus().value(),
                ex.getStatus().getReasonPhrase(),
                ex.getMessage()
        );
        return new ResponseEntity<>(error, ex.getStatus());
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
        logRejected(ex);
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                ex.getStatus().value(),
                ex.getStatus().getReasonPhrase(),
                ex.getMessage()
        );
        return new ResponseEntity<>(error, ex.getStatus());
    }

    /**
     * Catches the case where a user's preferred/default AI model needs
     * credits they don't have and no enabled fallback model is configured.
     * Scoped to the single AI operation that triggered it — no other feature
     * is affected.
     *
     * @param ex the no-available-model exception
     * @return an {@link ErrorResponse} with {@code ex}'s message, at HTTP 503 Service Unavailable
     */
    @ExceptionHandler(NoAvailableModelException.class)
    public ResponseEntity<ErrorResponse> handleNoAvailableModel(NoAvailableModelException ex) {
        logUpstreamFailure(ex);
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "AI Model Unavailable",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * Catches a rejection from the bounded AI request queue when it is
     * already at its configured max depth.
     *
     * @param ex the queue-full exception
     * @return an {@link ErrorResponse} with {@code ex}'s message, at HTTP 429 Too Many Requests
     */
    @ExceptionHandler(AiQueueFullException.class)
    public ResponseEntity<ErrorResponse> handleAiQueueFull(AiQueueFullException ex) {
        logRejected(ex);
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                "AI System Busy",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * Catches a queued AI job that never completed within its configured
     * job-level timeout.
     *
     * @param ex the AI request timeout exception
     * @return an {@link ErrorResponse} with {@code ex}'s message, at HTTP 504 Gateway Timeout
     */
    @ExceptionHandler(AiRequestTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleAiRequestTimeout(AiRequestTimeoutException ex) {
        logUpstreamFailure(ex);
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.GATEWAY_TIMEOUT.value(),
                "AI Request Timeout",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.GATEWAY_TIMEOUT);
    }

    /**
     * Catches a Process that failed structural validation (bad node/edge
     * references, circular or self-referencing subprocesses, a duplicate
     * MAIN process, unresolvable recipe ownership, etc).
     *
     * @param ex the process validation exception, carrying the joined list of validation errors
     * @return an {@link ErrorResponse} with {@code ex}'s message, at HTTP 422 Unprocessable Entity
     */
    @ExceptionHandler(ProcessValidationException.class)
    public ResponseEntity<ErrorResponse> handleProcessValidation(ProcessValidationException ex) {
        logRejected(ex);
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "Process Validation Failed",
                ex.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * Catches a lookup that found no matching entity (e.g. an unknown
     * recipe or process id). This is the same exception type
     * {@code RecipeTemplateServiceImpl}'s {@code findById(id).orElseThrow()}
     * calls already raised before this handler existed (previously falling
     * through to a generic 500); adding a handler for it here fixes that for
     * every existing caller as well as the new Recipe/Process endpoints.
     *
     * @param ex the not-found exception
     * @return an {@link ErrorResponse} with {@code ex}'s message, at HTTP 404 Not Found
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException ex) {
        logClientError(ex);
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage() != null ? ex.getMessage() : "The requested resource was not found"
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    /**
     * Last-resort handler for anything not mapped above. Exceptions Spring MVC or Spring Security already know
     * how to answer (malformed JSON, unknown path, wrong method, {@code @ResponseStatus} types, ...) are
     * rethrown untouched, which hands them back to Spring's default resolvers so their usual 4xx statuses are
     * kept. Everything else is a bug: logged with its stack trace and answered with a generic 500 that does not
     * leak the exception message.
     *
     * @param ex the unhandled exception
     * @return an {@link ErrorResponse} with a generic message, at HTTP 500 Internal Server Error
     * @throws Exception {@code ex} itself, when it is a framework exception with its own status mapping
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) throws Exception {
        if (isHandledByFramework(ex)) {
            log.debug("event=request_rejected {} errorType={} error=\"{}\"", route(), ex.getClass().getSimpleName(), FailureLogger.oneLine(ex.getMessage()));
            throw ex;
        }
        log.error("event=request_failed status=500 {} errorType={}", route(), ex.getClass().getSimpleName(), ex);
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred"
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static boolean isHandledByFramework(Exception ex) {
        return ex instanceof org.springframework.web.ErrorResponse
                || ex instanceof jakarta.servlet.ServletException
                || ex instanceof TypeMismatchException
                || ex instanceof HttpMessageNotReadableException
                || ex instanceof HttpMessageNotWritableException
                || ex instanceof BindException
                || ex instanceof AccessDeniedException
                || ex instanceof AuthenticationException
                || AnnotatedElementUtils.hasAnnotation(ex.getClass(), ResponseStatus.class);
    }

    /**
     * 400/404: the caller's mistake; INFO, so the operation log still says why a request did nothing. Only safe
     * detail is logged: validation errors list the offending field names (their messages echo the rejected values,
     * which can be personal data), lookups give their message (ids), and anything else just its type.
     */
    private static void logClientError(Exception ex) {
        String detail;
        if (ex instanceof MethodArgumentNotValidException invalid) {
            detail = "fields=" + invalid.getBindingResult().getFieldErrors().stream()
                    .map(org.springframework.validation.FieldError::getField).distinct().toList();
        } else if (ex instanceof NoSuchElementException) {
            detail = "error=\"" + FailureLogger.oneLine(ex.getMessage()) + "\"";
        } else {
            detail = "";
        }
        log.info("event=request_rejected {} errorType={} {}", route(), ex.getClass().getSimpleName(), detail);
    }

    /** Handled, expected-but-unwanted outcomes: forbidden, conflict, invalid AI output, capacity. */
    private static void logRejected(Exception ex) {
        log.warn("event=request_rejected {} errorType={} error=\"{}\"", route(), ex.getClass().getSimpleName(),
                FailureLogger.oneLine(ex.getMessage()));
    }

    /** A dependency (AI provider, model routing) failed; needs attention but the stack adds nothing. */
    private static void logUpstreamFailure(Exception ex) {
        log.error("event=request_failed {} errorType={} error=\"{}\"", route(), ex.getClass().getSimpleName(),
                FailureLogger.oneLine(ex.getMessage()));
    }

    /** {@code method=.. path=..} of the request being handled, so a rejection says which call it was. */
    private static String route() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return "method=" + request.getMethod() + " path=" + request.getRequestURI();
        }
        return "method=- path=-";
    }
}
