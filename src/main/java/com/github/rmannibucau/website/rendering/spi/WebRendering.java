package com.github.rmannibucau.website.rendering.spi;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.inject.Qualifier;

/**
 * Usable to customize the default setup. It can be to produce:
 * <ul>
 *     <li>A {@link javax.cache.configuration.MutableConfiguration<String, String>} when using default {@link HtmlCache}</li>
 *     <li>A {@link javax.cache.CacheManager} when using default {@link HtmlCache}</li>
 * </ul>
 */
@Qualifier
@Target({ METHOD, TYPE, FIELD })
@Retention(RUNTIME)
public @interface WebRendering {
}
