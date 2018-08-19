package com.github.rmannibucau.website.rendering.api;

import java.util.function.Consumer;

/**
 * Provide a way to render an url.
 */
public interface WebRenderer {
    /**
     * Provides a selenium API.
     *
     * @param expectedApi the selenium API to retrieve.
     * @param consumer the task to execute with the extracted API.
     * @param <T> the expected API type.
     */
    <T> void withDriver(Class<T> expectedApi, Consumer<T> consumer);

    /**
     * Capture a public page.
     *
     * @param url the url to capture.
     * @return the HTML of the captured page.
     */
    String capture(String url);
}
