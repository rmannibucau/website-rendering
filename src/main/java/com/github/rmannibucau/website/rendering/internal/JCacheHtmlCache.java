package com.github.rmannibucau.website.rendering.internal;

import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.Optional;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.spi.CachingProvider;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.Typed;
import javax.inject.Inject;

import com.github.rmannibucau.website.rendering.spi.HtmlCache;
import com.github.rmannibucau.website.rendering.spi.WebRendering;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Typed(JCacheHtmlCache.class)
public class JCacheHtmlCache implements HtmlCache {
    @Inject
    @WebRendering
    private Instance<CacheManager> cacheManager;

    @Inject
    @WebRendering
    private Instance<MutableConfiguration<String, String>> cacheConfiguration;

    @Inject
    @ConfigProperty(name = "rmannibucau.website.rendering.cache.name", defaultValue = "rmannibucau.website.rendering.cache")
    private String cacheName;

    @Inject
    @ConfigProperty(name = "rmannibucau.website.rendering.cache.jmx", defaultValue = "true")
    private Boolean cacheJmx;

    @Inject
    @ConfigProperty(name = "rmannibucau.website.rendering.cache.statistics", defaultValue = "false")
    private Boolean cacheStats;

    @Inject
    @ConfigProperty(name = "rmannibucau.website.rendering.cache.configuration.uri")
    private Optional<String> cacheConfigUri;

    @Inject
    @ConfigProperty(name = "rmannibucau.website.rendering.cache.configuration.properties")
    private Optional<String> cacheConfigProperties;

    private Cache<String, String> cache;
    private CachingProvider provider;

    @PostConstruct
    private void init() {
        if (cacheManager.isResolvable()) {
            cache = cacheManager.get().createCache(cacheName, createConfiguration());
        } else {
            provider = Caching.getCachingProvider(Thread.currentThread().getContextClassLoader());
            final CacheManager manager = provider.getCacheManager(
                    cacheConfigUri.map(URI::create).orElseGet(provider::getDefaultURI),
                    Thread.currentThread().getContextClassLoader(),
                    cacheConfigProperties.map(props -> {
                        try (final StringReader reader = new StringReader(props)) {
                            final Properties properties = new Properties();
                            properties.load(reader);
                            return properties;
                        } catch (final IOException e) {
                            throw new IllegalArgumentException(e);
                        }
                    }).orElseGet(provider::getDefaultProperties));
            cache = manager.createCache(cacheName, createConfiguration());
        }
    }

    @PreDestroy
    private void destroy() {
        cache.close();
        // no need to close the manager, if provided it is managed by the user, if not the provider#close will do it
        ofNullable(provider).ifPresent(CachingProvider::close);
    }

    @Override
    public String get(final String key) {
        return cache.get(key);
    }

    @Override
    public boolean putIfAbsent(final String key, final String html) {
        return cache.putIfAbsent(key, html);
    }

    @Override
    public boolean invalidate(final String key) {
        return cache.remove(key);
    }

    private MutableConfiguration<String, String> createConfiguration() {
        if (cacheConfiguration.isResolvable()) {
            return cacheConfiguration.get();
        }

        final MutableConfiguration<String, String> configuration = new MutableConfiguration<String, String>()
                .setTypes(String.class, String.class)
                .setStoreByValue(false);
        if (cacheJmx) {
            configuration.setManagementEnabled(true);
        }
        if (cacheStats) {
            configuration.setStatisticsEnabled(true);
        }
        return configuration;
    }
}
