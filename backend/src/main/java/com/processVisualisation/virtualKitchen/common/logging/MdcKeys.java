package com.processVisualisation.virtualKitchen.common.logging;

/**
 * MDC keys shared by the logging pattern in {@code logback-spring.xml} and the code that populates them.
 */
public final class MdcKeys {

    /** Set per HTTP request by {@link RequestIdFilter}; propagated to pool threads by {@code ThreadPoolTaskPool}. */
    public static final String REQUEST_ID = "requestId";

    /** Set by {@code JwtAuthenticationFilter} once a bearer token has been validated. */
    public static final String USER_ID = "userId";

    /** Set by async job pipelines and scheduled jobs, linking their logs across threads and requests. */
    public static final String JOB_ID = "jobId";

    private MdcKeys() {
    }
}
