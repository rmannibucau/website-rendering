package com.github.rmannibucau.website.rendering.internal;

import static java.util.stream.Collectors.joining;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Typed;
import javax.inject.Inject;

import com.github.rmannibucau.website.rendering.spi.HtmlCache;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
@Typed(LocalFileSystemHtmlCache.class)
public class LocalFileSystemHtmlCache implements HtmlCache {
    @Inject
    @ConfigProperty(name = "rmannibucau.website.rendering.cache.directory")
    private Optional<String> directory;

    private File root;

    @PostConstruct
    private void init() {
        root = new File(directory.orElseThrow(
                () -> new IllegalArgumentException("No directory set, please configure rmannibucau.website.rendering.cache.directory")));
    }

    @Override
    public String get(final String key) {
        final File cacheFile = getCacheFile(key);
        try {
            return cacheFile.exists() ?
                    Files.readAllLines(cacheFile.toPath()).stream().collect(joining("\n")) :
                    null;
        } catch (final IOException e) {
            return null;
        }
    }

    @Override
    public boolean putIfAbsent(final String key, final String html) {
        final File cacheFile = getCacheFile(key);
        if (!cacheFile.getParentFile().exists() && !cacheFile.getParentFile().mkdirs()) {
            throw new IllegalStateException("Can't create " + cacheFile);
        }
        try {
            Files.write(cacheFile.toPath(), html.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (final IOException e) {
            return false;
        }
    }

    @Override
    public boolean invalidate(final String key) {
        final File file = getCacheFile(key);
        return file.exists() && file.delete();
    }

    private File getCacheFile(final String key) {
        return new File(root, key.contains("?") ? key.substring(0, key.indexOf('?')) : key);
    }
}
