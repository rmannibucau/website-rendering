package com.github.rmannibucau.website.rendering.internal;

import static java.util.logging.Level.SEVERE;
import static java.util.stream.Collectors.toList;
import static org.apache.ziplock.JarLocation.jarFromRegex;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.github.rmannibucau.website.rendering.api.WebRenderer;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriverService;
import org.openqa.selenium.remote.DesiredCapabilities;

@ApplicationScoped
public class PhantomJsWebRenderer implements WebRenderer {
    private static final Logger LOGGER = Logger.getLogger(PhantomJsWebRenderer.class.getName());

    @Inject
    @ConfigProperty(name = "rmannibucau.website.rendering.phantomjs.location")
    private Optional<String> phantomJsLocation;

    @Inject
    @ConfigProperty(name = "rmannibucau.website.rendering.phantomjs.instances.count", defaultValue = "1")
    private Integer phantomJsInstanceCount;

    @Inject // potentially produced by the app, otherwise we just use chrome
    private javax.enterprise.inject.Instance<DesiredCapabilities> desiredCapabilities;

    private final Collection<Instance> instances = new ArrayList<>();
    private final Semaphore permits = new Semaphore(0);

    @PostConstruct
    private void init() {
        final File phantomJs = new File(findLocation());
        if (!phantomJs.exists() && !phantomJs.mkdirs()) {
            throw new IllegalStateException("Can't create " + phantomJs.getAbsolutePath());
        }
        final String execName = "bin/phantomjs" + (System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("win") ? ".exe" : "");
        final File exec = new File(phantomJs, execName);
        if (!exec.exists()) {
            try {
                Zips.unzip(jarFromRegex("arquillian-phantom-binary.*" + findSuffix()), phantomJs);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            exec.setExecutable(true);
        }

        final DesiredCapabilities capabilities = desiredCapabilities.isResolvable() ?
                this.desiredCapabilities.get() : DesiredCapabilities.chrome();
        capabilities.setCapability(PhantomJSDriverService.PHANTOMJS_EXECUTABLE_PATH_PROPERTY, exec.getAbsolutePath());
        instances.addAll(IntStream.range(0, phantomJsInstanceCount)
                .mapToObj(i -> new Instance(capabilities, exec))
                .collect(toList()));
        permits.release(instances.size());
    }

    // for advanced cases, directly expose the driver
    @Override
    public <T> void withDriver(final Class<T> expectedApi, final Consumer<T> consumer) {
        withInstance(driver -> {
            final T api = expectedApi.cast(driver);
            consumer.accept(api);
            return null;
        });
    }

    @Override
    public String capture(final String url) {
        return withInstance(driver -> {
            driver.get(url);
            return driver.getPageSource();
        });
    }

    private <T> T withInstance(final Function<PhantomJSDriver, T> fn) {
        try {
            permits.acquire();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        final Instance instance;
        synchronized (instances) {
            final Iterator<Instance> iterator = instances.iterator();
            instance = iterator.next();
            iterator.remove();
        }
        try {
            return fn.apply(instance.driver);
        } finally {
            synchronized (instances) {
                instances.add(instance);
            }
            permits.release();
        }
    }

    @PreDestroy
    private void destroy() {
        instances.forEach(Instance::close);
    }

    private String findLocation() {
        if (!phantomJsLocation.isPresent()) {
            final File mvnTemp = new File("target/phantomjs");
            if (mvnTemp.getParentFile().exists()) {
                return mvnTemp.getAbsolutePath();
            }
            final File gradleTemp = new File("build/phantomjs");
            if (gradleTemp.getParentFile().exists()) {
                return gradleTemp.getAbsolutePath();
            }
        }
        return phantomJsLocation.orElseThrow(() -> new IllegalArgumentException("Please specify rmannibucau.website.rendering.phantomjs.location"));
    }

    private String findSuffix() {
        final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            return "macosx.jar";
        }
        if (os.contains("win")) {
            return "windows.jar";
        }
        return ".jar";
    }

    private static class Zips {
        private Zips() {
            // no-op
        }

        private static void unzip(final File zipFile, final File destination) throws IOException {
            mkdir(destination);
            try (final ZipInputStream in = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
                ZipEntry entry;

                while ((entry = in.getNextEntry()) != null) {
                    final String path = entry.getName();
                    final File file = new File(destination, path);
                    if (entry.isDirectory()) {
                        mkdir(file);
                        continue;
                    }

                    mkdir(file.getParentFile());
                    try (final OutputStream to = new BufferedOutputStream(new FileOutputStream(file))) {
                        final byte[] buffer = new byte[1024];
                        int length;
                        while ((length = in.read(buffer)) != -1) {
                            to.write(buffer, 0, length);
                        }
                        to.flush();
                    }

                    final long lastModified = entry.getTime();
                    if (lastModified > 0) {
                        file.setLastModified(lastModified);
                    }
                }
            } catch (final IOException e) {
                throw new IOException("Unable to unzip " + zipFile, e);
            }
        }

        private static File mkdir(final File file) {
            if (file.exists()) {
                return file;
            }
            if (!file.mkdirs()) {
                throw new IllegalStateException("Cannot mkdir: " + file.getAbsolutePath());
            }
            return file;
        }
    }

    private static final class Instance implements AutoCloseable {
        private final PhantomJSDriverService service;
        private final PhantomJSDriver driver;

        public Instance(final DesiredCapabilities capabilities, final File exec) {
            service = new PhantomJSDriverService.Builder().usingPhantomJSExecutable(exec).usingAnyFreePort().build();
            driver = new PhantomJSDriver(service, capabilities);
            try {
                service.start();
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void close() {
            try {
                driver.close();
            } catch (final RuntimeException re) {
                LOGGER.log(SEVERE, re.getMessage(), re);
            }
            if (service.isRunning()) {
                try {
                    service.stop();
                } catch (final RuntimeException re) {
                    LOGGER.log(SEVERE, re.getMessage(), re);
                }
            }
        }
    }
}
