package com.github.rmannibucau.website.rendering.api;

/**
 * Provide a way to render an url.
 */
public interface WebRenderer {
    /**
     * Returns a selenium API.
     *
     * @param expectedApi the selenium API to retrieve.
     * @param <T> the expected type.
     * @return the instance if possible or it will throw an exception.
     */
    <T> T getDriverAs(Class<T> expectedApi);

    /**
     * Capture a public page.
     *
     * @param url the url to capture.
     * @return the HTML of the captured page.
     */
    String capture(String url);
}
