package me.internalizable.numdrassl.api.plugin;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

/**
 * Manages loading and retrieving plugins.
 */
public interface PluginManager {

    /**
     * Get a plugin by its ID.
     *
     * @param id the plugin ID
     * @return the plugin container, or empty if not found
     */
    @Nonnull
    Optional<PluginContainer> getPlugin(@Nonnull String id);

    /**
     * Get all loaded plugins.
     *
     * @return an unmodifiable collection of all plugins
     */
    @Nonnull
    Collection<PluginContainer> getPlugins();

    /**
     * Check if a plugin is loaded.
     *
     * @param id the plugin ID
     * @return true if the plugin is loaded
     */
    boolean isLoaded(@Nonnull String id);

    /**
     * Get the plugin directory where plugins are loaded from.
     *
     * @return the plugins directory
     */
    @Nonnull
    Path getPluginsDirectory();

    /**
     * Add a path to scan for plugins.
     *
     * @param path the path to add
     */
    void addPluginPath(@Nonnull Path path);
}

