package com.omni.marketplace;

import com.omni.marketplace.catalog.CategoryDef;
import com.omni.marketplace.catalog.CategoryDef.MainCategory;
import com.omni.marketplace.catalog.CategoryDef.SubCategory;
import com.omni.marketplace.catalog.PriceEngine;
import com.omni.marketplace.db.DatabaseManager;
import com.omni.marketplace.db.DatabaseManager.ListingResult;
import com.omni.marketplace.db.model.MarketModels.AccountSummary;
import com.omni.marketplace.db.model.MarketModels.BuyOrderRecord;
import com.omni.marketplace.db.model.MarketModels.ListingRecord;
import com.omni.marketplace.db.model.MarketModels.VaultItemRecord;
import com.omni.marketplace.util.CurrencyUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MarketplaceMechanicsTest {

    @TempDir
    Path tempDir;

    private DatabaseManager db;
    private final UUID playerA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID playerB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        db = DatabaseManager.getInstance();
        db.initialize(tempDir);
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    /* ========================================================================= */
    /* 1. Currency Conversion & Formatting Tests                                 */
    /* ========================================================================= */

    @Test
    @DisplayName("Currency Conversion: 100c = 1s, 100s = 1g (10,000c = 1g)")
    void testCurrencyConversions() {
        assertEquals(100L, CurrencyUtils.COPPER_PER_SILVER);
        assertEquals(100L, CurrencyUtils.SILVER_PER_GOLD);
        assertEquals(10000L, CurrencyUtils.COPPER_PER_GOLD);

        // 1g 23s 45c = 10000 + 2300 + 45 = 12345
        long copper = CurrencyUtils.toCopper(1, 23, 45);
        assertEquals(12345L, copper);

        CurrencyUtils.CurrencyBreakdown breakdown = CurrencyUtils.breakdown(12345L);
        assertEquals(1L, breakdown.gold());
        assertEquals(23L, breakdown.silver());
        assertEquals(45L, breakdown.copper());

        // Zero and boundaries
        CurrencyUtils.CurrencyBreakdown zero = CurrencyUtils.breakdown(0L);
        assertEquals(0L, zero.gold());
        assertEquals(0L, zero.silver());
        assertEquals(0L, zero.copper());

        CurrencyUtils.CurrencyBreakdown goldOnly = CurrencyUtils.breakdown(50000L);
        assertEquals(5L, goldOnly.gold());
        assertEquals(0L, goldOnly.silver());
        assertEquals(0L, goldOnly.copper());
    }

    @Test
    @DisplayName("Currency Formatting Display")
    void testCurrencyFormatting() {
        assertEquals("§c0c", CurrencyUtils.format(0));
        assertEquals("§c50c", CurrencyUtils.format(50));
        assertEquals("§f1s", CurrencyUtils.format(100));
        assertEquals("§f1s §c50c", CurrencyUtils.format(150));
        assertEquals("§61g", CurrencyUtils.format(10000));
        assertEquals("§61g §f25s", CurrencyUtils.format(12500));
        assertEquals("§61g §f25s §c50c", CurrencyUtils.format(12550));
    }

    /* ========================================================================= */
    /* 2. GW2 Fee Structure & Real Profit Calculations                           */
    /* ========================================================================= */

    @Test
    @DisplayName("GW2 Fee Structure: 5% Listing Fee (min 1c), 10% Exchange Fee")
    void testGW2Fees() {
        // 100c listing: 5% = 5c listing fee, 10% = 10c exchange fee
        assertEquals(5L, CurrencyUtils.calculateListingFee(100, 1));
        assertEquals(10L, CurrencyUtils.calculateExchangeFee(100, 1));
        assertEquals(85L, CurrencyUtils.calculateNetProfit(100, 1));

        // Minimum 1c listing fee for cheap items
        assertEquals(1L, CurrencyUtils.calculateListingFee(5, 1)); // 5% of 5 is 0.25 -> 1c min
        assertEquals(0L, CurrencyUtils.calculateExchangeFee(5, 1)); // 10% of 5 is 0.5 -> 0c

        // Real profit calculations for custom listing vs instant sell
        // Custom listing (15% total fee): 1,000c gross -> 50c listing fee + 100c exchange fee = 850c net
        long customProfit = PriceEngine.calculateRealProfit(1000, 1, false);
        assertEquals(850L, customProfit);

        // Instant sell (matches buy order, 10% fee only, no listing fee): 1,000c gross -> 100c exchange fee = 900c net
        long instantProfit = PriceEngine.calculateRealProfit(1000, 1, true);
        assertEquals(900L, instantProfit);
    }

    /* ========================================================================= */
    /* 3. Emerald Currency Exclusions                                            */
    /* ========================================================================= */

    @Test
    @DisplayName("Emerald Currency Exclusions: Emeralds and Emerald Blocks cannot be traded")
    void testEmeraldCurrencyExclusions() {
        assertTrue(CategoryDef.isEmeraldCurrency("minecraft:emerald"));
        assertTrue(CategoryDef.isEmeraldCurrency("minecraft:emerald_block"));
        assertTrue(CategoryDef.isEmeraldCurrency("emerald"));
        assertTrue(CategoryDef.isEmeraldCurrency("emerald_block"));

        assertFalse(CategoryDef.isEmeraldCurrency("minecraft:diamond"));
        assertFalse(CategoryDef.isEmeraldCurrency("minecraft:gold_ingot"));
        assertFalse(CategoryDef.isEmeraldCurrency("minecraft:iron_ingot"));
        assertFalse(CategoryDef.isEmeraldCurrency("minecraft:netherite_ingot"));
    }

    /* ========================================================================= */
    /* 4. Category and Subcategory Hierarchy                                    */
    /* ========================================================================= */

    @Test
    @DisplayName("Category & Subcategory Hierarchy")
    void testCategoriesAndSubcategories() {
        for (MainCategory main : MainCategory.values()) {
            List<SubCategory> subs = CategoryDef.getSubCategories(main);
            assertNotNull(subs);
            assertFalse(subs.isEmpty(), "Subcategories list for " + main + " must not be empty");
            assertTrue(subs.contains(SubCategory.ALL), "Subcategories list for " + main + " must contain ALL");
        }

        // Special items category
        List<SubCategory> specialSubs = CategoryDef.getSubCategories(MainCategory.SPECIAL);
        assertTrue(specialSubs.contains(SubCategory.BOSS_RARE));
        assertTrue(specialSubs.contains(SubCategory.NETHERITE));
        assertTrue(specialSubs.contains(SubCategory.MUSIC_DISCS));
        assertTrue(specialSubs.contains(SubCategory.ENCHANTED_BOOKS));
        assertTrue(specialSubs.contains(SubCategory.MOD_ITEMS));
    }

    /* ========================================================================= */
    /* 5. Dynamic Suggested Price Calculations                                   */
    /* ========================================================================= */

    @Test
    @DisplayName("Dynamic Suggested Price: Spread Midpoint & Fallbacks")
    void testDynamicSuggestedPrice() {
        // When both ask and bid exist, suggested price is within spread:
        // ask = 200, bid = 100: spread = 100, 60% = 60, suggested = 160
        long sug = PriceEngine.computeSuggestedPrice(200, 100, null);
        assertEquals(160L, sug);

        // When spread is small (1c): defaults to lowest ask
        long sugTight = PriceEngine.computeSuggestedPrice(101, 100, null);
        assertEquals(101L, sugTight);

        // When only ask exists: suggested is lowest ask
        long sugAskOnly = PriceEngine.computeSuggestedPrice(150, 0, null);
        assertEquals(150L, sugAskOnly);

        // When only bid exists: suggested is highest bid
        long sugBidOnly = PriceEngine.computeSuggestedPrice(0, 120, null);
        assertEquals(120L, sugBidOnly);
    }

    /* ========================================================================= */
    /* 6. Database Account Operations                                            */
    /* ========================================================================= */

    @Test
    @DisplayName("Database: Account Creation and Copper Balance Transactions")
    void testDatabaseAccountTransactions() {
        AccountSummary acc = db.getOrCreateAccount(playerA, "PlayerA");
        assertEquals(0L, acc.copperBalance());
        assertEquals(0, acc.vaultItemCount());
        assertEquals(0L, acc.vaultCopperAmount());

        // Deposit
        db.depositCopper(playerA, 5000L);
        AccountSummary updated = db.getOrCreateAccount(playerA, "PlayerA");
        assertEquals(5000L, updated.copperBalance());

        // Successful Withdraw
        boolean success = db.withdrawCopper(playerA, 2000L);
        assertTrue(success);
        assertEquals(3000L, db.getOrCreateAccount(playerA, "PlayerA").copperBalance());

        // Insufficient funds withdraw
        boolean fail = db.withdrawCopper(playerA, 10000L);
        assertFalse(fail);
        assertEquals(3000L, db.getOrCreateAccount(playerA, "PlayerA").copperBalance());
    }

    /* ========================================================================= */
    /* 7. Database Emerald Protections on Open Market                            */
    /* ========================================================================= */

    @Test
    @DisplayName("Database: Emeralds are strictly rejected on the open marketplace")
    void testDatabaseEmeraldRejection() {
        db.depositCopper(playerA, 100000L);

        // Sell listing rejection
        ListingResult sellRes = db.createSellListing(playerA, "PlayerA", "minecraft:emerald", "", 100, 1);
        assertFalse(sellRes.success());
        assertTrue(sellRes.message().contains("Emeralds"));

        ListingResult sellBlockRes = db.createSellListing(playerA, "PlayerA", "minecraft:emerald_block", "", 900, 1);
        assertFalse(sellBlockRes.success());

        // Buy order rejection
        ListingResult buyRes = db.createBuyOrder(playerA, "PlayerA", "minecraft:emerald", "", 100, 1);
        assertFalse(buyRes.success());

        // Instant sell rejection
        ListingResult instSellRes = db.instantSellByItem(playerA, "PlayerA", "minecraft:emerald", 1, "");
        assertFalse(instSellRes.success());

        // Instant buy rejection
        ListingResult instBuyRes = db.instantBuyByItem(playerA, "PlayerA", "minecraft:emerald", 1);
        assertFalse(instBuyRes.success());
    }

    /* ========================================================================= */
    /* 8. Insufficient Funds Guards (5% Listing Fee & Escrow)                      */
    /* ========================================================================= */

    @Test
    @DisplayName("Database: Insufficient funds for 5% listing fee rejects listing without state change")
    void testInsufficientFundsListingFee() {
        // Player has 10 copper
        db.depositCopper(playerA, 10L);

        // Wants to list 1 diamond for 1000c -> Listing fee is 50c
        ListingResult res = db.createSellListing(playerA, "PlayerA", "minecraft:diamond", "", 1000, 1);
        assertFalse(res.success());
        assertTrue(res.message().contains("Insufficient copper"));

        // Copper balance remains completely untouched
        assertEquals(10L, db.getOrCreateAccount(playerA, "PlayerA").copperBalance());
        // No listings created
        assertEquals(0, db.getPlayerListings(playerA).size());
    }

    @Test
    @DisplayName("Database: Insufficient funds for buy order escrow rejects buy order")
    void testInsufficientFundsBuyOrder() {
        db.depositCopper(playerA, 50L);

        // Wants to order 1 diamond at 500c -> Requires 500c escrow
        ListingResult res = db.createBuyOrder(playerA, "PlayerA", "minecraft:diamond", "", 500, 1);
        assertFalse(res.success());

        // Copper balance remains completely untouched
        assertEquals(50L, db.getOrCreateAccount(playerA, "PlayerA").copperBalance());
        assertEquals(0, db.getPlayerBuyOrders(playerA).size());
    }

    /* ========================================================================= */
    /* 9. Order Matching Engine: Buy Order into Existing Sell Listing            */
    /* ========================================================================= */

    @Test
    @DisplayName("Database: Matching Buy Order into cheaper Sell Listing matches at ask and refunds difference")
    void testOrderMatchingBuyOrderIntoCheaperAsk() {
        // Player A lists 10 diamonds at 400c each. Listing fee for 4,000c is 200c.
        db.depositCopper(playerA, 500L);
        ListingResult listRes = db.createSellListing(playerA, "PlayerA", "minecraft:diamond", "{id:\"diamond\"}", 400, 10);
        assertTrue(listRes.success());
        assertEquals(300L, db.getOrCreateAccount(playerA, "PlayerA").copperBalance()); // 500 - 200 fee

        // Player B creates a buy order for 4 diamonds at 500c each (2,000c escrowed).
        db.depositCopper(playerB, 3000L);
        ListingResult buyRes = db.createBuyOrder(playerB, "PlayerB", "minecraft:diamond", "{id:\"diamond\"}", 500, 4);
        assertTrue(buyRes.success());
        assertEquals(1000L, db.getOrCreateAccount(playerB, "PlayerB").copperBalance()); // 3000 - 2000 escrow

        // Verification of match:
        // Match occurs at seller's ask price: 400c per unit
        // Trade volume: 4 diamonds
        // Buyer refund: (500 - 400) * 4 = 400c refunded to Player B's vault currency!
        // Seller net earnings: (400 * 4) = 1600c - 10% fee (160c) = 1440c credited to Player A's vault currency!
        // Buyer vault items: 4 diamonds delivered to Player B's vault!
        // Player A's remaining listing: 6 diamonds at 400c!

        AccountSummary accB = db.getOrCreateAccount(playerB, "PlayerB");
        assertEquals(400L, accB.vaultCopperAmount());
        assertEquals(4, accB.vaultItemCount());

        AccountSummary accA = db.getOrCreateAccount(playerA, "PlayerA");
        assertEquals(1440L, accA.vaultCopperAmount());

        List<ListingRecord> listingsA = db.getPlayerListings(playerA);
        assertEquals(1, listingsA.size());
        assertEquals(6, listingsA.get(0).quantity());
    }

    /* ========================================================================= */
    /* 10. Order Matching Engine: Sell Listing into Existing Buy Order           */
    /* ========================================================================= */

    @Test
    @DisplayName("Database: Matching Sell Listing into higher Buy Order matches at buyer's bid price")
    void testOrderMatchingSellListingIntoHigherBid() {
        // Player B places a buy order for 10 redstone at 60c each (600c escrow).
        db.depositCopper(playerB, 1000L);
        ListingResult buyRes = db.createBuyOrder(playerB, "PlayerB", "minecraft:redstone", "", 60, 10);
        assertTrue(buyRes.success());

        // Player A lists 4 redstone at 50c each (200c gross, 10c listing fee).
        db.depositCopper(playerA, 100L);
        ListingResult listRes = db.createSellListing(playerA, "PlayerA", "minecraft:redstone", "", 50, 4);
        assertTrue(listRes.success());

        // Verification:
        // Matches immediately into the 60c bid!
        // Seller net: 4 * 60 = 240c - 10% (24c) = 216c delivered to Seller's vault!
        // Buyer vault: 4 redstone delivered!
        // Buyer remaining buy order: 6 redstone at 60c!
        AccountSummary accA = db.getOrCreateAccount(playerA, "PlayerA");
        assertEquals(216L, accA.vaultCopperAmount());

        AccountSummary accB = db.getOrCreateAccount(playerB, "PlayerB");
        assertEquals(4, accB.vaultItemCount());

        List<BuyOrderRecord> bidsB = db.getPlayerBuyOrders(playerB);
        assertEquals(1, bidsB.size());
        assertEquals(6, bidsB.get(0).quantity());
    }

    /* ========================================================================= */
    /* 11. Partial Fills in Instant Trade                                        */
    /* ========================================================================= */

    @Test
    @DisplayName("Database: Instant Sell only fills available buy orders and returns correct remainingQuantity")
    void testPartialInstantSell() {
        // Player B wants only 3 items at 100c
        db.depositCopper(playerB, 500L);
        db.createBuyOrder(playerB, "PlayerB", "minecraft:golden_apple", "", 100, 3);

        // Player A attempts to instant sell 7 items
        ListingResult res = db.instantSellByItem(playerA, "PlayerA", "minecraft:golden_apple", 7, "");
        assertTrue(res.success());
        assertEquals(4, res.remainingQuantity()); // 7 - 3 sold = 4 remaining

        AccountSummary accA = db.getOrCreateAccount(playerA, "PlayerA");
        // 3 * 100 = 300c - 10% (30c) = 270c net
        assertEquals(270L, accA.vaultCopperAmount());
    }

    @Test
    @DisplayName("Database: Instant Sell with zero active buy orders fails safely")
    void testInstantSellNoBuyOrders() {
        ListingResult res = db.instantSellByItem(playerA, "PlayerA", "minecraft:totem_of_undying", 1, "");
        assertFalse(res.success());
        assertTrue(res.message().contains("No active buy orders"));
    }

    /* ========================================================================= */
    /* 12. Order Cancellations                                                   */
    /* ========================================================================= */

    @Test
    @DisplayName("Database: Cancelling a Sell Listing returns items to vault with 0 fee refund")
    void testCancelSellListing() {
        db.depositCopper(playerA, 1000L);
        db.createSellListing(playerA, "PlayerA", "minecraft:bow", "{id:\"bow\"}", 200, 2);

        List<ListingRecord> listings = db.getPlayerListings(playerA);
        assertEquals(1, listings.size());
        long listingId = listings.get(0).id();

        boolean cancelled = db.cancelListing(playerA, listingId);
        assertTrue(cancelled);

        // Listing table is now empty
        assertEquals(0, db.getPlayerListings(playerA).size());

        // 2 bows deposited in Player A's vault
        List<VaultItemRecord> vault = db.getVaultItems(playerA);
        assertEquals(1, vault.size());
        assertEquals(2, vault.get(0).quantity());
        assertEquals("minecraft:bow", vault.get(0).itemId());
    }

    @Test
    @DisplayName("Database: Cancelling a Buy Order refunds 100% of remaining escrow to vault")
    void testCancelBuyOrder() {
        db.depositCopper(playerA, 1000L);
        db.createBuyOrder(playerA, "PlayerA", "minecraft:redstone", "", 150, 4); // 600c escrow

        List<BuyOrderRecord> buyOrders = db.getPlayerBuyOrders(playerA);
        assertEquals(1, buyOrders.size());
        long orderId = buyOrders.get(0).id();

        boolean cancelled = db.cancelBuyOrder(playerA, orderId);
        assertTrue(cancelled);

        assertEquals(0, db.getPlayerBuyOrders(playerA).size());
        // 600c escrow refunded to vault
        AccountSummary acc = db.getOrCreateAccount(playerA, "PlayerA");
        assertEquals(600L, acc.vaultCopperAmount());
    }

    /* ========================================================================= */
    /* 13. Special Items Discovery Rule                                          */
    /* ========================================================================= */

    @Test
    @DisplayName("Database: Special items require at least one player listing before buy orders can be placed")
    void testSpecialItemDiscoveryRule() {
        db.depositCopper(playerA, 500000L);
        db.depositCopper(playerB, 500000L);

        // Elytra is a special/boss item and is not discovered yet
        assertFalse(db.isItemDiscovered("minecraft:elytra"));

        // Player B tries to place buy order for elytra -> must be rejected
        ListingResult failBuy = db.createBuyOrder(playerB, "PlayerB", "minecraft:elytra", "", 300000, 1);
        assertFalse(failBuy.success());
        assertTrue(failBuy.message().contains("Special items"));

        // Player A lists an elytra for sale on the market
        ListingResult listRes = db.createSellListing(playerA, "PlayerA", "minecraft:elytra", "{Damage:0}", 350000, 1);
        assertTrue(listRes.success());

        // Now Elytra is discovered!
        assertTrue(db.isItemDiscovered("minecraft:elytra"));

        // Player B can now place a buy order for Elytra!
        ListingResult successBuy = db.createBuyOrder(playerB, "PlayerB", "minecraft:elytra", "", 320000, 1);
        assertTrue(successBuy.success());
    }

    /* ========================================================================= */
    /* 14. Baseline Liquidity Seeding Exclusions                                 */
    /* ========================================================================= */

    @Test
    @DisplayName("Database: Baseline server liquidity strictly excludes emeralds and endgame/boss drops")
    void testBaselineLiquidityExclusions() {
        UUID serverUuid = UUID.fromString("00000000-0000-0000-0000-000000000000");
        List<ListingRecord> serverListings = db.getPlayerListings(serverUuid);
        assertFalse(serverListings.isEmpty(), "Server must seed baseline listings for common materials");

        for (ListingRecord listing : serverListings) {
            String itemId = listing.itemId();
            assertFalse(CategoryDef.isEmeraldCurrency(itemId), "Emeralds must NEVER be seeded by server: " + itemId);
            assertFalse(CategoryDef.isEndgameRestricted(itemId), "Endgame items must NEVER be seeded by server: " + itemId);
        }
    }

    /* ========================================================================= */
    /* 15. Complex Combinations: Multi-Buyer Partial Matching & Residual Cancel  */
    /* ========================================================================= */

    @Test
    @DisplayName("Database: Multi-buyer partial matching leaves correct remainder and allows cancellation")
    void testMultiBuyerPartialMatchingAndCancel() {
        // Player A lists 10 amethyst shards at 200c each (listing fee: 100c)
        db.depositCopper(playerA, 500L);
        ListingResult listRes = db.createSellListing(playerA, "PlayerA", "minecraft:amethyst_shard", "", 200, 10);
        assertTrue(listRes.success());

        // Buyer 1 (Player B) buy orders 3 at 200c -> Matches 3 units immediately
        db.depositCopper(playerB, 1000L);
        ListingResult b1 = db.createBuyOrder(playerB, "PlayerB", "minecraft:amethyst_shard", "", 200, 3);
        assertTrue(b1.success());

        // Player A listing should now have 7 remaining
        List<ListingRecord> remListings = db.getPlayerListings(playerA);
        assertEquals(1, remListings.size());
        assertEquals(7, remListings.get(0).quantity());

        // Player B should have 3 shards in vault
        assertEquals(3, db.getOrCreateAccount(playerB, "PlayerB").vaultItemCount());

        // Seller Player A should have received 3 * 200 = 600c - 10% (60c) = 540c in vault
        assertEquals(540L, db.getOrCreateAccount(playerA, "PlayerA").vaultCopperAmount());

        // Now Player A cancels the remaining 7 units
        boolean cancelled = db.cancelListing(playerA, remListings.get(0).id());
        assertTrue(cancelled);
        assertEquals(0, db.getPlayerListings(playerA).size());

        // Player A vault now contains the 7 returned amethyst shards
        List<VaultItemRecord> vaultA = db.getVaultItems(playerA);
        assertEquals(1, vaultA.size());
        assertEquals(7, vaultA.get(0).quantity());
        assertEquals("minecraft:amethyst_shard", vaultA.get(0).itemId());
    }

    /* ========================================================================= */
    /* 16. Order Book Depth & Ordering                                           */
    /* ========================================================================= */

    @Test
    @DisplayName("Database: Order book returns asks ascending and bids descending")
    void testOrderBookDepthAndOrdering() {
        // Create 3 asks at 300c, 100c, 200c
        db.depositCopper(playerA, 5000L);
        db.createSellListing(playerA, "PlayerA", "minecraft:glowstone_dust", "", 300, 5);
        db.createSellListing(playerA, "PlayerA", "minecraft:glowstone_dust", "", 100, 2);
        db.createSellListing(playerA, "PlayerA", "minecraft:glowstone_dust", "", 200, 3);

        List<ListingRecord> asks = db.getListingsForItem("minecraft:glowstone_dust");
        assertEquals(3, asks.size());
        // Verify ascending order: 100, 200, 300
        assertEquals(100L, asks.get(0).priceCopper());
        assertEquals(200L, asks.get(1).priceCopper());
        assertEquals(300L, asks.get(2).priceCopper());

        // Create 2 bids at 40c, 80c (below lowest ask of 100c so no auto-match occurs)
        db.depositCopper(playerB, 5000L);
        db.createBuyOrder(playerB, "PlayerB", "minecraft:glowstone_dust", "", 40, 4);
        db.createBuyOrder(playerB, "PlayerB", "minecraft:glowstone_dust", "", 80, 2);

        List<BuyOrderRecord> bids = db.getBuyOrdersForItem("minecraft:glowstone_dust");
        assertEquals(2, bids.size());
        // Verify descending order: 80, 40
        assertEquals(80L, bids.get(0).priceCopper());
        assertEquals(40L, bids.get(1).priceCopper());
    }

    /* ========================================================================= */
    /* 17. Bulk Emerald Conversion Math & Constraints                            */
    /* ========================================================================= */

    @Test
    @DisplayName("Currency: Bulk emerald and block exchange calculations")
    void testBulkEmeraldExchangeMath() {
        // 64 emeralds = 6,400c = 64 silver
        long stackEmeraldCopper = 64 * CurrencyUtils.COPPER_PER_EMERALD;
        assertEquals(6400L, stackEmeraldCopper);
        assertEquals(64L, CurrencyUtils.breakdown(stackEmeraldCopper).silver());
        assertEquals(0L, CurrencyUtils.breakdown(stackEmeraldCopper).gold());

        // 64 emerald blocks = 57,600c = 5 gold, 76 silver
        long stackBlockCopper = 64 * CurrencyUtils.COPPER_PER_EMERALD_BLOCK;
        assertEquals(57600L, stackBlockCopper);
        assertEquals(5L, CurrencyUtils.breakdown(stackBlockCopper).gold());
        assertEquals(76L, CurrencyUtils.breakdown(stackBlockCopper).silver());
        assertEquals(0L, CurrencyUtils.breakdown(stackBlockCopper).copper());

        // Deposit & withdraw full stacks in db
        db.depositCopper(playerA, stackBlockCopper);
        assertEquals(57600L, db.getOrCreateAccount(playerA, "PlayerA").copperBalance());

        // Withdraw 64 emeralds (6400c)
        boolean w1 = db.withdrawCopper(playerA, stackEmeraldCopper);
        assertTrue(w1);
        assertEquals(51200L, db.getOrCreateAccount(playerA, "PlayerA").copperBalance());

        // Insufficient funds withdraw
        boolean wFail = db.withdrawCopper(playerA, 60000L);
        assertFalse(wFail);
        assertEquals(51200L, db.getOrCreateAccount(playerA, "PlayerA").copperBalance());
    }

    /* ========================================================================= */
    /* 18. Large Quantity Edge Cases & Overflows                                 */
    /* ========================================================================= */

    @Test
    @DisplayName("Currency: Massive currency transactions maintain precision without overflow")
    void testMassiveCurrencyPrecision() {
        // 1,000 gold = 10,000,000 copper
        long thousandGold = 10_000_000L;
        CurrencyUtils.CurrencyBreakdown b = CurrencyUtils.breakdown(thousandGold);
        assertEquals(1000L, b.gold());
        assertEquals(0L, b.silver());
        assertEquals(0L, b.copper());

        // Fee calculation on 1,000 gold:
        // 5% listing fee = 500,000 copper (50 gold)
        assertEquals(500_000L, CurrencyUtils.calculateListingFee(thousandGold, 1));
        // 10% exchange fee = 1,000,000 copper (100 gold)
        assertEquals(1_000_000L, CurrencyUtils.calculateExchangeFee(thousandGold, 1));
        // Real net profit = 8,500,000 copper (850 gold)
        assertEquals(8_500_000L, CurrencyUtils.calculateNetProfit(thousandGold, 1));
    }

    /* ========================================================================= */
    /* 19. Security: Self-Trade / Wash-Trading Prevention                        */
    /* ========================================================================= */

    @Test
    @DisplayName("Security: Player cannot self-match orders to wash-trade or burn fees")
    void testSelfTradePrevention() {
        db.depositCopper(playerA, 50000L);

        // Player A lists 10 sculk catalysts at 500c each
        ListingResult listRes = db.createSellListing(playerA, "PlayerA", "minecraft:sculk_catalyst", "", 500, 10);
        assertTrue(listRes.success());

        // Player A now attempts to place a buy order for 5 sculk catalysts at 600c (which normally would match the 500c ask)
        ListingResult buyRes = db.createBuyOrder(playerA, "PlayerA", "minecraft:sculk_catalyst", "", 600, 5);
        assertTrue(buyRes.success());

        // The buy order must NOT have matched Player A's own listing!
        // Both the listing and the buy order should remain active on the market
        List<ListingRecord> listings = db.getPlayerListings(playerA);
        assertEquals(1, listings.size());
        assertEquals(10, listings.get(0).quantity(), "Listing quantity must remain 10 (no self-match)");

        List<BuyOrderRecord> buyOrders = db.getPlayerBuyOrders(playerA);
        assertEquals(1, buyOrders.size());
        assertEquals(5, buyOrders.get(0).quantity(), "Buy order quantity must remain 5 (no self-match)");

        // Player A's vault must NOT have received any items or currency from self-trade
        AccountSummary acc = db.getOrCreateAccount(playerA, "PlayerA");
        assertEquals(0, acc.vaultItemCount());
        assertEquals(0L, acc.vaultCopperAmount());

        // Also test instant buy by item: Player A cannot instant buy their own item
        ListingResult instBuy = db.instantBuyByItem(playerA, "PlayerA", "minecraft:sculk_catalyst", 1);
        assertFalse(instBuy.success(), "Instant buy of own item must fail");

        // Also test instant sell by item: Player A cannot instant sell to their own buy order
        ListingResult instSell = db.instantSellByItem(playerA, "PlayerA", "minecraft:sculk_catalyst", 1, "");
        assertFalse(instSell.success(), "Instant sell to own buy order must fail");
    }

    /* ========================================================================= */
    /* 20. Security: Negative & Zero Value Exploit Guards                        */
    /* ========================================================================= */

    @Test
    @DisplayName("Security: Negative or zero prices/quantities are rejected with no state change")
    void testNegativeAndZeroValueRejection() {
        db.depositCopper(playerA, 10000L);

        // Negative price in listing
        ListingResult negPriceList = db.createSellListing(playerA, "PlayerA", "minecraft:iron_ingot", "", -100, 1);
        assertFalse(negPriceList.success());

        // Zero price in listing
        ListingResult zeroPriceList = db.createSellListing(playerA, "PlayerA", "minecraft:iron_ingot", "", 0, 1);
        assertFalse(zeroPriceList.success());

        // Negative quantity in listing
        ListingResult negQtyList = db.createSellListing(playerA, "PlayerA", "minecraft:iron_ingot", "", 100, -5);
        assertFalse(negQtyList.success());

        // Zero quantity in listing
        ListingResult zeroQtyList = db.createSellListing(playerA, "PlayerA", "minecraft:iron_ingot", "", 100, 0);
        assertFalse(zeroQtyList.success());

        // Negative price in buy order
        ListingResult negPriceBuy = db.createBuyOrder(playerA, "PlayerA", "minecraft:iron_ingot", "", -50, 1);
        assertFalse(negPriceBuy.success());

        // Negative quantity in buy order
        ListingResult negQtyBuy = db.createBuyOrder(playerA, "PlayerA", "minecraft:iron_ingot", "", 50, -2);
        assertFalse(negQtyBuy.success());

        // Negative instant buy / sell
        assertFalse(db.instantBuyByItem(playerA, "PlayerA", "minecraft:iron_ingot", -1).success());
        assertFalse(db.instantSellByItem(playerA, "PlayerA", "minecraft:iron_ingot", -1, "").success());

        // Negative deposit / withdraw
        assertFalse(db.depositCopper(playerA, -500L));
        assertFalse(db.withdrawCopper(playerA, -500L));
        assertFalse(db.depositCopper(playerA, 0L));
        assertFalse(db.withdrawCopper(playerA, 0L));

        // Ensure balance is completely untouched
        assertEquals(10000L, db.getOrCreateAccount(playerA, "PlayerA").copperBalance());
        assertEquals(0, db.getPlayerListings(playerA).size());
        assertEquals(0, db.getPlayerBuyOrders(playerA).size());
    }

    /* ========================================================================= */
    /* 21. Economic Integrity: Exact Copper Conservation (Zero Unintended Profit)*/
    /* ========================================================================= */

    @Test
    @DisplayName("Integrity: Copper is strictly conserved across bids, asks, fee sinks, and refunds")
    void testExactCopperConservation() {
        // Player A starts with 10,000c. Player B starts with 10,000c.
        // Total money in system = 20,000c.
        db.depositCopper(playerA, 10000L);
        db.depositCopper(playerB, 10000L);

        // Player A lists 5 hearts of the sea at 200c each (Total: 1,000c).
        // Upfront listing fee: 5% of 1,000c = 50c sink.
        ListingResult listRes = db.createSellListing(playerA, "PlayerA", "minecraft:heart_of_the_sea", "", 200, 5);
        assertTrue(listRes.success());
        // Player A copper balance: 10,000 - 50 = 9,950c.

        // Player B creates buy order for 5 hearts of the sea at 300c each (Total escrow: 1,500c).
        ListingResult buyRes = db.createBuyOrder(playerB, "PlayerB", "minecraft:heart_of_the_sea", "", 300, 5);
        assertTrue(buyRes.success());
        // Player B copper balance: 10,000 - 1,500 = 8,500c.

        // Matching mechanics audit:
        // Match price = 200c (seller's ask).
        // 5 units traded = 1,000c gross trade value.
        // Exchange fee = 10% of 1,000c = 100c sink.
        // Seller net payout = 1,000c - 100c = 900c credited to Player A's vault.
        // Buyer refund for lower price = (300c - 200c) * 5 = 500c credited to Player B's vault.
        // Total fees sunk from economy: 50c (listing) + 100c (exchange) = 150c.

        AccountSummary accA = db.getOrCreateAccount(playerA, "PlayerA");
        AccountSummary accB = db.getOrCreateAccount(playerB, "PlayerB");

        long playerATotalWealth = accA.copperBalance() + accA.vaultCopperAmount();
        long playerBTotalWealth = accB.copperBalance() + accB.vaultCopperAmount();

        assertEquals(9950L, accA.copperBalance());
        assertEquals(900L, accA.vaultCopperAmount());
        assertEquals(10850L, playerATotalWealth); // 9950 + 900

        assertEquals(8500L, accB.copperBalance());
        assertEquals(500L, accB.vaultCopperAmount());
        assertEquals(9000L, playerBTotalWealth); // 8500 + 500

        long totalSystemWealth = playerATotalWealth + playerBTotalWealth;
        long expectedTotalWealth = 20000L - 150L; // Exactly 19,850c after 150c total fee sinks
        assertEquals(expectedTotalWealth, totalSystemWealth, "System copper must equal initial minus exact fees sinked (no phantom profits)");
    }

    /* ========================================================================= */
    /* 22. Integrity: No Item Duplication via Vault Claim or Order Cancellation  */
    /* ========================================================================= */

    @Test
    @DisplayName("Integrity: Items cannot be duplicated via repeated cancel or claim operations")
    void testNoItemDuplication() {
        db.depositCopper(playerA, 5000L);
        db.createSellListing(playerA, "PlayerA", "minecraft:netherite_scrap", "", 1000, 3);

        List<ListingRecord> listings = db.getPlayerListings(playerA);
        assertEquals(1, listings.size());
        long orderId = listings.get(0).id();

        // Cancel order once -> successful
        boolean firstCancel = db.cancelListing(playerA, orderId);
        assertTrue(firstCancel);

        // Cancel order a second time -> MUST fail
        boolean secondCancel = db.cancelListing(playerA, orderId);
        assertFalse(secondCancel, "Second cancel must fail");

        // Player B cannot cancel Player A's order
        boolean rogueCancel = db.cancelListing(playerB, orderId);
        assertFalse(rogueCancel, "Another player cannot cancel foreign orders");

        // Check vault items: exactly 3 scraps
        List<VaultItemRecord> vaultItems = db.getVaultItems(playerA);
        assertEquals(1, vaultItems.size());
        assertEquals(3, vaultItems.get(0).quantity());
    }

    /* ========================================================================= */
    /* 23. Catalog Supply/Demand Statistics without Cartesian Inflation          */
    /* ========================================================================= */

    @Test
    @DisplayName("Catalog: Supply and demand counts are exact and not multiplied by join cardinality")
    void testCatalogStatsExactCounts() {
        db.depositCopper(playerA, 50000L);
        db.depositCopper(playerB, 50000L);

        // Create 3 listings for prismarine_shard (not in server baseline seeds): quantities 5, 10, 15 (Total supply: 30)
        db.createSellListing(playerA, "PlayerA", "minecraft:prismarine_shard", "", 50, 5);
        db.createSellListing(playerA, "PlayerA", "minecraft:prismarine_shard", "", 55, 10);
        db.createSellListing(playerA, "PlayerA", "minecraft:prismarine_shard", "", 60, 15);

        // Create 2 buy orders for prismarine_shard: quantities 7, 8 (Total demand: 15)
        db.createBuyOrder(playerB, "PlayerB", "minecraft:prismarine_shard", "", 40, 7);
        db.createBuyOrder(playerB, "PlayerB", "minecraft:prismarine_shard", "", 35, 8);

        // Search catalog for prismarine_shard
        List<com.omni.marketplace.db.model.MarketModels.MarketSummaryRecord> entries = db.searchCatalog("prismarine_shard", 0, 50);
        assertFalse(entries.isEmpty());

        com.omni.marketplace.db.model.MarketModels.MarketSummaryRecord entry = entries.stream()
                .filter(e -> e.itemId().equals("minecraft:prismarine_shard"))
                .findFirst().orElse(null);

        assertNotNull(entry);
        assertEquals(30, entry.supplyCount(), "Total supply must be exactly 30 (not 30 * 2 = 60)");
        assertEquals(15, entry.demandCount(), "Total demand must be exactly 15 (not 15 * 3 = 45)");
        assertEquals(50L, entry.lowestSellCopper(), "Lowest ask must be 50c");
        assertEquals(40L, entry.highestBuyCopper(), "Highest bid must be 40c");
    }

    /* ========================================================================= */
    /* 24. Vault Persistence & Partial Quantity Updating for Inventory Failsafe  */
    /* ========================================================================= */

    @Test
    @DisplayName("Vault: Item quantity can be updated partially and deleted cleanly without item loss")
    void testVaultItemPartialRetention() throws Exception {
        // Add 64 diamonds to playerA's vault
        db.addVaultItem(playerA, "minecraft:diamond", "", 64, "Trade Fill");
        List<VaultItemRecord> items = db.getVaultItems(playerA);
        assertEquals(1, items.size());
        assertEquals(64, items.get(0).quantity());
        long recordId = items.get(0).id();

        // Simulate claiming 20 diamonds because player inventory only had room for 20:
        db.updateVaultItemQuantity(recordId, 44);

        // Verify that 44 diamonds remain safely in the vault
        List<VaultItemRecord> remaining = db.getVaultItems(playerA);
        assertEquals(1, remaining.size());
        assertEquals(44, remaining.get(0).quantity());

        // Account summary must also report 44 items waiting in vault
        AccountSummary acc = db.getOrCreateAccount(playerA, "PlayerA");
        assertEquals(44, acc.vaultItemCount());

        // Complete the remaining withdrawal
        db.deleteVaultItem(recordId);
        List<VaultItemRecord> emptyList = db.getVaultItems(playerA);
        assertTrue(emptyList.isEmpty());
    }
}

