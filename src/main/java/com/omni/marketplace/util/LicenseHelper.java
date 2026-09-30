package com.omni.marketplace.util;

import com.omni.marketplace.db.DatabaseManager;
import net.minecraft.world.entity.player.Player;

public class LicenseHelper {

    /**
     * Checks if the player is licensed to sell on the market and summon guild merchants.
     * Player is licensed if their license is recorded permanently in the SQLite database (by consuming the license deed).
     */
    public static boolean hasLicense(Player player) {
        if (player == null) return false;

        try {
            if (DatabaseManager.getInstance().isPlayerLicensed(player.getUUID())) {
                return true;
            }
        } catch (Exception ignored) {}

        return false;
    }
}

