package com.processVisualisation.virtualKitchen.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gives every HTTP request a request ID and makes it available to every log line written while handling it.
 * <p>
 * Runs first in the servlet filter chain (ahead of Spring Security), so authentication failures and the JWT
 * filter are covered too. A valid inbound {@value #HEADER} is reused so a caller can correlate its own logs;
 * anything else is replaced, which keeps client-controlled text out of the logs. The ID is echoed back in the
 * response header so a user or bug report can quote it.
 * <p>
 * The MDC is cleared when the request finishes: servlet threads are pooled, so anything left behind would
 * leak into the next request served by the same thread. Work handed to a {@code TaskPool} carries a copy of
 * the MDC (see {@code ThreadPoolTaskPool}).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-ID";

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);
    private static final Pattern VALID_INBOUND_ID = Pattern.compile("^[A-Za-z0-9-]{8,64}$");

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HEADER));
        MDC.put(MdcKeys.REQUEST_ID, requestId);
        response.setHeader(HEADER, requestId);

        boolean quiet = isQuiet(request);
        long startedAt = System.nanoTime();
        if (!quiet) {
            log.debug("event=http_request_started method={} path={}", request.getMethod(), request.getRequestURI());
        }

        boolean failed = false;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException ex) {
            failed = true;
            throw ex;
        } finally {
            if (!quiet) {
                // An exception escaping the chain means the container will answer 500, whatever the response says now.
                int status = failed ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus();
                log.info("event=http_request_completed method={} path={} status={} durationMs={}",
                        request.getMethod(), request.getRequestURI(), status, (System.nanoTime() - startedAt) / 1_000_000L);
            }
            MDC.clear();
        }
    }

    static String resolveRequestId(String inbound) {
        if (inbound != null && VALID_INBOUND_ID.matcher(inbound).matches()) {
            return inbound;
        }
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /** Health checks, API docs and CORS preflights would drown out real traffic. */
    private boolean isQuiet(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }
}
