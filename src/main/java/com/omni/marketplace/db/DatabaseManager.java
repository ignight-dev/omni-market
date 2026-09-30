package com.omni.marketplace.db;

import com.omni.marketplace.catalog.CategoryDef;
import com.omni.marketplace.catalog.CategoryDef.ItemClassification;
import com.omni.marketplace.config.MarketConfig;
import com.omni.marketplace.db.model.MarketModels.*;
import com.omni.marketplace.network.MarketPackets.ItemAnalytics;
import com.omni.marketplace.util.CurrencyUtils;
import com.omni.marketplace.util.DiscordWebhookHelper;
import com.omni.marketplace.util.InventoryUtils;
import com.omni.marketplace.util.ItemSerializer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class DatabaseManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseManager.class);
    private static final DatabaseManager INSTANCE = new DatabaseManager();

    private Connection connection;
    private final ReentrantLock lock = new ReentrantLock();

    public static DatabaseManager getInstance() {
        return INSTANCE;
    }

    private DatabaseManager() {}

    public void initialize(Path worldDirectory) {
        lock.lock();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }

            Path dbDir = worldDirectory.resolve("data");
            Files.createDirectories(dbDir);
            Path dbFile = dbDir.resolve("omni_marketplace.db");

            String url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
            LOGGER.info("Connecting to Omni Marketplace database at: {}", url);
            connection = DriverManager.getConnection(url);

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode = WAL;");
                stmt.execute("PRAGMA synchronous = NORMAL;");

                // Accounts
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS accounts (
                        uuid TEXT PRIMARY KEY,
                        player_name TEXT NOT NULL,
                        copper_balance BIGINT NOT NULL DEFAULT 0,
                        is_licensed INTEGER NOT NULL DEFAULT 0
                    );
                """);
                try {
                    stmt.execute("ALTER TABLE accounts ADD COLUMN is_licensed INTEGER NOT NULL DEFAULT 0;");
                } catch (SQLException ignored) {
                    // Column already exists
                }
                try {
                    stmt.execute("ALTER TABLE accounts ADD COLUMN bank_rows INTEGER NOT NULL DEFAULT 2;");
                } catch (SQLException ignored) {
                    // Column already exists
                }
                try {
                    stmt.execute("ALTER TABLE accounts ADD COLUMN pocket_emeralds INTEGER NOT NULL DEFAULT 0;");
                } catch (SQLException ignored) {
                    // Column already exists
                }
                try {
                    stmt.execute("ALTER TABLE accounts ADD COLUMN vault_emeralds INTEGER NOT NULL DEFAULT 0;");
                } catch (SQLException ignored) {
                    // Column already exists
                }
                try {
                    stmt.execute("UPDATE accounts SET bank_rows = 3 WHERE bank_rows < 3 AND uuid IN (SELECT DISTINCT player_uuid FROM bank_vault_items WHERE slot_index >= 18);");
                } catch (SQLException ignored) {
                }

                // Listings (Sell Asks)
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS listings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        seller_uuid TEXT NOT NULL,
                        seller_name TEXT NOT NULL,
                        item_id TEXT NOT NULL,
                        item_nbt TEXT,
                        price_copper BIGINT NOT NULL,
                        quantity INTEGER NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """);

                // Buy Orders (Buy Bids)
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS buy_orders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        buyer_uuid TEXT NOT NULL,
                        buyer_name TEXT NOT NULL,
                        item_id TEXT NOT NULL,
                        item_nbt TEXT,
                        price_copper BIGINT NOT NULL,
                        quantity INTEGER NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """);

                // Collection Vault (Items)
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS vault_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL,
                        item_id TEXT NOT NULL,
                        item_nbt TEXT,
                        quantity INTEGER NOT NULL,
                        source TEXT NOT NULL
                    );
                """);

                // Collection Vault (Currency Escrow/Earnings)
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS vault_currency (
                        player_uuid TEXT PRIMARY KEY,
                        copper_amount BIGINT NOT NULL DEFAULT 0
                    );
                """);

                // Transaction History
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS trade_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        item_id TEXT NOT NULL,
                        item_name TEXT,
                        price_copper BIGINT NOT NULL,
                        quantity INTEGER NOT NULL,
                        fee_copper BIGINT NOT NULL,
                        buyer_uuid TEXT,
                        seller_uuid TEXT,
                        trade_type TEXT,
                        timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """);

                // Discovered Items (Tracks items that have been listed at least once by players)
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS discovered_items (
                        item_id TEXT PRIMARY KEY,
                        first_listed_by TEXT,
                        first_listed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    );
                """);

                // Player Bank Vault Storage (27 slots per player instance, SQLite backed)
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS bank_vault_items (
                        player_uuid TEXT NOT NULL,
                        slot_index INTEGER NOT NULL,
                        item_id TEXT NOT NULL,
                        item_nbt TEXT,
                        quantity INTEGER NOT NULL,
                        PRIMARY KEY (player_uuid, slot_index)
                    );
                """);

                // Player Watchlist / Favorites
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS player_favorites (
                        player_uuid TEXT NOT NULL,
                        item_id TEXT NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (player_uuid, item_id)
                    );
                """);

                // Create Indexes
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_listings_item ON listings(item_id, price_copper ASC);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_buy_orders_item ON buy_orders(item_id, price_copper DESC);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_vault_player ON vault_items(player_uuid);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_bank_player ON bank_vault_items(player_uuid);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_fav ON player_favorites(player_uuid);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_trade_hist_item_time ON trade_history(item_id, timestamp);");
            }
            LOGGER.info("Omni Marketplace SQLite database tables initialized successfully.");
            seedBaselineLiquidity();
        } catch (Exception e) {
            LOGGER.error("Failed to initialize Omni Marketplace database", e);
        } finally {
            lock.unlock();
        }
    }

    public void close() {
        lock.lock();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                LOGGER.info("Omni Marketplace database connection closed.");
            }
        } catch (SQLException e) {
            LOGGER.error("Error closing database connection", e);
        } finally {
            lock.unlock();
        }
    }

    /* ========================================================================= */
    /* Account & Wallet Operations                                               */
    /* ========================================================================= */

    public AccountSummary getOrCreateAccount(UUID uuid, String name) {
        lock.lock();
        try {
            String selectSql = "SELECT copper_balance FROM accounts WHERE uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(selectSql)) {
                pstmt.setString(1, uuid.toString());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    long balance = rs.getLong("copper_balance");
                    return buildAccountSummary(uuid, name, balance);
                }
            }

            // Insert new account
            String insertSql = "INSERT INTO accounts (uuid, player_name, copper_balance) VALUES (?, ?, 0)";
            try (PreparedStatement pstmt = connection.prepareStatement(insertSql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, name);
                pstmt.executeUpdate();
            }

            return buildAccountSummary(uuid, name, 0L);
        } catch (SQLException e) {
            LOGGER.error("Error in getOrCreateAccount for {}", uuid, e);
            return new AccountSummary(uuid, name, 0L, 0, 0L);
        } finally {
            lock.unlock();
        }
    }

    public boolean isPlayerLicensed(UUID uuid) {
        lock.lock();
        try {
            String sql = "SELECT is_licensed FROM accounts WHERE uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("is_licensed") == 1;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            LOGGER.error("Error checking isPlayerLicensed for {}", uuid, e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    public void setPlayerLicensed(UUID uuid, String name, boolean licensed) {
        lock.lock();
        try {
            getOrCreateAccount(uuid, name);
            String sql = "UPDATE accounts SET is_licensed = ? WHERE uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, licensed ? 1 : 0);
                pstmt.setString(2, uuid.toString());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.error("Error setting isPlayerLicensed for {}", uuid, e);
        } finally {
            lock.unlock();
        }
    }

    public int resetAllPlayerLicenses() {
        lock.lock();
        try {
            String sql = "UPDATE accounts SET is_licensed = 0";
            try (Statement stmt = connection.createStatement()) {
                int affected = stmt.executeUpdate(sql);
                LOGGER.info("Reset merchant licenses for {} accounts in database.", affected);
                return affected;
            }
        } catch (SQLException e) {
            LOGGER.error("Error resetting all player licenses", e);
            return 0;
        } finally {
            lock.unlock();
        }
    }

    public boolean resetPlayerLicenseByName(String name) {
        lock.lock();
        try {
            String sql = "UPDATE accounts SET is_licensed = 0 WHERE LOWER(player_name) = LOWER(?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, name);
                int affected = pstmt.executeUpdate();
                LOGGER.info("Reset merchant license for account name '{}', affected: {}", name, affected);
                return affected > 0;
            }
        } catch (SQLException e) {
            LOGGER.error("Error resetting license by name for {}", name, e);
            return false;
        } finally {
            lock.unlock();
        }
    }


    private AccountSummary buildAccountSummary(UUID uuid, String name, long copperBalance) throws SQLException {
        int vaultItemsCount = 0;
        long vaultCopper = 0L;

        String vaultItemSql = "SELECT SUM(quantity) as total_items FROM vault_items WHERE player_uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(vaultItemSql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                vaultItemsCount = rs.getInt("total_items");
            }
        }

        String vaultCoinSql = "SELECT copper_amount FROM vault_currency WHERE player_uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(vaultCoinSql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                vaultCopper = rs.getLong("copper_amount");
            }
        }

        return new AccountSummary(uuid, name, copperBalance, vaultItemsCount, vaultCopper);
    }

    public boolean depositCopper(UUID uuid, long amount) {
        if (amount <= 0) return false;
        lock.lock();
        try {
            String sql = """
                INSERT INTO accounts (uuid, player_name, copper_balance) VALUES (?, 'Player', ?)
                ON CONFLICT(uuid) DO UPDATE SET copper_balance = copper_balance + excluded.copper_balance
            """;
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setLong(2, amount);
                int rows = pstmt.executeUpdate();
                return rows > 0;
            }
        } catch (SQLException e) {
            LOGGER.error("Error depositing copper for {}", uuid, e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean withdrawCopper(UUID uuid, long amount) {
        if (amount <= 0) return false;
        lock.lock();
        try {
            String sql = "UPDATE accounts SET copper_balance = copper_balance - ? WHERE uuid = ? AND copper_balance >= ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, amount);
                pstmt.setString(2, uuid.toString());
                pstmt.setLong(3, amount);
                int rows = pstmt.executeUpdate();
                return rows > 0;
            }
        } catch (SQLException e) {
            LOGGER.error("Error withdrawing copper for {}", uuid, e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    /* ========================================================================= */
    /* GW2 Trading Engine: Place Sell Listing (Ask)                              */
    /* ========================================================================= */

    public record ListingResult(boolean success, String message, int remainingQuantity) {}

    public ListingResult createSellListing(UUID sellerUuid, String sellerName, String itemId, String itemNbt,
                                          long unitPrice, int quantity) {
        if (unitPrice <= 0 || quantity <= 0) {
            return new ListingResult(false, "Invalid price or quantity.", quantity);
        }

        if (CategoryDef.isEmeraldCurrency(itemId)) {
            return new ListingResult(false, "Emeralds cannot be traded on the open market. Use the Currency Exchange tab.", quantity);
        }

        lock.lock();
        try {
            long listingFee = CurrencyUtils.calculateListingFee(unitPrice, quantity);
            if (!withdrawCopper(sellerUuid, listingFee)) {
                return new ListingResult(false, "Insufficient copper to pay the 5% listing fee (" + CurrencyUtils.format(listingFee) + ").", quantity);
            }

            int remaining = quantity;

            // Check if there are matching buy orders (instant match!)
            // Exclude seller's own buy orders to prevent self-trading / fee burn
            String matchSql = "SELECT id, buyer_uuid, buyer_name, price_copper, quantity FROM buy_orders " +
                    "WHERE item_id = ? AND price_copper >= ? AND buyer_uuid != ? ORDER BY price_copper DESC, id ASC";

            try (PreparedStatement matchStmt = connection.prepareStatement(matchSql)) {
                matchStmt.setString(1, itemId);
                matchStmt.setLong(2, unitPrice);
                matchStmt.setString(3, sellerUuid.toString());
                ResultSet rs = matchStmt.executeQuery();

                while (rs.next() && remaining > 0) {
                    long buyOrderId = rs.getLong("id");
                    UUID buyerUuid = UUID.fromString(rs.getString("buyer_uuid"));
                    long buyerPrice = rs.getLong("price_copper");
                    int buyerQty = rs.getInt("quantity");

                    int tradeQty = Math.min(remaining, buyerQty);
                    long tradeRevenue = buyerPrice * (long) tradeQty;
                    long exchangeFee = CurrencyUtils.calculateExchangeFee(buyerPrice, tradeQty);
                    long sellerNet = tradeRevenue - exchangeFee;

                    // Deliver items to buyer's vault
                    addVaultItem(buyerUuid, itemId, itemNbt, tradeQty, "Buy Order Filled");

                    // Deliver seller net earnings to seller's vault
                    addVaultCurrency(sellerUuid, sellerNet);

                    // Update or remove buy order
                    if (buyerQty <= tradeQty) {
                        deleteBuyOrder(buyOrderId);
                    } else {
                        updateBuyOrderQuantity(buyOrderId, buyerQty - tradeQty);
                    }

                    // Record trade
                    recordTrade(itemId, buyerPrice, tradeQty, exchangeFee, buyerUuid, sellerUuid, "MATCH_SELL_INTO_BID");
                    remaining -= tradeQty;
                }
            }

            // If any quantity is not matched, place it on the market listings
            if (remaining > 0) {
                String insertListing = "INSERT INTO listings (seller_uuid, seller_name, item_id, item_nbt, price_copper, quantity) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement pstmt = connection.prepareStatement(insertListing)) {
                    pstmt.setString(1, sellerUuid.toString());
                    pstmt.setString(2, sellerName);
                    pstmt.setString(3, itemId);
                    pstmt.setString(4, itemNbt);
                    pstmt.setLong(5, unitPrice);
                    pstmt.setInt(6, remaining);
                    pstmt.executeUpdate();
                }
            }

            recordItemDiscovery(itemId, sellerName);

            return new ListingResult(true, "Listing placed successfully! Listing fee paid: " + CurrencyUtils.format(listingFee), remaining);
        } catch (SQLException e) {
            LOGGER.error("Error creating sell listing", e);
            return new ListingResult(false, "Database error creating listing.", quantity);
        } finally {
            lock.unlock();
        }
    }

    /* ========================================================================= */
    /* GW2 Trading Engine: Place Buy Order (Bid)                                 */
    /* ========================================================================= */

    public ListingResult createBuyOrder(UUID buyerUuid, String buyerName, String itemId, String itemNbt,
                                       long unitPrice, int quantity) {
        if (unitPrice <= 0 || quantity <= 0) {
            return new ListingResult(false, "Invalid price or quantity.", quantity);
        }

        if (CategoryDef.isEmeraldCurrency(itemId)) {
            return new ListingResult(false, "Emeralds cannot be traded on the open market. Use the Currency Exchange tab.", quantity);
        }

        if (CategoryDef.isSpecialItem(itemId) && !isItemDiscovered(itemId)) {
            return new ListingResult(false, "Special items can only have buy orders placed after another player has listed them on the market at least once.", quantity);
        }

        lock.lock();
        try {
            long totalCost = unitPrice * (long) quantity;
            if (!withdrawCopper(buyerUuid, totalCost)) {
                return new ListingResult(false, "Insufficient copper to escrow buy order (" + CurrencyUtils.format(totalCost) + ").", quantity);
            }

            int remaining = quantity;

            // Check if there are matching sell listings (instant match!)
            // Exclude buyer's own listings to prevent self-trading / fee burn
            String matchSql = "SELECT id, seller_uuid, seller_name, price_copper, quantity, item_nbt FROM listings " +
                    "WHERE item_id = ? AND price_copper <= ? AND seller_uuid != ? ORDER BY price_copper ASC, id ASC";

            try (PreparedStatement matchStmt = connection.prepareStatement(matchSql)) {
                matchStmt.setString(1, itemId);
                matchStmt.setLong(2, unitPrice);
                matchStmt.setString(3, buyerUuid.toString());
                ResultSet rs = matchStmt.executeQuery();

                while (rs.next() && remaining > 0) {
                    long listingId = rs.getLong("id");
                    UUID sellerUuid = UUID.fromString(rs.getString("seller_uuid"));
                    long sellPrice = rs.getLong("price_copper");
                    int sellQty = rs.getInt("quantity");
                    String actualNbt = rs.getString("item_nbt");

                    int tradeQty = Math.min(remaining, sellQty);
                    long tradeCost = sellPrice * (long) tradeQty;
                    long exchangeFee = CurrencyUtils.calculateExchangeFee(sellPrice, tradeQty);
                    long sellerNet = tradeCost - exchangeFee;

                    // If ask was cheaper than buyer's maximum unitPrice, refund difference to buyer's vault
                    long difference = (unitPrice - sellPrice) * (long) tradeQty;
                    if (difference > 0) {
                        addVaultCurrency(buyerUuid, difference);
                    }

                    // Deliver items to buyer's vault
                    addVaultItem(buyerUuid, itemId, actualNbt, tradeQty, "Instant Buy Matched");

                    // Deliver seller net earnings to seller's vault
                    addVaultCurrency(sellerUuid, sellerNet);

                    // Update or remove listing
                    if (sellQty <= tradeQty) {
                        deleteListing(listingId);
                    } else {
                        updateListingQuantity(listingId, sellQty - tradeQty);
                    }

                    // Record trade
                    recordTrade(itemId, sellPrice, tradeQty, exchangeFee, buyerUuid, sellerUuid, "MATCH_BID_INTO_ASK");
                    remaining -= tradeQty;
                }
            }

            // If remaining quantity, store in buy_orders table
            if (remaining > 0) {
                String insertBuyOrder = "INSERT INTO buy_orders (buyer_uuid, buyer_name, item_id, item_nbt, price_copper, quantity) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement pstmt = connection.prepareStatement(insertBuyOrder)) {
                    pstmt.setString(1, buyerUuid.toString());
                    pstmt.setString(2, buyerName);
                    pstmt.setString(3, itemId);
                    pstmt.setString(4, itemNbt);
                    pstmt.setLong(5, unitPrice);
                    pstmt.setInt(6, remaining);
                    pstmt.executeUpdate();
                }
            }

            return new ListingResult(true, "Buy order placed successfully for " + quantity + " units.", remaining);
        } catch (SQLException e) {
            LOGGER.error("Error creating buy order", e);
            return new ListingResult(false, "Database error creating buy order.", quantity);
        } finally {
            lock.unlock();
        }
    }

    /* ========================================================================= */
    /* Instant Buy / Instant Sell Helpers                                        */
    /* ========================================================================= */

    public ListingResult instantBuyFromListing(UUID buyerUuid, String buyerName, long listingId, int quantity) {
        if (quantity <= 0) {
            return new ListingResult(false, "Invalid quantity.", 0);
        }

        lock.lock();
        try {
            String selectSql = "SELECT seller_uuid, item_id, item_nbt, price_copper, quantity FROM listings WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(selectSql)) {
                pstmt.setLong(1, listingId);
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) {
                    return new ListingResult(false, "Listing no longer available.", 0);
                }

                UUID sellerUuid = UUID.fromString(rs.getString("seller_uuid"));
                if (sellerUuid.equals(buyerUuid)) {
                    return new ListingResult(false, "You cannot buy your own listing. Cancel it in Guild Ledgers instead.", 0);
                }

                String itemId = rs.getString("item_id");
                String itemNbt = rs.getString("item_nbt");
                long unitPrice = rs.getLong("price_copper");
                int availableQty = rs.getInt("quantity");

                int tradeQty = Math.min(quantity, availableQty);
                long totalCost = unitPrice * (long) tradeQty;

                if (!withdrawCopper(buyerUuid, totalCost)) {
                    return new ListingResult(false, "Insufficient copper to instant buy (" + CurrencyUtils.format(totalCost) + ").", 0);
                }

                long exchangeFee = CurrencyUtils.calculateExchangeFee(unitPrice, tradeQty);
                long sellerNet = totalCost - exchangeFee;

                // Deliver
                addVaultItem(buyerUuid, itemId, itemNbt, tradeQty, "Instant Buy");
                addVaultCurrency(sellerUuid, sellerNet);

                // Update listing
                if (availableQty <= tradeQty) {
                    deleteListing(listingId);
                } else {
                    updateListingQuantity(listingId, availableQty - tradeQty);
                }

                recordTrade(itemId, unitPrice, tradeQty, exchangeFee, buyerUuid, sellerUuid, "INSTANT_BUY");
                return new ListingResult(true, "Instant bought " + tradeQty + " items for " + CurrencyUtils.format(totalCost) + "!", quantity - tradeQty);
            }
        } catch (SQLException e) {
            LOGGER.error("Error in instant buy", e);
            return new ListingResult(false, "Database error during instant buy.", quantity);
        } finally {
            lock.unlock();
        }
    }

    public ListingResult instantSellToBuyOrder(UUID sellerUuid, String sellerName, long buyOrderId, int quantity, String itemNbt) {
        if (quantity <= 0) {
            return new ListingResult(false, "Invalid quantity.", quantity);
        }

        lock.lock();
        try {
            String selectSql = "SELECT buyer_uuid, item_id, price_copper, quantity FROM buy_orders WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(selectSql)) {
                pstmt.setLong(1, buyOrderId);
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) {
                    return new ListingResult(false, "Buy order no longer available.", quantity);
                }

                UUID buyerUuid = UUID.fromString(rs.getString("buyer_uuid"));
                if (buyerUuid.equals(sellerUuid)) {
                    return new ListingResult(false, "You cannot sell to your own buy order. Cancel it in Guild Ledgers instead.", quantity);
                }

                String itemId = rs.getString("item_id");
                long unitPrice = rs.getLong("price_copper");
                int requestedQty = rs.getInt("quantity");

                int tradeQty = Math.min(quantity, requestedQty);
                long totalRevenue = unitPrice * (long) tradeQty;
                long exchangeFee = CurrencyUtils.calculateExchangeFee(unitPrice, tradeQty);
                long sellerNet = totalRevenue - exchangeFee;

                // Deliver
                addVaultItem(buyerUuid, itemId, itemNbt, tradeQty, "Instant Sell Filled");
                addVaultCurrency(sellerUuid, sellerNet);

                // Update buy order
                if (requestedQty <= tradeQty) {
                    deleteBuyOrder(buyOrderId);
                } else {
                    updateBuyOrderQuantity(buyOrderId, requestedQty - tradeQty);
                }

                recordTrade(itemId, unitPrice, tradeQty, exchangeFee, buyerUuid, sellerUuid, "INSTANT_SELL");
                return new ListingResult(true, "Instant sold " + tradeQty + " items for net profit of " + CurrencyUtils.format(sellerNet) + "!", quantity - tradeQty);
            }
        } catch (SQLException e) {
            LOGGER.error("Error in instant sell", e);
            return new ListingResult(false, "Database error during instant sell.", quantity);
        } finally {
            lock.unlock();
        }
    }

    public ListingResult instantSellByItem(UUID sellerUuid, String sellerName, String itemId, int quantity, String itemNbt) {
        if (quantity <= 0) {
            return new ListingResult(false, "Invalid quantity.", 0);
        }

        if (CategoryDef.isEmeraldCurrency(itemId)) {
            return new ListingResult(false, "Emeralds cannot be traded on the open market.", 0);
        }

        lock.lock();
        try {
            String selectSql = "SELECT id, buyer_uuid, price_copper, quantity FROM buy_orders WHERE item_id = ? AND buyer_uuid != ? ORDER BY price_copper DESC, id ASC";
            try (PreparedStatement pstmt = connection.prepareStatement(selectSql)) {
                pstmt.setString(1, itemId);
                pstmt.setString(2, sellerUuid.toString());
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) {
                    return new ListingResult(false, "No active buy orders found for this item.", 0);
                }

                int remaining = quantity;
                long totalSellerNet = 0L;

                do {
                    long buyOrderId = rs.getLong("id");
                    UUID buyerUuid = UUID.fromString(rs.getString("buyer_uuid"));
                    long unitPrice = rs.getLong("price_copper");
                    int requestedQty = rs.getInt("quantity");

                    int tradeQty = Math.min(remaining, requestedQty);
                    long totalRevenue = unitPrice * (long) tradeQty;
                    long exchangeFee = CurrencyUtils.calculateExchangeFee(unitPrice, tradeQty);
                    long sellerNet = totalRevenue - exchangeFee;
                    totalSellerNet += sellerNet;

                    addVaultItem(buyerUuid, itemId, itemNbt, tradeQty, "Instant Sell Filled");
                    addVaultCurrency(sellerUuid, sellerNet);

                    if (requestedQty <= tradeQty) {
                        deleteBuyOrder(buyOrderId);
                    } else {
                        updateBuyOrderQuantity(buyOrderId, requestedQty - tradeQty);
                    }

                    recordTrade(itemId, unitPrice, tradeQty, exchangeFee, buyerUuid, sellerUuid, "INSTANT_SELL");
                    remaining -= tradeQty;
                } while (rs.next() && remaining > 0);

                int soldCount = quantity - remaining;
                return new ListingResult(true, "Instant sold " + soldCount + " items for a net payout of " + CurrencyUtils.format(totalSellerNet) + " in your Collection Vault!", remaining);
            }
        } catch (SQLException e) {
            LOGGER.error("Error in instantSellByItem", e);
            return new ListingResult(false, "Database error during instant sell.", 0);
        } finally {
            lock.unlock();
        }
    }

    public ListingResult instantBuyByItem(UUID buyerUuid, String buyerName, String itemId, int quantity) {
        if (quantity <= 0) {
            return new ListingResult(false, "Invalid quantity.", 0);
        }

        if (CategoryDef.isEmeraldCurrency(itemId)) {
            return new ListingResult(false, "Emeralds cannot be traded on the open market.", 0);
        }

        lock.lock();
        try {
            String selectSql = "SELECT id, seller_uuid, item_nbt, price_copper, quantity FROM listings WHERE item_id = ? AND seller_uuid != ? ORDER BY price_copper ASC, id ASC";
            try (PreparedStatement pstmt = connection.prepareStatement(selectSql)) {
                pstmt.setString(1, itemId);
                pstmt.setString(2, buyerUuid.toString());
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) {
                    return new ListingResult(false, "No active listings found for this item.", 0);
                }

                int remaining = quantity;
                int boughtCount = 0;
                long totalSpent = 0L;

                do {
                    long listingId = rs.getLong("id");
                    UUID sellerUuid = UUID.fromString(rs.getString("seller_uuid"));
                    String itemNbt = rs.getString("item_nbt");
                    long unitPrice = rs.getLong("price_copper");
                    int availableQty = rs.getInt("quantity");

                    int tradeQty = Math.min(remaining, availableQty);
                    long totalCost = unitPrice * (long) tradeQty;

                    if (!withdrawCopper(buyerUuid, totalCost)) {
                        if (boughtCount == 0) {
                            return new ListingResult(false, "Insufficient funds to instant buy (" + CurrencyUtils.format(totalCost) + ").", 0);
                        }
                        break;
                    }

                    totalSpent += totalCost;
                    long exchangeFee = CurrencyUtils.calculateExchangeFee(unitPrice, tradeQty);
                    long sellerNet = totalCost - exchangeFee;

                    addVaultItem(buyerUuid, itemId, itemNbt, tradeQty, "Instant Buy");
                    addVaultCurrency(sellerUuid, sellerNet);

                    if (availableQty <= tradeQty) {
                        deleteListing(listingId);
                    } else {
                        updateListingQuantity(listingId, availableQty - tradeQty);
                    }

                    recordTrade(itemId, unitPrice, tradeQty, exchangeFee, buyerUuid, sellerUuid, "INSTANT_BUY");
                    remaining -= tradeQty;
                    boughtCount += tradeQty;
                } while (rs.next() && remaining > 0);

                return new ListingResult(true, "Instant bought " + boughtCount + " items for " + CurrencyUtils.format(totalSpent) + "! Pick them up in your Collection Vault.", remaining);
            }
        } catch (SQLException e) {
            LOGGER.error("Error in instantBuyByItem", e);
            return new ListingResult(false, "Database error during instant buy.", 0);
        } finally {
            lock.unlock();
        }
    }

    /* ========================================================================= */
    /* Order Cancellation                                                        */
    /* ========================================================================= */

    public boolean cancelListing(UUID playerUuid, long listingId) {
        lock.lock();
        try {
            String selectSql = "SELECT seller_uuid, item_id, item_nbt, quantity FROM listings WHERE id = ? AND seller_uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(selectSql)) {
                pstmt.setLong(1, listingId);
                pstmt.setString(2, playerUuid.toString());
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) return false;

                String itemId = rs.getString("item_id");
                String itemNbt = rs.getString("item_nbt");
                int quantity = rs.getInt("quantity");

                // Return items to vault
                addVaultItem(playerUuid, itemId, itemNbt, quantity, "Listing Cancelled");
                deleteListing(listingId);
                return true;
            }
        } catch (SQLException e) {
            LOGGER.error("Error cancelling listing {}", listingId, e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean cancelBuyOrder(UUID playerUuid, long buyOrderId) {
        lock.lock();
        try {
            String selectSql = "SELECT buyer_uuid, price_copper, quantity FROM buy_orders WHERE id = ? AND buyer_uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(selectSql)) {
                pstmt.setLong(1, buyOrderId);
                pstmt.setString(2, playerUuid.toString());
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) return false;

                long unitPrice = rs.getLong("price_copper");
                int quantity = rs.getInt("quantity");
                long escrowRefund = unitPrice * (long) quantity;

                // Return escrow to vault
                addVaultCurrency(playerUuid, escrowRefund);
                deleteBuyOrder(buyOrderId);
                return true;
            }
        } catch (SQLException e) {
            LOGGER.error("Error cancelling buy order {}", buyOrderId, e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    /* ========================================================================= */
    /* Collection Vault Operations (GW2 Pick-up Delivery Box)                   */
    /* ========================================================================= */

    public List<VaultItemRecord> getVaultItems(UUID playerUuid) {
        lock.lock();
        List<VaultItemRecord> list = new ArrayList<>();
        try {
            String sql = "SELECT id, item_id, item_nbt, quantity, source FROM vault_items WHERE player_uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid.toString());
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    list.add(new VaultItemRecord(
                            rs.getLong("id"),
                            playerUuid,
                            rs.getString("item_id"),
                            rs.getString("item_nbt"),
                            rs.getInt("quantity"),
                            rs.getString("source")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Error fetching vault items for {}", playerUuid, e);
        } finally {
            lock.unlock();
        }
        return list;
    }

    public void addVaultItem(UUID playerUuid, String itemId, String itemNbt, int quantity, String source) throws SQLException {
        String sql = "INSERT INTO vault_items (player_uuid, item_id, item_nbt, quantity, source) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setString(2, itemId);
            pstmt.setString(3, itemNbt);
            pstmt.setInt(4, quantity);
            pstmt.setString(5, source);
            pstmt.executeUpdate();
        }
    }

    public void addVaultCurrency(UUID playerUuid, long amount) throws SQLException {
        if (amount <= 0) return;
        String sql = "INSERT INTO vault_currency (player_uuid, copper_amount) VALUES (?, ?) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET copper_amount = copper_amount + excluded.copper_amount";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setLong(2, amount);
            pstmt.executeUpdate();
        }
    }

    public void deleteVaultItem(long id) throws SQLException {
        String sql = "DELETE FROM vault_items WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        }
    }

    public void updateVaultItemQuantity(long id, int newQuantity) throws SQLException {
        String sql = "UPDATE vault_items SET quantity = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, newQuantity);
            pstmt.setLong(2, id);
            pstmt.executeUpdate();
        }
    }

    public record VaultClaimResult(int itemsClaimed, long coinsClaimed, int itemsRemaining, boolean inventoryFull) {
        public VaultClaimResult(int itemsClaimed, long coinsClaimed) {
            this(itemsClaimed, coinsClaimed, 0, false);
        }
    }

    public VaultClaimResult claimAllVault(ServerPlayer player, HolderLookup.Provider registries) {
        lock.lock();
        try {
            UUID uuid = player.getUUID();
            List<VaultItemRecord> items = getVaultItems(uuid);
            int totalItemsClaimed = 0;
            int totalItemsRemaining = 0;
            boolean inventoryFull = false;

            for (VaultItemRecord record : items) {
                int qty = record.quantity();
                if (qty <= 0) {
                    deleteVaultItem(record.id());
                    continue;
                }

                ItemStack proto = ItemSerializer.deserialize(record.itemNbt(), record.itemId(), 1, registries);
                if (proto.isEmpty()) {
                    LOGGER.warn("Vault item #{} ({}) could not be deserialized. Skipping.", record.id(), record.itemId());
                    totalItemsRemaining += qty;
                    continue;
                }

                int freeSpace = InventoryUtils.getFreeSpaceForItem(player.getInventory(), proto);
                if (freeSpace <= 0) {
                    // Player has no space at all for this item
                    inventoryFull = true;
                    totalItemsRemaining += qty;
                    continue;
                }

                int toGive = Math.min(qty, freeSpace);
                int remainingForRecord = qty - toGive;

                int givenSoFar = 0;
                while (givenSoFar < toGive) {
                    int maxStack = proto.getMaxStackSize();
                    int batchSize = Math.min(toGive - givenSoFar, maxStack);
                    ItemStack giveStack = ItemSerializer.deserialize(record.itemNbt(), record.itemId(), batchSize, registries);
                    boolean added = player.getInventory().add(giveStack);
                    if (!added) {
                        int notAdded = giveStack.getCount();
                        int actuallyAdded = batchSize - notAdded;
                        givenSoFar += actuallyAdded;
                        totalItemsClaimed += actuallyAdded;
                        remainingForRecord += notAdded;
                        inventoryFull = true;
                        break;
                    } else {
                        givenSoFar += batchSize;
                        totalItemsClaimed += batchSize;
                    }
                }

                if (remainingForRecord <= 0) {
                    deleteVaultItem(record.id());
                } else {
                    updateVaultItemQuantity(record.id(), remainingForRecord);
                    inventoryFull = true;
                    totalItemsRemaining += remainingForRecord;
                }
            }

            // Claim currency
            long coinsToClaim = 0L;
            String selectCoinsSql = "SELECT copper_amount FROM vault_currency WHERE player_uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(selectCoinsSql)) {
                pstmt.setString(1, uuid.toString());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    coinsToClaim = rs.getLong("copper_amount");
                }
            }

            if (coinsToClaim > 0) {
                depositCopper(uuid, coinsToClaim);
                String clearCoinsSql = "UPDATE vault_currency SET copper_amount = 0 WHERE player_uuid = ?";
                try (PreparedStatement pstmt = connection.prepareStatement(clearCoinsSql)) {
                    pstmt.setString(1, uuid.toString());
                    pstmt.executeUpdate();
                }
            }

            return new VaultClaimResult(totalItemsClaimed, coinsToClaim, totalItemsRemaining, inventoryFull);
        } catch (SQLException e) {
            LOGGER.error("Error claiming vault for {}", player.getScoreboardName(), e);
            return new VaultClaimResult(0, 0L, 0, false);
        } finally {
            lock.unlock();
        }
    }

    /* ========================================================================= */
    /* Search & Market Catalog Queries                                          */
    /* ========================================================================= */

    public List<MarketSummaryRecord> searchCatalog(String query, int offset, int limit) {
        lock.lock();
        List<MarketSummaryRecord> results = new ArrayList<>();
        try {
            String filter = (query == null || query.isBlank()) ? "%" : "%" + query.trim().toLowerCase() + "%";

            String sql = """
                SELECT 
                    items.item_id,
                    items.item_nbt,
                    COALESCE(l_stats.lowest_sell, 0) AS lowest_sell,
                    COALESCE(b_stats.highest_buy, 0) AS highest_buy,
                    COALESCE(l_stats.supply_count, 0) AS supply_count,
                    COALESCE(b_stats.demand_count, 0) AS demand_count
                FROM (
                    SELECT item_id, item_nbt FROM listings
                    UNION
                    SELECT item_id, item_nbt FROM buy_orders
                ) AS items
                LEFT JOIN (
                    SELECT item_id, MIN(price_copper) AS lowest_sell, SUM(quantity) AS supply_count
                    FROM listings GROUP BY item_id
                ) l_stats ON items.item_id = l_stats.item_id
                LEFT JOIN (
                    SELECT item_id, MAX(price_copper) AS highest_buy, SUM(quantity) AS demand_count
                    FROM buy_orders GROUP BY item_id
                ) b_stats ON items.item_id = b_stats.item_id
                WHERE LOWER(items.item_id) LIKE ?
                ORDER BY COALESCE(l_stats.supply_count, 0) + COALESCE(b_stats.demand_count, 0) DESC, items.item_id ASC
                LIMIT ? OFFSET ?
            """;

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, filter);
                pstmt.setInt(2, limit);
                pstmt.setInt(3, offset);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    String itemId = rs.getString("item_id");
                    String sampleNbt = rs.getString("item_nbt");
                    long lowestSell = rs.getLong("lowest_sell");
                    long highestBuy = rs.getLong("highest_buy");
                    int supply = rs.getInt("supply_count");
                    int demand = rs.getInt("demand_count");

                    results.add(new MarketSummaryRecord(
                            itemId,
                            sampleNbt,
                            itemId,
                            lowestSell,
                            highestBuy,
                            supply,
                            demand
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Error searching catalog", e);
        } finally {
            lock.unlock();
        }
        return results;
    }

    public List<ListingRecord> getListingsForItem(String itemId) {
        lock.lock();
        List<ListingRecord> list = new ArrayList<>();
        try {
            String sql = "SELECT id, seller_uuid, seller_name, item_id, item_nbt, price_copper, quantity, created_at " +
                    "FROM listings WHERE item_id = ? ORDER BY price_copper ASC, id ASC LIMIT 50";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, itemId);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    list.add(new ListingRecord(
                            rs.getLong("id"),
                            UUID.fromString(rs.getString("seller_uuid")),
                            rs.getString("seller_name"),
                            rs.getString("item_id"),
                            rs.getString("item_nbt"),
                            rs.getLong("price_copper"),
                            rs.getInt("quantity"),
                            rs.getString("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Error getting listings for item {}", itemId, e);
        } finally {
            lock.unlock();
        }
        return list;
    }

    public List<BuyOrderRecord> getBuyOrdersForItem(String itemId) {
        lock.lock();
        List<BuyOrderRecord> list = new ArrayList<>();
        try {
            String sql = "SELECT id, buyer_uuid, buyer_name, item_id, item_nbt, price_copper, quantity, created_at " +
                    "FROM buy_orders WHERE item_id = ? ORDER BY price_copper DESC, id ASC LIMIT 50";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, itemId);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    list.add(new BuyOrderRecord(
                            rs.getLong("id"),
                            UUID.fromString(rs.getString("buyer_uuid")),
                            rs.getString("buyer_name"),
                            rs.getString("item_id"),
                            rs.getString("item_nbt"),
                            rs.getLong("price_copper"),
                            rs.getInt("quantity"),
                            rs.getString("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Error getting buy orders for item {}", itemId, e);
        } finally {
            lock.unlock();
        }
        return list;
    }

    public List<ListingRecord> getPlayerListings(UUID playerUuid) {
        lock.lock();
        List<ListingRecord> list = new ArrayList<>();
        try {
            String sql = "SELECT id, seller_uuid, seller_name, item_id, item_nbt, price_copper, quantity, created_at " +
                    "FROM listings WHERE seller_uuid = ? ORDER BY id DESC";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid.toString());
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    list.add(new ListingRecord(
                            rs.getLong("id"),
                            playerUuid,
                            rs.getString("seller_name"),
                            rs.getString("item_id"),
                            rs.getString("item_nbt"),
                            rs.getLong("price_copper"),
                            rs.getInt("quantity"),
                            rs.getString("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Error getting player listings for {}", playerUuid, e);
        } finally {
            lock.unlock();
        }
        return list;
    }

    public List<BuyOrderRecord> getPlayerBuyOrders(UUID playerUuid) {
        lock.lock();
        List<BuyOrderRecord> list = new ArrayList<>();
        try {
            String sql = "SELECT id, buyer_uuid, buyer_name, item_id, item_nbt, price_copper, quantity, created_at " +
                    "FROM buy_orders WHERE buyer_uuid = ? ORDER BY id DESC";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid.toString());
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    list.add(new BuyOrderRecord(
                            rs.getLong("id"),
                            playerUuid,
                            rs.getString("buyer_name"),
                            rs.getString("item_id"),
                            rs.getString("item_nbt"),
                            rs.getLong("price_copper"),
                            rs.getInt("quantity"),
                            rs.getString("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Error getting player buy orders for {}", playerUuid, e);
        } finally {
            lock.unlock();
        }
        return list;
    }

    /* ========================================================================= */
    /* Internal Helpers                                                          */
    /* ========================================================================= */

    private void updateListingQuantity(long id, int newQty) throws SQLException {
        String sql = "UPDATE listings SET quantity = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, newQty);
            pstmt.setLong(2, id);
            pstmt.executeUpdate();
        }
    }

    private void deleteListing(long id) throws SQLException {
        String sql = "DELETE FROM listings WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        }
    }

    private void updateBuyOrderQuantity(long id, int newQty) throws SQLException {
        String sql = "UPDATE buy_orders SET quantity = ? WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, newQty);
            pstmt.setLong(2, id);
            pstmt.executeUpdate();
        }
    }

    private void deleteBuyOrder(long id) throws SQLException {
        String sql = "DELETE FROM buy_orders WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        }
    }

    private void recordTrade(String itemId, long priceCopper, int quantity, long feeCopper,
                            UUID buyerUuid, UUID sellerUuid, String type) {
        try {
            String sql = "INSERT INTO trade_history (item_id, price_copper, quantity, fee_copper, buyer_uuid, seller_uuid, trade_type) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, itemId);
                pstmt.setLong(2, priceCopper);
                pstmt.setInt(3, quantity);
                pstmt.setLong(4, feeCopper);
                pstmt.setString(5, buyerUuid.toString());
                pstmt.setString(6, sellerUuid.toString());
                pstmt.setString(7, type);
                pstmt.executeUpdate();
            }

            long totalValue = priceCopper * (long) quantity;
            if (totalValue >= MarketConfig.get().getMinBroadcastValueCopper()) {
                dispatchHighValueTradeAlert(itemId, priceCopper, quantity, totalValue, buyerUuid, sellerUuid);
            }
        } catch (SQLException e) {
            LOGGER.error("Error recording trade history", e);
        }
    }

    private void dispatchHighValueTradeAlert(String itemId, long unitPrice, int quantity, long totalValue, UUID buyerUuid, UUID sellerUuid) {
        try {
            String buyerName = getPlayerName(buyerUuid);
            String sellerName = getPlayerName(sellerUuid);

            String itemName = itemId;
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                itemName = item.getDescription().getString();
            }

            // 1. Asynchronous Discord Webhook Embed
            DiscordWebhookHelper.sendTradeAlert(itemId, itemName, quantity, unitPrice, totalValue, buyerName, sellerName);

            // 2. In-Game Public Broadcast
            if (MarketConfig.get().isEnableInGameBroadcasts()) {
                net.minecraft.server.MinecraftServer server = com.omni.marketplace.OmniMarketplace.getServer();
                if (server != null) {
                    Component broadcast = Component.literal(String.format(
                        "§6[Trading Post] ⚖ §fLegendary Trade: §e%s §7purchased §a%dx %s §7from §e%s §7for %s§7!",
                        buyerName, quantity, itemName, sellerName, CurrencyUtils.format(totalValue)
                    ));
                    server.getPlayerList().broadcastSystemMessage(broadcast, false);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error dispatching high-value trade alert", e);
        }
    }

    public String getPlayerName(UUID uuid) {
        if (uuid == null) return "System";
        if (uuid.equals(UUID.fromString("00000000-0000-0000-0000-000000000000"))) return "Imperial Guild Exchange";
        String sql = "SELECT player_name FROM accounts WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("player_name");
                }
            }
        } catch (SQLException e) {
            LOGGER.debug("Could not resolve player name for {}", uuid);
        }
        return "Citizen";
    }

    public ItemAnalytics getItemAnalytics(String itemId) {
        lock.lock();
        try {
            int vol24 = 0;
            long min24 = 0L;
            long max24 = 0L;
            long avg24 = 0L;

            String sql24 = """
                SELECT 
                    COALESCE(SUM(quantity), 0) AS vol,
                    COALESCE(MIN(price_copper), 0) AS min_price,
                    COALESCE(MAX(price_copper), 0) AS max_price,
                    COALESCE(AVG(price_copper), 0) AS avg_price
                FROM trade_history
                WHERE item_id = ? AND timestamp >= datetime('now', '-24 hours')
            """;
            try (PreparedStatement pstmt = connection.prepareStatement(sql24)) {
                pstmt.setString(1, itemId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        vol24 = rs.getInt("vol");
                        min24 = rs.getLong("min_price");
                        max24 = rs.getLong("max_price");
                        avg24 = Math.round(rs.getDouble("avg_price"));
                    }
                }
            }

            long lastTraded = 0L;
            String sqlLast = "SELECT price_copper FROM trade_history WHERE item_id = ? ORDER BY id DESC LIMIT 1";
            try (PreparedStatement pstmt = connection.prepareStatement(sqlLast)) {
                pstmt.setString(1, itemId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        lastTraded = rs.getLong("price_copper");
                    }
                }
            }

            int totalVol = 0;
            String sqlTotal = "SELECT COALESCE(SUM(quantity), 0) AS tot FROM trade_history WHERE item_id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sqlTotal)) {
                pstmt.setString(1, itemId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        totalVol = rs.getInt("tot");
                    }
                }
            }

            return new ItemAnalytics(itemId, vol24, min24, max24, avg24, lastTraded, totalVol);
        } catch (SQLException e) {
            LOGGER.error("Error fetching analytics for {}", itemId, e);
            return new ItemAnalytics(itemId, 0, 0L, 0L, 0L, 0L, 0);
        } finally {
            lock.unlock();
        }
    }

    public Set<String> getPlayerFavorites(UUID playerUuid) {
        if (playerUuid == null) return Collections.emptySet();
        lock.lock();
        try {
            Set<String> set = new HashSet<>();
            String sql = "SELECT item_id FROM player_favorites WHERE player_uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerUuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        set.add(rs.getString("item_id"));
                    }
                }
            }
            return set;
        } catch (SQLException e) {
            LOGGER.error("Error fetching favorites for {}", playerUuid, e);
            return Collections.emptySet();
        } finally {
            lock.unlock();
        }
    }

    public boolean togglePlayerFavorite(UUID playerUuid, String itemId) {
        if (playerUuid == null || itemId == null) return false;
        lock.lock();
        try {
            String checkSql = "SELECT 1 FROM player_favorites WHERE player_uuid = ? AND item_id = ?";
            boolean exists = false;
            try (PreparedStatement pstmt = connection.prepareStatement(checkSql)) {
                pstmt.setString(1, playerUuid.toString());
                pstmt.setString(2, itemId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    exists = rs.next();
                }
            }

            if (exists) {
                String delSql = "DELETE FROM player_favorites WHERE player_uuid = ? AND item_id = ?";
                try (PreparedStatement pstmt = connection.prepareStatement(delSql)) {
                    pstmt.setString(1, playerUuid.toString());
                    pstmt.setString(2, itemId);
                    pstmt.executeUpdate();
                }
                return false;
            } else {
                String insSql = "INSERT INTO player_favorites (player_uuid, item_id) VALUES (?, ?)";
                try (PreparedStatement pstmt = connection.prepareStatement(insSql)) {
                    pstmt.setString(1, playerUuid.toString());
                    pstmt.setString(2, itemId);
                    pstmt.executeUpdate();
                }
                return true;
            }
        } catch (SQLException e) {
            LOGGER.error("Error toggling favorite for {} on {}", playerUuid, itemId, e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    /* ========================================================================= */
    /* Discovery Tracking & Master Item Stats                                    */
    /* ========================================================================= */

    public void recordItemDiscovery(String itemId, String discoveredBy) {
        lock.lock();
        try {
            String sql = "INSERT OR IGNORE INTO discovered_items (item_id, first_listed_by) VALUES (?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, itemId);
                pstmt.setString(2, discoveredBy);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.error("Error recording item discovery for {}", itemId, e);
        } finally {
            lock.unlock();
        }
    }

    public boolean isItemDiscovered(String itemId) {
        lock.lock();
        try {
            String sql = "SELECT 1 FROM discovered_items WHERE item_id = ? UNION SELECT 1 FROM listings WHERE item_id = ? LIMIT 1";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, itemId);
                pstmt.setString(2, itemId);
                ResultSet rs = pstmt.executeQuery();
                return rs.next();
            }
        } catch (SQLException e) {
            LOGGER.error("Error checking item discovery for {}", itemId, e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    public Set<String> getDiscoveredItems() {
        lock.lock();
        Set<String> set = new HashSet<>();
        try {
            String sql = "SELECT item_id FROM discovered_items UNION SELECT item_id FROM listings";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    set.add(rs.getString("item_id"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Error fetching discovered items", e);
        } finally {
            lock.unlock();
        }
        return set;
    }

    public record ItemMarketStats(String itemId, long lowestSell, long highestBuy, int supply, int demand) {}

    public Map<String, ItemMarketStats> getAllItemMarketStats() {
        lock.lock();
        Map<String, ItemMarketStats> map = new HashMap<>();
        try {
            String sql = """
                SELECT 
                    items.item_id,
                    COALESCE(l_stats.lowest_sell, 0) AS lowest_sell,
                    COALESCE(b_stats.highest_buy, 0) AS highest_buy,
                    COALESCE(l_stats.supply_count, 0) AS supply_count,
                    COALESCE(b_stats.demand_count, 0) AS demand_count
                FROM (
                    SELECT item_id FROM listings
                    UNION
                    SELECT item_id FROM buy_orders
                ) AS items
                LEFT JOIN (
                    SELECT item_id, MIN(price_copper) AS lowest_sell, SUM(quantity) AS supply_count
                    FROM listings GROUP BY item_id
                ) l_stats ON items.item_id = l_stats.item_id
                LEFT JOIN (
                    SELECT item_id, MAX(price_copper) AS highest_buy, SUM(quantity) AS demand_count
                    FROM buy_orders GROUP BY item_id
                ) b_stats ON items.item_id = b_stats.item_id
            """;
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    String itemId = rs.getString("item_id");
                    long lowestSell = rs.getLong("lowest_sell");
                    long highestBuy = rs.getLong("highest_buy");
                    int supply = rs.getInt("supply_count");
                    int demand = rs.getInt("demand_count");
                    map.put(itemId, new ItemMarketStats(itemId, lowestSell, highestBuy, supply, demand));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Error getting all item market stats", e);
        } finally {
            lock.unlock();
        }
        return map;
    }

    /* ========================================================================= */
    /* Baseline Server Liquidity Seeding (Standard Vanilla Only)                 */
    /* ========================================================================= */

    private void seedBaselineLiquidity() {
        try {
            String checkSql = "SELECT COUNT(*) AS count FROM listings WHERE seller_uuid = '00000000-0000-0000-0000-000000000000'";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(checkSql)) {
                if (rs.next() && rs.getInt("count") > 0) {
                    return; // Already seeded
                }
            }

            LOGGER.info("Seeding baseline server liquidity for standard vanilla resources...");
            // Common starter items with reasonable supplies and baseline prices (copper)
            Map<String, int[]> seeds = new LinkedHashMap<>();
            // format: itemId -> [priceCopper, quantity]
            seeds.put("minecraft:oak_log", new int[]{5, 256});
            seeds.put("minecraft:spruce_log", new int[]{5, 256});
            seeds.put("minecraft:birch_log", new int[]{5, 256});
            seeds.put("minecraft:cobblestone", new int[]{2, 512});
            seeds.put("minecraft:stone", new int[]{2, 512});
            seeds.put("minecraft:dirt", new int[]{1, 512});
            seeds.put("minecraft:coal", new int[]{10, 256});
            seeds.put("minecraft:copper_ingot", new int[]{15, 128});
            seeds.put("minecraft:iron_ingot", new int[]{50, 128});
            seeds.put("minecraft:gold_ingot", new int[]{150, 64});
            seeds.put("minecraft:diamond", new int[]{500, 32});
            seeds.put("minecraft:torch", new int[]{3, 256});
            seeds.put("minecraft:arrow", new int[]{4, 256});
            seeds.put("minecraft:bread", new int[]{8, 128});
            seeds.put("minecraft:cooked_beef", new int[]{10, 128});
            seeds.put("minecraft:wheat", new int[]{4, 256});
            seeds.put("minecraft:sugar_cane", new int[]{5, 256});
            seeds.put("minecraft:glass", new int[]{4, 256});
            seeds.put("minecraft:iron_pickaxe", new int[]{180, 16});
            seeds.put("minecraft:iron_sword", new int[]{120, 16});
            seeds.put("minecraft:iron_axe", new int[]{180, 16});
            seeds.put("minecraft:bow", new int[]{100, 16});
            seeds.put("minecraft:shield", new int[]{80, 16});

            String insertSql = "INSERT INTO listings (seller_uuid, seller_name, item_id, item_nbt, price_copper, quantity) VALUES ('00000000-0000-0000-0000-000000000000', 'Market Liquidity', ?, '', ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(insertSql)) {
                for (Map.Entry<String, int[]> entry : seeds.entrySet()) {
                    String itemId = entry.getKey();
                    int price = entry.getValue()[0];
                    int qty = entry.getValue()[1];

                    // Safety verification: NEVER seed emeralds or endgame items
                    if (CategoryDef.isEmeraldCurrency(itemId)) continue;
                    if (CategoryDef.isEndgameRestricted(itemId)) continue;

                    pstmt.setString(1, itemId);
                    pstmt.setLong(2, price);
                    pstmt.setInt(3, qty);
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
            LOGGER.info("Baseline server liquidity successfully seeded.");
        } catch (SQLException e) {
            LOGGER.error("Error seeding baseline liquidity", e);
        }
    }

    /**
     * Gets the number of unlocked rows in the personal bank vault for the given player UUID (2 to 6).
     */
    public int getBankRows(UUID playerUuid) {
        lock.lock();
        try {
            String sql = "SELECT bank_rows FROM accounts WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int rows = rs.getInt("bank_rows");
                        if (rows < 2) rows = 2;
                        if (rows > 6) rows = 6;
                        return rows;
                    }
                }
            }
            return 2;
        } catch (Exception e) {
            return 2;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Upgrades the player's bank vault capacity by +1 row (up to max 6 rows / 54 slots).
     */
    public boolean upgradeBankRows(UUID playerUuid, String playerName) {
        lock.lock();
        try {
            int current = getBankRows(playerUuid);
            if (current >= 6) return false;
            int next = current + 1;
            String sql = "UPDATE accounts SET bank_rows = ? WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, next);
                ps.setString(2, playerUuid.toString());
                ps.executeUpdate();
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to upgrade bank rows for {}", playerUuid, e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Loads the personal bank vault for the given player UUID with dynamic row count.
     */
    public SimpleContainer loadBankVault(UUID playerUuid, int rows, HolderLookup.Provider registries) {
        lock.lock();
        try {
            int totalSlots = rows * 9;
            SimpleContainer container = new SimpleContainer(totalSlots);
            String sql = "SELECT slot_index, item_id, item_nbt, quantity FROM bank_vault_items WHERE player_uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int slot = rs.getInt("slot_index");
                        String itemId = rs.getString("item_id");
                        String nbt = rs.getString("item_nbt");
                        int qty = rs.getInt("quantity");
                        if (slot >= 0 && slot < totalSlots) {
                            ItemStack stack = ItemSerializer.deserialize(nbt, itemId, qty, registries);
                            container.setItem(slot, stack);
                        }
                    }
                }
            }
            return container;
        } catch (Exception e) {
            LOGGER.error("Failed to load bank vault for {}", playerUuid, e);
            return new SimpleContainer(rows * 9);
        } finally {
            lock.unlock();
        }
    }

    public SimpleContainer loadBankVault(UUID playerUuid, HolderLookup.Provider registries) {
        return loadBankVault(playerUuid, getBankRows(playerUuid), registries);
    }

    /**
     * Persists the 27-slot personal bank vault for the given player UUID into SQLite.
     */
    public void saveBankVault(UUID playerUuid, Container container, HolderLookup.Provider registries) {
        lock.lock();
        try {
            connection.setAutoCommit(false);
            try {
                // Delete previous entries
                try (PreparedStatement del = connection.prepareStatement("DELETE FROM bank_vault_items WHERE player_uuid = ?")) {
                    del.setString(1, playerUuid.toString());
                    del.executeUpdate();
                }

                // Insert all non-empty items
                String insertSql = "INSERT INTO bank_vault_items (player_uuid, slot_index, item_id, item_nbt, quantity) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement ins = connection.prepareStatement(insertSql)) {
                    for (int slot = 0; slot < container.getContainerSize(); slot++) {
                        ItemStack stack = container.getItem(slot);
                        if (!stack.isEmpty()) {
                            ResourceLocation loc = BuiltInRegistries.ITEM.getKey(stack.getItem());
                            String itemId = loc != null ? loc.toString() : "minecraft:air";
                            String nbt = ItemSerializer.serialize(stack, registries);
                            ins.setString(1, playerUuid.toString());
                            ins.setInt(2, slot);
                            ins.setString(3, itemId);
                            ins.setString(4, nbt != null ? nbt : "");
                            ins.setInt(5, stack.getCount());
                            ins.addBatch();
                        }
                    }
                    ins.executeBatch();
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to save bank vault for {}", playerUuid, e);
        } finally {
            lock.unlock();
        }
    }

    public int getPocketEmeralds(UUID uuid) {
        lock.lock();
        try {
            String sql = "SELECT pocket_emeralds FROM accounts WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("pocket_emeralds");
                    }
                }
            }
            return 0;
        } catch (Exception e) {
            LOGGER.error("Error getting pocket emeralds for {}", uuid, e);
            return 0;
        } finally {
            lock.unlock();
        }
    }

    public void setPocketEmeralds(UUID uuid, int amount) {
        if (amount < 0) amount = 0;
        if (amount > 999) amount = 999;
        lock.lock();
        try {
            String sql = "UPDATE accounts SET pocket_emeralds = ? WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, amount);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            LOGGER.error("Error setting pocket emeralds for {}", uuid, e);
        } finally {
            lock.unlock();
        }
    }

    public int getVaultEmeralds(UUID uuid) {
        lock.lock();
        try {
            String sql = "SELECT vault_emeralds FROM accounts WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("vault_emeralds");
                    }
                }
            }
            return 0;
        } catch (Exception e) {
            LOGGER.error("Error getting vault emeralds for {}", uuid, e);
            return 0;
        } finally {
            lock.unlock();
        }
    }

    public void setVaultEmeralds(UUID uuid, int amount) {
        if (amount < 0) amount = 0;
        if (amount > 9_999_999) amount = 9_999_999;
        lock.lock();
        try {
            String sql = "UPDATE accounts SET vault_emeralds = ? WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, amount);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            LOGGER.error("Error setting vault emeralds for {}", uuid, e);
        } finally {
            lock.unlock();
        }
    }

    public int depositEmeraldsToVault(UUID uuid, int amount) {
        if (amount <= 0) return 0;
        lock.lock();
        try {
            int currentPocket = getPocketEmeralds(uuid);
            int currentVault = getVaultEmeralds(uuid);
            int spaceInVault = 9_999_999 - currentVault;
            int toDeposit = Math.min(amount, Math.min(currentPocket, spaceInVault));
            if (toDeposit > 0) {
                setPocketEmeralds(uuid, currentPocket - toDeposit);
                setVaultEmeralds(uuid, currentVault + toDeposit);
            }
            return toDeposit;
        } finally {
            lock.unlock();
        }
    }

    public int depositDirectToVault(UUID uuid, int amount) {
        if (amount <= 0) return 0;
        lock.lock();
        try {
            int currentVault = getVaultEmeralds(uuid);
            int spaceInVault = 9_999_999 - currentVault;
            int toDeposit = Math.min(amount, spaceInVault);
            if (toDeposit > 0) {
                setVaultEmeralds(uuid, currentVault + toDeposit);
            }
            return toDeposit;
        } finally {
            lock.unlock();
        }
    }

    public int withdrawEmeraldsFromVault(UUID uuid, int amount) {
        if (amount <= 0) return 0;
        lock.lock();
        try {
            int currentPocket = getPocketEmeralds(uuid);
            int currentVault = getVaultEmeralds(uuid);
            int spaceInPocket = 999 - currentPocket;
            int toWithdraw = Math.min(amount, Math.min(currentVault, spaceInPocket));
            if (toWithdraw > 0) {
                setVaultEmeralds(uuid, currentVault - toWithdraw);
                setPocketEmeralds(uuid, currentPocket + toWithdraw);
            }
            return toWithdraw;
        } finally {
            lock.unlock();
        }
    }
}
