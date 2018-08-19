package com.github.rmannibucau.website.rendering.internal;

import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.github.rmannibucau.website.rendering.spi.HtmlCache;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class DefaultHtmlCache implements HtmlCache {
    @Inject
    @ConfigProperty(name = "rmannibucau.website.rendering.cache.mode", defaultValue = "jcache")
    private String mode;

    @Inject
    private JCacheHtmlCache jcache;

    @Inject
    private LocalFileSystemHtmlCache fileSystem;

    private HtmlCache delegate;

    @PostConstruct
    private void init() {
        switch (mode) {
            case "jcache":
                delegate = jcache;
                break;
            case "file":
                delegate = fileSystem;
                break;
            default:
                throw new IllegalArgumentException("Unsupported cache mode: " + mode);
        }
    }

    @Override
    public String get(final String key) {
        return delegate.get(key);
    }

    @Override
    public boolean putIfAbsent(final String key, final String html) {
        return delegate.putIfAbsent(key, html);
    }

    @Override
    public boolean invalidate(final String key) {
        return delegate.invalidate(key);
    }
}
