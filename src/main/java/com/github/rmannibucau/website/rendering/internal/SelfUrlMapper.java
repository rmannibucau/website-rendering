package com.github.rmannibucau.website.rendering.internal;

import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import com.github.rmannibucau.website.rendering.spi.RequestUrlMapper;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class SelfUrlMapper implements RequestUrlMapper {
    @Inject
    @ConfigProperty(name = "rmannibucau.website.rendering.url.mapper.base")
    private Optional<String> base;

    @Override
    public String toAbsoluteUrl(final HttpServletRequest request) {
        return base.orElseGet(() ->
                request.getScheme() + "://" +
                request.getServerName() + (request.getServerPort() > 0 ? ":" + request.getServerPort() : "") +
                request.getContextPath())
                + request.getRequestURI().substring(request.getContextPath().length());
    }
}
