package com.omni.marketplace.entity;

import com.omni.marketplace.network.MarketPackets.OpenMerchantMasterS2C;
import com.omni.marketplace.util.EmeraldHelper;
import com.omni.marketplace.util.LicenseHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public class MerchantMasterEntity extends WanderingTrader {

    public MerchantMasterEntity(EntityType<? extends WanderingTrader> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
        this.setCustomName(Component.literal("§6❖ Grand Merchant Master ❖"));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D); // Stationary grand master
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            int totalEmeralds = EmeraldHelper.getTotalEmeralds(serverPlayer);
            int emeraldBlocks = EmeraldHelper.getEmeraldBlocks(serverPlayer);
            boolean hasLicense = LicenseHelper.hasLicense(serverPlayer);
            int bankRows = com.omni.marketplace.db.DatabaseManager.getInstance().getBankRows(serverPlayer.getUUID());

            PacketDistributor.sendToPlayer(serverPlayer, new OpenMerchantMasterS2C(totalEmeralds, emeraldBlocks, hasLicense, bankRows));
            this.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player player && player.getAbilities().instabuild) {
            return super.hurt(source, amount);
        }
        return false;
    }
}
