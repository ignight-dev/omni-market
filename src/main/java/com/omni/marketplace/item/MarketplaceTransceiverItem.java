package com.omni.marketplace.item;

import com.omni.marketplace.entity.TradingPostMerchantEntity;
import com.omni.marketplace.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class MarketplaceTransceiverItem extends Item {

    public MarketplaceTransceiverItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        BlockPos clickedPos = context.getClickedPos();
        BlockPos spawnPos = clickedPos.relative(context.getClickedFace());
        Vec3 target = new Vec3(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D);

        return summonMerchant(level, player, context.getHand(), target, context.getItemInHand());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        Vec3 forward = player.getLookAngle().scale(1.5D);
        Vec3 target = player.position().add(forward.x, 0, forward.z);

        InteractionResult res = summonMerchant(level, player, hand, target, held);
        if (res == InteractionResult.FAIL) {
            return InteractionResultHolder.fail(held);
        }
        return new InteractionResultHolder<>(res, held);
    }

    private InteractionResult summonMerchant(Level level, Player player, InteractionHand hand, Vec3 pos, ItemStack held) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!com.omni.marketplace.util.LicenseHelper.hasLicense(serverPlayer)) {
                serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c[Trading Post] Access Denied: You must possess a §6Merchant's License §cto authorize and dispatch a Trading Post Merchant! Purchase one from the Grand Merchant Master."));
                return InteractionResult.FAIL;
            }

            TradingPostMerchantEntity merchant = new TradingPostMerchantEntity(ModRegistry.TRADING_POST_MERCHANT.get(), level);
            merchant.moveTo(pos.x, pos.y, pos.z, player.getYRot() + 180.0F, 0.0F);
            merchant.setTemporary(true, 12000, player.getUUID()); // 12000 ticks = 10 minutes
            level.addFreshEntity(merchant);

            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.PORTAL, pos.x, pos.y + 1.0, pos.z, 35, 0.5, 0.5, 0.5, 0.1);
                serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.x, pos.y + 1.0, pos.z, 15, 0.3, 0.3, 0.3, 0.05);
            }

            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.2F);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.VILLAGER_YES, SoundSource.PLAYERS, 1.0F, 1.0F);

            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }

            serverPlayer.sendSystemMessage(Component.literal("§6[Trading Post] Express Trading Post Merchant summoned! Anyone may trade for the next 10 minutes."));
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7An enchanted guild ledger signed by imperial traders."));
        tooltip.add(Component.literal("§7Right-click on the ground to summon a"));
        tooltip.add(Component.literal("§6Trading Post Merchant §7for §e10 minutes§7."));
        tooltip.add(Component.literal("§8❖ Accessible by all nearby adventurers"));
        tooltip.add(Component.literal("§c⚠ Consumable on use (one-time dispatch)"));
        tooltip.add(Component.literal("§d★ Found in exploration chests or via royal decree"));
    }
}
