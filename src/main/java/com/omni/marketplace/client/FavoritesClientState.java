package com.omni.marketplace.client;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class FavoritesClientState {
    private static final Set<String> FAVORITES = new HashSet<>();

    public static synchronized boolean isFavorite(String itemId) {
        if (itemId == null) return false;
        return FAVORITES.contains(itemId);
    }

    public static synchronized boolean toggleFavorite(String itemId) {
        if (itemId == null) return false;
        if (FAVORITES.contains(itemId)) {
            FAVORITES.remove(itemId);
            return false;
        } else {
            FAVORITES.add(itemId);
            return true;
        }
    }

    public static synchronized void setFavorites(Collection<String> itemIds) {
        FAVORITES.clear();
        if (itemIds != null) {
            FAVORITES.addAll(itemIds);
        }
    }

    public static synchronized Set<String> getFavorites() {
        return Collections.unmodifiableSet(new HashSet<>(FAVORITES));
    }
}
