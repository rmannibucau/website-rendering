package com.github.rmannibucau.website.rendering.internal;

import static java.lang.Thread.sleep;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.rules.RuleChain.outerRule;

import java.util.function.Consumer;

import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import com.github.rmannibucau.website.rendering.spi.HtmlCache;

import org.apache.meecrowave.junit.InjectRule;
import org.apache.meecrowave.junit.MeecrowaveRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runners.model.Statement;

public class ServerRenderingFilterTest {
    private static final MeecrowaveRule SERVER = new MeecrowaveRule();

    @ClassRule
    public static final TestRule RULE = outerRule((base, description) -> new Statement() {
        @Override
        public void evaluate() throws Throwable {
            System.setProperty("rmannibucau.website.rendering.web.urlPatterns", "/cached/*");
            try {
                base.evaluate();
            } finally {
                System.clearProperty("rmannibucau.website.rendering.web.urlPatterns");
            }
        }
    }).around(SERVER);

    @Rule
    public final InjectRule inject = new InjectRule(this);

    @Inject
    private HtmlCache cache;

    @Test
    public void cache() {
        withClient(target -> assertEquals(HttpServletResponse.SC_OK, target.path("/cached/index.html").request().get().getStatus()));
        final String key = base() + "/cached/index.html";
        retry(10).accept(() -> {
            final String value = cache.get(key);
            assertNotNull(value);
            assertTrue(value.contains("<h1>Index</h1>"));
            assertTrue(cache.invalidate(key));
        });
    }

    @Test
    public void noCache() {
        withClient(target -> assertEquals(HttpServletResponse.SC_OK, target.path("/foo.html").request().get().getStatus()));
        retry(8).accept(() -> assertFalse(cache.invalidate(base() + "/foo.html")));
    }

    private Consumer<Runnable> retry(final int count) {
        return task -> {
            AssertionError error = null;
            for (int i = 0; i < count; i++) {
                try {
                    task.run();
                    return;
                } catch (final Throwable throwable) {
                    try {
                        sleep(1000);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    if (AssertionError.class.isInstance(throwable)) {
                        error = AssertionError.class.cast(throwable);
                    }
                }
            }
            if (error != null) {
                throw error;
            }
            fail();
        };
    }

    private void withClient(final Consumer<WebTarget> consumer) {
        final Client client = ClientBuilder.newClient();
        try {
            consumer.accept(client.target(base()));
        } finally {
            client.close();
        }
    }

    private String base() {
        return "http://localhost:" + SERVER.getConfiguration().getHttpPort();
    }
}
