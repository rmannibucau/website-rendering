package com.github.rmannibucau.website.rendering.internal;

import static java.lang.Thread.sleep;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.WARNING;
import static javax.servlet.DispatcherType.REQUEST;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import com.github.rmannibucau.website.rendering.spi.HtmlCache;
import com.github.rmannibucau.website.rendering.spi.RequestUrlMapper;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Dependent
public class ServerRenderingFilter implements Filter {
    private static final Logger LOGGER = Logger.getLogger(ServerRenderingFilter.class.getName());

    @Inject
    private PhantomJsWebRenderer renderer;

    @Inject
    private HtmlCache cache;

    @Inject
    private RequestUrlMapper requestUrlMapper;

    @Inject
    @ConfigProperty(name = "rmannibucau.website.rendering.web.thread.pool.size", defaultValue = "64")
    private Integer poolSize;

    @Inject
    @ConfigProperty(name = "rmannibucau.website.rendering.web.thread.pool.timeout", defaultValue = "18000")
    private Integer timeout;

    private ExecutorService pool;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private final ConcurrentMap<String, AtomicReference<Future<?>>> inProgressRenderings = new ConcurrentHashMap<>();

    @Override
    public void init(final FilterConfig filterConfig) {
        pool = Executors.newScheduledThreadPool(poolSize);
        pool.submit(() -> {
            while (running.get()) {
                new HashMap<>(inProgressRenderings).forEach((url, futureRef) -> {
                    final Future<?> future = futureRef.get();
                    if (future == null) {
                        return;
                    }
                    try {
                        future.get(timeout, MILLISECONDS);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (final TimeoutException | ExecutionException e) {
                        inProgressRenderings.remove(url);
                        LOGGER.log(WARNING, e.getMessage(), e);
                    }
                });
            }
        });
    }

    @Override
    public void destroy() {
        running.set(false);
        pool.shutdown();

        int remainingWaitIterations = (int) (TimeUnit.MINUTES.toMillis(1) / 250);
        while (--remainingWaitIterations > 0 && !pool.isTerminated()) {
            try {
                sleep(250);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void doFilter(final ServletRequest servletRequest, final ServletResponse servletResponse, final FilterChain chain) throws IOException, ServletException {
        if (!HttpServletRequest.class.isInstance(servletRequest)) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }
        final HttpServletRequest request = HttpServletRequest.class.cast(servletRequest);
        if (!"GET".equals(request.getMethod())) {
            chain.doFilter(servletRequest, servletResponse);
            return;
        }

        doFilterImpl(request, servletResponse, chain);
    }

    private void doFilterImpl(final HttpServletRequest request, final ServletResponse servletResponse, final FilterChain chain) throws IOException, ServletException {
        final String url = requestUrlMapper.toAbsoluteUrl(request);
        final String html = cache.get(url);
        if (html != null) {
            servletResponse.getWriter().write(html);
            return;
        }

        chain.doFilter(request, servletResponse);

        if (running.get()) {
            final AtomicReference<Future<?>> ref = new AtomicReference<>();
            final Future<?> future = pool.submit(() -> {
                if (inProgressRenderings.putIfAbsent(url, ref) == null) {
                    try {
                        cache.putIfAbsent(url, renderer.capture(url));
                    } finally {
                        inProgressRenderings.remove(url);
                    }
                }
            });
            ref.set(future);
        }
    }

    public static class Registrer implements ServletContainerInitializer {

        @Override
        public void onStartup(final Set<Class<?>> set, final ServletContext servletContext) {
            final Config config = ConfigProvider.getConfig(servletContext.getClassLoader());
            config.getOptionalValue("rmannibucau.website.rendering.web.urlPatterns", String.class)
                    .map(this::toUrlPatterns)
                    .filter(l -> l.length > 0)
                    .ifPresent(patterns -> {
                        final FilterRegistration.Dynamic filter = servletContext.addFilter(ServerRenderingFilter.class.getSimpleName(), ServerRenderingFilter.class);
                        filter.setAsyncSupported(true);
                        filter.addMappingForUrlPatterns(EnumSet.of(REQUEST), false, patterns);
                    });
        }

        private String[] toUrlPatterns(String s) {
            return Stream.of(s.split(","))
                .map(String::trim)
                .filter(it -> !it.isEmpty())
                .toArray(String[]::new);
        }
    }
}
