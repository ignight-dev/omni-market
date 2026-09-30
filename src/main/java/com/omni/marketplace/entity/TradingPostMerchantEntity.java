package com.omni.marketplace.entity;

import com.omni.marketplace.db.DatabaseManager;
import com.omni.marketplace.db.model.MarketModels.AccountSummary;
import com.omni.marketplace.network.MarketPackets.OpenMarketplaceS2C;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

public class TradingPostMerchantEntity extends WanderingTrader {

    private boolean isTemporary = false;
    private int ticksRemaining = 12000; // 10 minutes (600s * 20t)
    private UUID summonerUuid = null;

    public TradingPostMerchantEntity(EntityType<? extends WanderingTrader> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
        this.setCustomName(Component.literal("§6❖ Guild Trading Post Merchant ❖"));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D); // Stays at stall
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
        // No wander goals - merchant stays anchored at the stall/summon spot
    }

    public void setTemporary(boolean temporary, int durationTicks, UUID summoner) {
        this.isTemporary = temporary;
        this.ticksRemaining = Math.max(20, durationTicks);
        this.summonerUuid = summoner;
        updateNametag();
    }

    public boolean isTemporary() {
        return isTemporary;
    }

    public int getTicksRemaining() {
        return ticksRemaining;
    }

    private void updateNametag() {
        if (isTemporary) {
            int totalSecs = Math.max(0, this.ticksRemaining / 20);
            int mins = totalSecs / 60;
            int secs = totalSecs % 60;
            this.setCustomName(Component.literal(String.format("§6Guild Merchant §e[%02d:%02d]", mins, secs)));
            this.setCustomNameVisible(true);
        } else {
            this.setCustomName(Component.literal("§6❖ Guild Trading Post Merchant ❖"));
            this.setCustomNameVisible(true);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide && this.isTemporary) {
            this.ticksRemaining--;

            if (this.ticksRemaining % 20 == 0) {
                updateNametag();
            }

            if (this.ticksRemaining <= 0) {
                // Expired: despawn with effects
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 1.0, getZ(), 25, 0.3, 0.5, 0.3, 0.05);
                    serverLevel.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1.0, getZ(), 30, 0.5, 0.5, 0.5, 0.1);
                    serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0F, 1.0F);

                    serverLevel.players().forEach(p -> {
                        if (p.distanceToSqr(this) < 400.0D) { // within 20 blocks
                            p.sendSystemMessage(Component.literal("§e[Trading Post] The temporary Trading Post Merchant has departed."));
                        }
                    });
                }
                this.discard();
            }
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            DatabaseManager db = DatabaseManager.getInstance();
            AccountSummary acc = db.getOrCreateAccount(serverPlayer.getUUID(), serverPlayer.getScoreboardName());

            // Open the Trading Post Screen and sync favorites
            PacketDistributor.sendToPlayer(serverPlayer, new OpenMarketplaceS2C(acc.copperBalance(), acc.vaultItemCount(), acc.vaultCopperAmount()));
            PacketDistributor.sendToPlayer(serverPlayer, new com.omni.marketplace.network.MarketPackets.SyncFavoritesS2C(new java.util.ArrayList<>(db.getPlayerFavorites(serverPlayer.getUUID()))));
            this.playSound(SoundEvents.VILLAGER_YES, 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Prevent damage from players and monsters to avoid merchant griefing
        if (source.getEntity() instanceof Player player && player.getAbilities().instabuild) {
            return super.hurt(source, amount); // Creative players can remove if needed
        }
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("TradingPostTemporary", this.isTemporary);
        tag.putInt("TradingPostTicksRemaining", this.ticksRemaining);
        if (this.summonerUuid != null) {
            tag.putUUID("TradingPostSummoner", this.summonerUuid);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("TradingPostTemporary")) {
            this.isTemporary = tag.getBoolean("TradingPostTemporary");
        }
        if (tag.contains("TradingPostTicksRemaining")) {
            this.ticksRemaining = tag.getInt("TradingPostTicksRemaining");
        }
        if (tag.hasUUID("TradingPostSummoner")) {
            this.summonerUuid = tag.getUUID("TradingPostSummoner");
        }
        updateNametag();
    }
}
