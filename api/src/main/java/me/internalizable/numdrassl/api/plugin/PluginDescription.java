package me.internalizable.numdrassl.api.plugin;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;

/**
 * Describes a plugin's metadata.
 */
public interface PluginDescription {

    /**
     * Get the plugin's unique ID.
     *
     * @return the plugin ID
     */
    @Nonnull
    String getId();

    /**
     * Get the plugin's display name.
     *
     * @return the plugin name, or the ID if not specified
     */
    @Nonnull
    String getName();

    /**
     * Get the plugin's version.
     *
     * @return the version, or empty if not specified
     */
    @Nonnull
    Optional<String> getVersion();

    /**
     * Get the plugin's description.
     *
     * @return the description, or empty if not specified
     */
    @Nonnull
    Optional<String> getDescription();

    /**
     * Get the plugin's authors.
     *
     * @return the list of authors
     */
    @Nonnull
    List<String> getAuthors();

    /**
     * Get the plugin's dependencies.
     *
     * @return the list of dependencies
     */
    @Nonnull
    List<PluginDependency> getDependencies();

    /**
     * Get the plugin's main class name.
     *
     * @return the main class name, or empty if not specified
     */
    @Nonnull
    Optional<String> getMainClass();
}

