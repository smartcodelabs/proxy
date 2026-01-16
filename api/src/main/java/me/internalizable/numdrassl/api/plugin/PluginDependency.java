package me.internalizable.numdrassl.api.plugin;

import javax.annotation.Nonnull;

/**
 * Represents a plugin dependency.
 */
public final class PluginDependency {

    private final String id;
    private final boolean optional;

    public PluginDependency(@Nonnull String id, boolean optional) {
        this.id = id;
        this.optional = optional;
    }

    /**
     * Get the ID of the required plugin.
     *
     * @return the dependency ID
     */
    @Nonnull
    public String getId() {
        return id;
    }

    /**
     * Check if this dependency is optional.
     *
     * @return true if optional
     */
    public boolean isOptional() {
        return optional;
    }

    @Override
    public String toString() {
        return "PluginDependency{id='" + id + "', optional=" + optional + "}";
    }
}

