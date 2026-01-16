package me.internalizable.numdrassl.api.plugin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a class as a Numdrassl plugin.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * @Plugin(
 *     id = "my-plugin",
 *     name = "My Plugin",
 *     version = "1.0.0",
 *     authors = {"Author1", "Author2"},
 *     description = "A sample plugin for Numdrassl"
 * )
 * public class MyPlugin {
 *
 *     @Inject
 *     private ProxyServer proxy;
 *
 *     @Subscribe
 *     public void onProxyInit(ProxyInitializeEvent event) {
 *         // Plugin initialization
 *     }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Plugin {

    /**
     * The unique ID of this plugin.
     * Should be lowercase and may contain alphanumeric characters, dashes, and underscores.
     *
     * @return the plugin ID
     */
    String id();

    /**
     * The display name of this plugin.
     * Defaults to the ID if not specified.
     *
     * @return the plugin name
     */
    String name() default "";

    /**
     * The version of this plugin.
     *
     * @return the version string
     */
    String version() default "";

    /**
     * A description of this plugin.
     *
     * @return the description
     */
    String description() default "";

    /**
     * The authors of this plugin.
     *
     * @return an array of author names
     */
    String[] authors() default {};

    /**
     * The dependencies of this plugin.
     *
     * @return an array of plugin IDs this plugin depends on
     */
    String[] dependencies() default {};

    /**
     * The optional dependencies of this plugin.
     *
     * @return an array of plugin IDs this plugin optionally depends on
     */
    String[] softDependencies() default {};
}

