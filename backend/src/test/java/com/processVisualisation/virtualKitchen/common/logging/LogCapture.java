package com.processVisualisation.virtualKitchen.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Captures the events written to one logger for the duration of a test. */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final Level previousLevel;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(Logger logger) {
        this.logger = logger;
        this.previousLevel = logger.getLevel();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
    }

    public static LogCapture of(Class<?> loggerClass) {
        return new LogCapture((Logger) LoggerFactory.getLogger(loggerClass));
    }

    public static LogCapture of(String loggerName) {
        return new LogCapture((Logger) LoggerFactory.getLogger(loggerName));
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    /** Events whose formatted message starts with {@code event=<name>}. */
    public List<ILoggingEvent> events(String eventName) {
        return events().stream()
                .filter(e -> e.getFormattedMessage().startsWith("event=" + eventName + " ")
                        || e.getFormattedMessage().equals("event=" + eventName))
                .toList();
    }

    public String allMessages() {
        StringBuilder sb = new StringBuilder();
        for (ILoggingEvent e : events()) {
            sb.append(e.getLevel()).append(' ').append(e.getFormattedMessage()).append('\n');
        }
        return sb.toString();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
        appender.stop();
    }
}
