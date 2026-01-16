package me.internalizable.numdrassl.api.plugin;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.Optional;

/**
 * A wrapper around a loaded plugin instance.
 */
public interface PluginContainer {

    /**
     * Get the plugin's description.
     *
     * @return the plugin description
     */
    @Nonnull
    PluginDescription getDescription();

    /**
     * Get the plugin instance.
     *
     * @return the plugin instance, or empty if the plugin has no main class
     */
    @Nonnull
    Optional<Object> getInstance();

    /**
     * Get the plugin's data directory.
     * This is where the plugin should store configuration and data files.
     *
     * @return the data directory
     */
    @Nonnull
    Path getDataDirectory();
}

