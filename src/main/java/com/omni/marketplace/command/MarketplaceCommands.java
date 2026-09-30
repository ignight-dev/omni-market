package com.omni.marketplace.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.omni.marketplace.db.DatabaseManager;
import com.omni.marketplace.db.model.MarketModels.AccountSummary;
import com.omni.marketplace.entity.TradingPostMerchantEntity;
import com.omni.marketplace.network.MarketPackets.OpenMarketplaceS2C;
import com.omni.marketplace.registry.ModRegistry;
import com.omni.marketplace.util.CurrencyUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;
import java.util.Collections;

public class MarketplaceCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("market")
                .executes(MarketplaceCommands::checkBalance)
                .then(Commands.literal("balance").executes(MarketplaceCommands::checkBalance))
                .then(Commands.literal("check").executes(MarketplaceCommands::checkBalance))
                .then(Commands.literal("open").executes(MarketplaceCommands::openMarket))
                .then(Commands.literal("vault").executes(MarketplaceCommands::claimVault))
                .then(Commands.literal("claim").executes(MarketplaceCommands::claimVault))
                .then(buildAdminNode())
        );

        dispatcher.register(
            Commands.literal("marketplace")
                .executes(MarketplaceCommands::checkBalance)
                .then(Commands.literal("balance").executes(MarketplaceCommands::checkBalance))
                .then(Commands.literal("check").executes(MarketplaceCommands::checkBalance))
                .then(Commands.literal("open").executes(MarketplaceCommands::openMarket))
                .then(Commands.literal("vault").executes(MarketplaceCommands::claimVault))
                .then(Commands.literal("claim").executes(MarketplaceCommands::claimVault))
                .then(buildAdminNode())
        );

        dispatcher.register(
            Commands.literal("tradingpost")
                .executes(MarketplaceCommands::checkBalance)
                .then(Commands.literal("balance").executes(MarketplaceCommands::checkBalance))
                .then(Commands.literal("check").executes(MarketplaceCommands::checkBalance))
                .then(Commands.literal("open").executes(MarketplaceCommands::openMarket))
                .then(Commands.literal("vault").executes(MarketplaceCommands::claimVault))
                .then(Commands.literal("claim").executes(MarketplaceCommands::claimVault))
                .then(buildAdminNode())
        );

        dispatcher.register(
            Commands.literal("omni")
                .then(Commands.literal("market")
                    .executes(MarketplaceCommands::checkBalance)
                    .then(Commands.literal("balance").executes(MarketplaceCommands::checkBalance))
                    .then(Commands.literal("check").executes(MarketplaceCommands::checkBalance))
                    .then(Commands.literal("open").executes(MarketplaceCommands::openMarket))
                    .then(Commands.literal("vault").executes(MarketplaceCommands::claimVault))
                    .then(Commands.literal("claim").executes(MarketplaceCommands::claimVault))
                    .then(buildAdminNode())
                )
        );
    }

    private static int checkBalance(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            DatabaseManager db = DatabaseManager.getInstance();
            AccountSummary acc = db.getOrCreateAccount(player.getUUID(), player.getScoreboardName());

            player.sendSystemMessage(Component.literal("§6[Trading Post Wallet] §fPurse: " + CurrencyUtils.format(acc.copperBalance())));
            if (acc.vaultItemCount() > 0 || acc.vaultCopperAmount() > 0) {
                player.sendSystemMessage(Component.literal(String.format(
                        "§e[Guild Vault] §fItems Waiting: §a%d §f| Coins: §e%s",
                        acc.vaultItemCount(),
                        CurrencyUtils.format(acc.vaultCopperAmount())
                )));
                player.sendSystemMessage(Component.literal("§7(Visit a Trading Post Merchant or use a Transceiver to claim goods)"));
            } else {
                player.sendSystemMessage(Component.literal("§7[Guild Vault] Your delivery box is currently empty."));
                player.sendSystemMessage(Component.literal("§7(Visit a Trading Post Merchant or use a Transceiver to browse & trade)"));
            }
            return 1;
        }
        return 0;
    }

    private static int openMarket(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            // Check if player has operator permission (level 2) to bypass in-person requirement
            if (context.getSource().hasPermission(2)) {
                return adminForceOpen(context);
            } else {
                player.sendSystemMessage(Component.literal("§cRemote market access is unavailable! You must visit a §6Trading Post Merchant §cin person, or summon one using a §eMarketplace Transceiver§c."));
                return 0;
            }
        } else {
            context.getSource().sendFailure(Component.literal("Only players can open the Trading Post GUI."));
            return 0;
        }
    }

    private static int claimVault(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            // Check if player has operator permission (level 2) to bypass in-person requirement
            if (context.getSource().hasPermission(2)) {
                DatabaseManager db = DatabaseManager.getInstance();
                DatabaseManager.VaultClaimResult res = db.claimAllVault(player, player.level().registryAccess());
                if (res.itemsClaimed() > 0 || res.coinsClaimed() > 0) {
                    player.sendSystemMessage(Component.translatable("message.omni_marketplace.vault_claimed",
                            res.itemsClaimed(),
                            CurrencyUtils.format(res.coinsClaimed())));
                } else if (!res.inventoryFull()) {
                    player.sendSystemMessage(Component.literal("§7Your Guild Vault is empty."));
                }
                if (res.inventoryFull()) {
                    player.sendSystemMessage(Component.translatable("message.omni_marketplace.inventory_full_vault", res.itemsRemaining()));
                }
                return 1;
            } else {
                player.sendSystemMessage(Component.literal("§cRemote vault claiming is unavailable! You must visit a §6Trading Post Merchant §cin person to withdraw goods from your Guild Vault."));
                return 0;
            }
        }
        return 0;
    }

    private static int adminForceOpen(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            DatabaseManager db = DatabaseManager.getInstance();
            AccountSummary acc = db.getOrCreateAccount(player.getUUID(), player.getScoreboardName());
            PacketDistributor.sendToPlayer(player, new OpenMarketplaceS2C(acc.copperBalance(), acc.vaultItemCount(), acc.vaultCopperAmount()));
            PacketDistributor.sendToPlayer(player, new com.omni.marketplace.network.MarketPackets.SyncFavoritesS2C(new java.util.ArrayList<>(db.getPlayerFavorites(player.getUUID()))));
            player.sendSystemMessage(Component.literal("§d[Admin] Opened Trading Post GUI."));
            return 1;
        }
        return 0;
    }

    private static int adminSpawnMerchant(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            TradingPostMerchantEntity merchant = new TradingPostMerchantEntity(ModRegistry.TRADING_POST_MERCHANT.get(), player.level());
            merchant.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
            merchant.setTemporary(false, 0, null); // Permanent merchant
            player.level().addFreshEntity(merchant);

            player.sendSystemMessage(Component.literal("§aPermanent Trading Post Merchant spawned successfully!"));
            return 1;
        }
        return 0;
    }

    private static int adminSpawnMaster(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            com.omni.marketplace.entity.MerchantMasterEntity master = new com.omni.marketplace.entity.MerchantMasterEntity(ModRegistry.MERCHANT_MASTER.get(), player.level());
            master.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
            player.level().addFreshEntity(master);

            player.sendSystemMessage(Component.literal("§6❖ Grand Merchant Master spawned successfully!"));
            return 1;
        }
        return 0;
    }

    private static int adminSpawnBanker(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            com.omni.marketplace.entity.BankerEntity banker = new com.omni.marketplace.entity.BankerEntity(ModRegistry.BANKER.get(), player.level());
            banker.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
            player.level().addFreshEntity(banker);

            player.sendSystemMessage(Component.literal("§6❖ Imperial Banker spawned successfully!"));
            return 1;
        }
        return 0;
    }

    private static int adminGiveSelf(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            return giveDispatchBook(context.getSource(), player, 1);
        }
        context.getSource().sendFailure(Component.literal("Must specify a player from the server console."));
        return 0;
    }

    private static int adminGiveTarget(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            return giveDispatchBook(context.getSource(), target, 1);
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Player not found."));
            return 0;
        }
    }

    private static int adminGiveTargetAmount(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(context, "player");
            int amount = IntegerArgumentType.getInteger(context, "amount");
            return giveDispatchBook(context.getSource(), target, amount);
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Invalid arguments."));
            return 0;
        }
    }

    private static int giveDispatchBook(CommandSourceStack source, ServerPlayer target, int amount) {
        ItemStack stack = new ItemStack(ModRegistry.MARKETPLACE_TRANSCEIVER.get(), Math.max(1, amount));
        boolean added = target.getInventory().add(stack);
        if (!added && !stack.isEmpty()) {
            target.drop(stack, false);
        }
        source.sendSuccess(() -> Component.literal(String.format("§aGave %dx [Trader's Dispatch Book] to %s.", amount, target.getScoreboardName())), true);
        target.sendSystemMessage(Component.literal(String.format("§6[Trading Post] You received %dx §e[Trader's Dispatch Book]§6.", amount)));
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAdminNode() {
        return Commands.literal("admin")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("spawn")
                .executes(MarketplaceCommands::adminSpawnMerchant)
                .then(Commands.literal("merchant").executes(MarketplaceCommands::adminSpawnMerchant))
                .then(Commands.literal("master").executes(MarketplaceCommands::adminSpawnMaster))
                .then(Commands.literal("banker").executes(MarketplaceCommands::adminSpawnBanker))
            )
            .then(Commands.literal("spawn_merchant").executes(MarketplaceCommands::adminSpawnMerchant))
            .then(Commands.literal("spawn_master").executes(MarketplaceCommands::adminSpawnMaster))
            .then(Commands.literal("spawn_banker").executes(MarketplaceCommands::adminSpawnBanker))
            .then(Commands.literal("open").executes(MarketplaceCommands::adminForceOpen))
            .then(Commands.literal("give")
                .executes(MarketplaceCommands::adminGiveSelf)
                .then(Commands.argument("player", EntityArgument.player())
                    .executes(MarketplaceCommands::adminGiveTarget)
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
                        .executes(MarketplaceCommands::adminGiveTargetAmount)
                    )
                )
            )
            .then(Commands.literal("give_license")
                .executes(MarketplaceCommands::adminGiveLicenseSelf)
                .then(Commands.argument("player", EntityArgument.players())
                    .executes(MarketplaceCommands::adminGiveLicenseTarget)
                )
            )
            .then(Commands.literal("reset_license")
                .executes(MarketplaceCommands::adminResetLicenseSelf)
                .then(Commands.literal("all").executes(MarketplaceCommands::adminResetLicenseAll))
                .then(Commands.literal("name")
                    .then(Commands.argument("player_name", StringArgumentType.word())
                        .executes(MarketplaceCommands::adminResetLicenseByName)
                    )
                )
                .then(Commands.argument("player", EntityArgument.players())
                    .executes(MarketplaceCommands::adminResetLicenseTargets)
                )
            );
    }

    private static int adminGiveLicenseSelf(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            return giveLicense(context.getSource(), player);
        }
        context.getSource().sendFailure(Component.literal("Must specify a player from the server console."));
        return 0;
    }

    private static int adminGiveLicenseTarget(CommandContext<CommandSourceStack> context) {
        try {
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "player");
            int count = 0;
            for (ServerPlayer target : targets) {
                giveLicense(context.getSource(), target);
                count++;
            }
            return count;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Player not found."));
            return 0;
        }
    }

    private static int giveLicense(CommandSourceStack source, ServerPlayer target) {
        ItemStack stack = com.omni.marketplace.item.MerchantsLicenseItem.createForPlayer(target);
        boolean added = target.getInventory().add(stack);
        if (!added) {
            target.containerMenu.setCarried(stack);
        }
        target.containerMenu.broadcastChanges();
        target.inventoryMenu.broadcastFullState();
        source.sendSuccess(() -> Component.literal(String.format("§aGave Imperial Merchant License deed to %s.", target.getScoreboardName())), true);
        target.sendSystemMessage(Component.literal("§6❖ [Trading Post] You received an §6Imperial Merchant License§6 deed! Right-click with it in your hand to consume and activate it."));
        return 1;
    }

    private static int adminResetLicenseSelf(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            return resetLicense(context.getSource(), Collections.singletonList(player));
        }
        context.getSource().sendFailure(Component.literal("§cUsage from console: /market admin reset_license <all | <player> | name <player_name>>"));
        return 0;
    }

    private static int adminResetLicenseAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        DatabaseManager db = DatabaseManager.getInstance();
        int dbCount = db.resetAllPlayerLicenses();

        int onlineCount = 0;
        if (source.getServer() != null) {
            for (ServerPlayer p : source.getServer().getPlayerList().getPlayers()) {
                stripLicenseItems(p);
                p.sendSystemMessage(Component.literal("§c❖ [Trading Post] Your Imperial Merchant License has been revoked and reset by an administrator!"));
                onlineCount++;
            }
        }

        final int finalOnlineCount = onlineCount;
        source.sendSuccess(() -> Component.literal(String.format("§aSuccessfully reset Merchant License for ALL players (%d accounts in database reset, %d online players updated).", dbCount, finalOnlineCount)), true);
        return 1;
    }

    private static int adminResetLicenseTargets(CommandContext<CommandSourceStack> context) {
        try {
            Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "player");
            return resetLicense(context.getSource(), targets);
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("§cPlayer not found."));
            return 0;
        }
    }

    private static int adminResetLicenseByName(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "player_name");
        DatabaseManager db = DatabaseManager.getInstance();
        boolean updated = db.resetPlayerLicenseByName(name);

        if (source.getServer() != null) {
            ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(name);
            if (online != null) {
                stripLicenseItems(online);
                online.sendSystemMessage(Component.literal("§c❖ [Trading Post] Your Imperial Merchant License has been revoked and reset by an administrator!"));
            }
        }

        if (updated) {
            source.sendSuccess(() -> Component.literal(String.format("§aMerchant License revoked and reset for '%s' in guild records.", name)), true);
            return 1;
        } else {
            source.sendFailure(Component.literal(String.format("§cNo account found with username '%s'.", name)));
            return 0;
        }
    }

    private static int resetLicense(CommandSourceStack source, Collection<ServerPlayer> targets) {
        DatabaseManager db = DatabaseManager.getInstance();
        int count = 0;
        for (ServerPlayer target : targets) {
            db.setPlayerLicensed(target.getUUID(), target.getScoreboardName(), false);
            stripLicenseItems(target);
            target.sendSystemMessage(Component.literal("§c❖ [Trading Post] Your Imperial Merchant License has been revoked and reset by an administrator!"));
            count++;
        }
        final int finalCount = count;
        if (count == 1) {
            ServerPlayer single = targets.iterator().next();
            source.sendSuccess(() -> Component.literal(String.format("§aMerchant License revoked and reset for %s.", single.getScoreboardName())), true);
        } else {
            source.sendSuccess(() -> Component.literal(String.format("§aMerchant License revoked and reset for %d players.", finalCount)), true);
        }
        return count;
    }

    public static void stripLicenseItems(ServerPlayer player) {
        if (player == null) return;

        // 1. Inventory & equipment & offhand
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(ModRegistry.MERCHANTS_LICENSE.get())) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }

        // 2. Cursor/carried slot
        if (player.containerMenu != null && !player.containerMenu.getCarried().isEmpty() && player.containerMenu.getCarried().is(ModRegistry.MERCHANTS_LICENSE.get())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }

        // 3. ContainerMenu open slots
        if (player.containerMenu != null) {
            for (int i = 0; i < player.containerMenu.slots.size(); i++) {
                ItemStack slotStack = player.containerMenu.getSlot(i).getItem();
                if (!slotStack.isEmpty() && slotStack.is(ModRegistry.MERCHANTS_LICENSE.get())) {
                    player.containerMenu.getSlot(i).set(ItemStack.EMPTY);
                }
            }
            player.containerMenu.broadcastChanges();
        }

        // 4. Ender chest
        for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++) {
            ItemStack stack = player.getEnderChestInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(ModRegistry.MERCHANTS_LICENSE.get())) {
                player.getEnderChestInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }
}
