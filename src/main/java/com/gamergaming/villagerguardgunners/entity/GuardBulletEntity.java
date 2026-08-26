package com.gamergaming.villagerguardgunners.entity;

import com.gamergaming.villagerguardgunners.init.ModConfigs;
import com.gamergaming.villagerguardgunners.init.ModEntities;
import com.tacz.guns.config.common.OtherConfig;
import com.tacz.guns.init.ModDamageTypes;
import com.tacz.guns.init.ModSounds;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

public class GuardBulletEntity extends AbstractHurtingProjectile implements IEntityAdditionalSpawnData {
    // public static final EntityType<GuardBulletEntity> TYPE = EntityType.Builder.<GuardBulletEntity>of(GuardBulletEntity::new, MobCategory.MISC).noSummon().noSave().fireImmune().sized(0.0625F, 0.0625F).clientTrackingRange(5).updateInterval(5).setShouldReceiveVelocityUpdates(false).build("guard_bullet");
    public static final float DEFAULT_DAMAGE = 6.0F;

    private float damage = DEFAULT_DAMAGE;
    private float friction = 0.99F;

    private Vec3 initialPosition;

    private LivingEntity owner;

    public GuardBulletEntity(EntityType<? extends AbstractHurtingProjectile> entityType, Level level) {
        super(entityType, level);
        this.initialPosition = this.position();
    }

    public GuardBulletEntity(Level level, LivingEntity shooter) {
        super(ModEntities.GUARD_BULLET.get(), shooter, 0, 0, 0, level);
        this.setOwner(shooter);
        this.setShootingOwner(shooter);
        this.setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
        this.initialPosition = this.position();
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    public float getDamage() {
        return this.damage;
    }

    public void setShootingOwner(LivingEntity owner) {
        this.owner = owner;
    }

    public LivingEntity getShootingOwner() {
        return this.owner;
    }

    @Override
    protected void defineSynchedData() {
        // No additional data to sync
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compoundTag) {
        // Deserialize initialPosition
        if (compoundTag.contains("InitialPosition")) {
            this.initialPosition = new Vec3(
                compoundTag.getDouble("InitialPositionX"),
                compoundTag.getDouble("InitialPositionY"),
                compoundTag.getDouble("InitialPositionZ")   
            );
        }

    }

    @Override
    public void addAdditionalSaveData(CompoundTag compoundTag) {
        // Serialize initialPosition
        if (this.initialPosition != null) {
            compoundTag.putDouble("InitialPositionX", this.initialPosition.x);
            compoundTag.putDouble("InitialPositionY", this.initialPosition.y);
            compoundTag.putDouble("InitialPositionZ", this.initialPosition.z);
        }

    }

    @Override
    public void tick() {
        super.tick();

        if (this.level().isClientSide) {
            // Client-side visual effects
            //this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
            if (ModConfigs.GENERAL.debugMode.get() && this.tickCount > 200) {
                this.discard();
            }
        } else {
            // Server-side logic
            if (this.tickCount > 100) {
                this.discard();
                return;
            }

            Vec3 motion = this.getDeltaMovement();
            HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hitResult.getType() != Type.MISS) {
                this.onHit(hitResult);
            }

            this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
            this.setDeltaMovement(motion.scale(friction)); // Apply friction
        }
    }

    @Override
    protected void onHit(HitResult result) {
        if (result.getType() == Type.ENTITY) {
            this.onHitEntity((EntityHitResult) result);
        } else if (result.getType() == Type.BLOCK) {
            this.onHitBlock((BlockHitResult) result);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public Vec3 getInitialPosition() {
        return this.initialPosition;
    }

    // @Override
    // protected void onHitEntity(EntityHitResult entityHitResult) {
    //     if (!this.level().isClientSide) {
    //         Entity hitEntity = entityHitResult.getEntity();
    //         if (hitEntity != null && hitEntity != this.getOwner()) {
    //             if (this.getOwner() instanceof LivingEntity owner) {
    //                 DamageSource source = this.damageSources().indirectMagic(this, owner);
    //                 entityHitResult.getEntity().hurt(source, this.getDamage());
    //             }
    //             this.discard();
    //         }
    //     }
    // }

    // @Override
    // protected void onHitEntity(EntityHitResult entityHitResult) {
    //     VillagerGuardGunners.LOGGER.info("Bullet hit entity");
    //     if (!this.level().isClientSide) {
    //         Entity hitEntity = entityHitResult.getEntity();
    //         if (hitEntity != null && hitEntity != this.getOwner()) {
    //             DamageSource source = hitEntity.damageSources().generic();
    //             Entity owner = this.getOwner();
    //             if (owner instanceof LivingEntity) {
    //                 source = this.damageSources().indirectMagic(this, owner);
    //             } else {
    //                 source = this.damageSources().mobProjectile(this, (LivingEntity) owner);
    //             }
    //             hitEntity.hurt(source, this.getDamage());
    //             this.discard();
    //         }
    //     }
    // }

    @Override
        protected void onHitEntity(EntityHitResult entityHitResult) {
            if (!this.level().isClientSide) {
                Entity hitEntity = entityHitResult.getEntity();
                if (hitEntity != null && hitEntity != this.getOwner()) {
                    DamageSource source;
                    Entity owner = this.getOwner();
                    if (owner instanceof LivingEntity) {
                        //source = this.damageSources().indirectMagic(this, (LivingEntity) owner);
                        // ModDamageTypes.BULLET is of type ResourceLocation<DamageType>
                        source = ModDamageTypes.Sources.bullet(this.level().registryAccess(), (Entity) this, (LivingEntity) owner, false);
                    } else {
                        source = ModDamageTypes.Sources.bullet(this.level().registryAccess(), (Entity) this, (LivingEntity) owner, false);
                        //source = this.damageSources().magic();
                    }
                    hitEntity.hurt(source, this.getDamage());
                    this.discard();
                }
            }
        }

    @Override
    protected void onHitBlock(BlockHitResult blockHitResult) {
        BlockPos pos = blockHitResult.getBlockPos();
        if (!this.level().isClientSide) {
            BlockState blockState = this.level().getBlockState(pos);
            int volume = OtherConfig.TARGET_SOUND_DISTANCE.get();
            BlockEntity blockEntity = blockState.hasBlockEntity() ? this.level().getBlockEntity(pos) : null;
            if (blockEntity != null) {
                blockEntity.getLevel().playSound(null, pos, ModSounds.TARGET_HIT.get(), SoundSource.BLOCKS, volume / 16.0f, this.level().random.nextFloat() * 0.1F + 0.9F);
            }
            if (!blockState.canBeReplaced()) {
                this.discard();
            }
        }
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        buffer.writeFloat(this.damage);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        this.damage = buffer.readFloat();
    }

    @Override
    public Packet getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}