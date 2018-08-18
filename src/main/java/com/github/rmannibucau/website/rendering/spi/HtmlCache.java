package com.github.rmannibucau.website.rendering.spi;

/**
 * Where the html pages will be stored. Default implementation relies on JCache,
 * {@link com.github.rmannibucau.website.rendering.internal.JCacheHtmlCache}.
 */
public interface HtmlCache {
    String get(final String key);

    boolean putIfAbsent(final String key, final String html);

    boolean invalidate(final String key);
}
