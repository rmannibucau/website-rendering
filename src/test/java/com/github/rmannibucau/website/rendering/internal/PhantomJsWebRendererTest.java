package com.github.rmannibucau.website.rendering.internal;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.stream.Stream;

import javax.inject.Inject;

import com.github.rmannibucau.website.rendering.api.WebRenderer;

import org.apache.meecrowave.junit.InjectRule;
import org.apache.meecrowave.junit.MeecrowaveRule;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

public class PhantomJsWebRendererTest {
    @ClassRule
    public static final MeecrowaveRule RULE = new MeecrowaveRule();

    @Rule
    public final InjectRule inject = new InjectRule(this);

    @Inject
    private WebRenderer renderer;

    @Test
    public void renderHtml() {
        /* angular 4
        final String capture = renderer.capture("https://rmannibucau.metawerx.net/post/tomee-microprofile-2.0");
        // meta is updated once the post is loaded
        assertTrue(capture, capture.contains("<meta name=\"description\" content=\"Microprofile is fully implemented at Apache but TomEE doesn't provide yet an implementation. This is not a blocker to use it and this post will show you how!\">"));
        // post content
        assertTrue(capture, capture.contains("<li>Use TomEE Maven Plugin to bundle a custom TomEE MP distro</li>"));
        */
        final String capture = renderer.capture("http://localhost:" + RULE.getConfiguration().getHttpPort() + "/foo.html");
        assertTrue(capture, capture.contains("<h1>HTML</h1>"));
    }

    @Test
    public void seleniumAPIs() {
        Stream.of(WebDriver.class, TakesScreenshot.class, JavascriptExecutor.class)
                .forEach(it -> renderer.withDriver(it, Assert::assertNotNull));
    }
}
