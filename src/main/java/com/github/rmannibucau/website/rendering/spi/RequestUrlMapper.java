package com.github.rmannibucau.website.rendering.spi;

import javax.servlet.http.HttpServletRequest;

/**
 * How to map the current request to the absolute url of the website.
 * In general the authority (host+port) will be rewritten.
 * Default implementation use the request absolute url.
 * {@link com.github.rmannibucau.website.rendering.internal.SelfUrlMapper}.
 */
public interface RequestUrlMapper {
    String toAbsoluteUrl(HttpServletRequest request);
}
