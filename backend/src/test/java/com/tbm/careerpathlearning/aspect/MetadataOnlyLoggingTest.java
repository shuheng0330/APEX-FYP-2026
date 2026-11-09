package com.tbm.careerpathlearning.aspect;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import static org.junit.jupiter.api.Assertions.*;

class MetadataOnlyLoggingTest {
    ListAppender<ILoggingEvent> appender;
    Logger logger;
    @BeforeEach void setup() {
        logger = (Logger) LoggerFactory.getLogger(LoggingAspect.class);
        appender = new ListAppender<>(); appender.start(); logger.addAppender(appender);
    }
    @AfterEach void close() { logger.detachAppender(appender); appender.stop(); }
    @Test void sensitiveArgumentsResultsAndExceptionBodiesAreNotLogged() {
        var factory = new AspectJProxyFactory(new SensitiveExample()); factory.addAspect(new LoggingAspect());
        SensitiveExample proxy = factory.getProxy();
        assertEquals("private-output", proxy.echo("private-input"));
        assertThrows(IllegalStateException.class, () -> proxy.fail("secret-key"));
        assertFalse(appender.list.isEmpty());
        for (var event : appender.list) {
            assertFalse(event.getFormattedMessage().contains("private")); assertFalse(event.getFormattedMessage().contains("secret-key"));
            assertNull(event.getThrowableProxy());
        }
    }
    @Test void annotatedRepositoryInterfaceProtectsInheritedMethods() {
        var factory = new AspectJProxyFactory(new PreviewRepository()); factory.setInterfaces(SensitiveRepository.class);
        factory.addAspect(new LoggingAspect()); SensitiveRepository proxy = factory.getProxy();
        assertEquals("private-output", proxy.echo("private-input"));
        assertFalse(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("private")));
    }
    @Test void unrelatedLoggingRemainsUnchanged() {
        var factory = new AspectJProxyFactory(new OrdinaryExample()); factory.addAspect(new LoggingAspect());
        OrdinaryExample proxy = factory.getProxy(); proxy.echo("ordinary-value");
        assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("ordinary-value")));
    }
    @Test void scopeProtectsNestedUnannotatedReadsAndOuterResultsAndErrors() {
        var readsFactory = new AspectJProxyFactory(new OrdinaryExample());
        readsFactory.addAspect(new LoggingAspect());
        OrdinaryExample reads = readsFactory.getProxy();
        var serviceFactory = new AspectJProxyFactory(new ScopedExample(reads));
        serviceFactory.addAspect(new LoggingAspect());
        ScopedExample service = serviceFactory.getProxy();

        assertEquals("private-input", service.echo("private-input"));
        assertThrows(IllegalStateException.class, () -> service.fail("private-input"));
        assertFalse(MetadataOnlyLogScope.active());
        assertEquals(8, appender.list.size());
        for (var event : appender.list) {
            assertTrue(event.getFormattedMessage().contains("[payload omitted]"));
            assertFalse(event.getFormattedMessage().contains("private"));
            assertNull(event.getThrowableProxy());
            assertFalse(java.util.Arrays.deepToString(event.getArgumentArray()).contains("private"));
        }

        appender.list.clear();
        reads.echo("ordinary-after-scope");
        assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("ordinary-after-scope")));
    }
    @Test void scopeUtilityIsExcludedFromLoggingPointcuts() throws Exception {
        for (var method : LoggingAspect.class.getDeclaredMethods()) {
            String expression = null;
            if (method.isAnnotationPresent(org.aspectj.lang.annotation.Before.class))
                expression = method.getAnnotation(org.aspectj.lang.annotation.Before.class).value();
            if (method.isAnnotationPresent(org.aspectj.lang.annotation.AfterReturning.class))
                expression = method.getAnnotation(org.aspectj.lang.annotation.AfterReturning.class).pointcut();
            if (method.isAnnotationPresent(org.aspectj.lang.annotation.AfterThrowing.class))
                expression = method.getAnnotation(org.aspectj.lang.annotation.AfterThrowing.class).pointcut();
            if (expression == null) continue;
            var pointcut = new org.springframework.aop.aspectj.AspectJExpressionPointcut();
            pointcut.setExpression(expression);
            for (String name : new String[]{"enter", "active", "close"})
                assertFalse(pointcut.matches(MetadataOnlyLogScope.class.getMethod(name), MetadataOnlyLogScope.class));
        }
    }
    @MetadataOnlyLogging public static class SensitiveExample {
        public String echo(String input) { return "private-output"; }
        public String fail(String input) { throw new IllegalStateException("private-error " + input); }
    }
    public interface BaseRepository { String echo(String input); }
    @MetadataOnlyLogging public interface SensitiveRepository extends BaseRepository {}
    public static class PreviewRepository implements SensitiveRepository { public String echo(String input) { return "private-output"; } }
    @MetadataOnlyLogging public static class ScopedExample {
        private final OrdinaryExample reads;
        ScopedExample(OrdinaryExample reads) { this.reads = reads; }
        public String echo(String input) {
            try (var scope = MetadataOnlyLogScope.enter()) { return reads.echo(input); }
        }
        public String fail(String input) {
            try (var scope = MetadataOnlyLogScope.enter()) { return reads.fail(input); }
        }
    }
    public static class OrdinaryExample {
        public String echo(String input) { return input; }
        public String fail(String input) { throw new IllegalStateException("private-error " + input); }
    }
}
