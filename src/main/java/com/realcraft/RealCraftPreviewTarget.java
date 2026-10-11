package com.realcraft;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Alvo invisível persistente; seu centro e dimensões acompanham o preview que ele representa. */
public final class RealCraftPreviewTarget extends Entity {
    private static final float BLOCK_ITEM_SIZE = 0.16f;
    private static final float FLAT_ITEM_SIZE = 0.30f;
    private static final double PREVIEW_CENTER_Y = 1.45;
    private static final double BOB_AMPLITUDE = 0.04;
    private static final double BOB_PERIOD = 10.0;
    private static final double OUTER_RENDER_SCALE = 0.6;

    public RealCraftPreviewTarget(EntityType<? extends RealCraftPreviewTarget> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        // A entidade não precisa de estado próprio: tipo e posição são sincronizados pelo Minecraft.
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        // Sua posição persistida identifica a mesa proprietária.
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        // Sem campos adicionais.
    }

    private ItemStack previewStack(BlockPos tablePos) {
        if (level().getBlockEntity(tablePos) instanceof RealCraftingTableBE table) {
            return table.getPreview();
        }
        return ItemStack.EMPTY;
    }

    private boolean previewIsBlockItem(BlockPos tablePos) {
        ItemStack preview = previewStack(tablePos);
        return !preview.isEmpty() && preview.getItem() instanceof BlockItem;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        // Modelos de bloco usam o transform GROUND vanilla de 0.25; itens gerados usam 0.5.
        float size = previewIsBlockItem(getTablePos()) ? BLOCK_ITEM_SIZE : FLAT_ITEM_SIZE;
        return EntityDimensions.scalable(size, size);
    }

    private double renderedCenterY(BlockPos tablePos, double time) {
        // Transformações GROUND vanilla: blocos deslocam 3/16 e itens gerados 2/16.
        double translationPixels = previewIsBlockItem(tablePos) ? 3.0 : 2.0;
        double modelOffset = translationPixels / 16.0 * OUTER_RENDER_SCALE;
        return tablePos.getY() + PREVIEW_CENTER_Y + modelOffset
                + Math.sin(time / BOB_PERIOD) * BOB_AMPLITUDE;
    }

    private double targetBottomY(BlockPos tablePos, double time) {
        return renderedCenterY(tablePos, time) - getDimensions(getPose()).height() / 2.0;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean skipAttackInteraction(Entity entity) {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource damageSource, float amount) {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        BlockPos tablePos = getTablePos();
        refreshDimensions();
        double targetY = targetBottomY(tablePos, level().getGameTime());
        double targetX = tablePos.getX() + 0.5;
        double targetZ = tablePos.getZ() + 0.5;
        if (Math.abs(getX() - targetX) > 1.0E-5
                || Math.abs(getY() - targetY) > 1.0E-5
                || Math.abs(getZ() - targetZ) > 1.0E-5) {
            setPos(targetX, targetY, targetZ);
        }

        if (level().isClientSide() || level().getGameTime() % 20L != 0L) return;
        BlockState state = level().getBlockState(tablePos);
        if (!state.is(RealCraftMod.TABLE)) {
            discard();
            return;
        }
        if (level().getBlockEntity(tablePos) instanceof RealCraftingTableBE table
                && table.getPreview().isEmpty()) {
            discard();
        }
        // Se o bloco existe mas seu BlockEntity ainda não carregou, aguarda o ticker reparar o alvo.
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        return InteractionResult.PASS;
    }

    /** O alvo fica imediatamente acima do bloco dono; abaixo da sua posição é a mesa. */
    public BlockPos getTablePos() {
        return BlockPos.containing(getX(), getY(), getZ()).below();
    }

    /** Só aceita mira dentro dos limites do modelo visível, não em toda uma caixa aproximada. */
    public boolean isAimedAtVisiblePreview(Player player, BlockPos tablePos) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        if (Math.abs(look.y) < 1.0E-5) return false;

        double time = level().getGameTime();
        double previewY = renderedCenterY(tablePos, time);
        double distance = (previewY - eye.y) / look.y;
        if (distance <= 0.0) return false;

        Vec3 projected = eye.add(look.scale(distance));
        double centerX = tablePos.getX() + 0.5;
        double centerZ = tablePos.getZ() + 0.5;
        double halfSize = getDimensions(getPose()).width() / 2.0;
        return Math.abs(projected.x - centerX) <= halfSize
                && Math.abs(projected.z - centerZ) <= halfSize;
    }

    private static AABB searchArea(BlockPos tablePos) {
        double x = tablePos.getX() + 0.5;
        double z = tablePos.getZ() + 0.5;
        return new AABB(x - 0.40, tablePos.getY() + 1.0, z - 0.40,
                x + 0.40, tablePos.getY() + 1.90, z + 0.40);
    }

    private static double spawnBottomY(ServerLevel level, BlockPos tablePos) {
        ItemStack preview = level.getBlockEntity(tablePos) instanceof RealCraftingTableBE table
                ? table.getPreview() : ItemStack.EMPTY;
        boolean blockItem = !preview.isEmpty() && preview.getItem() instanceof BlockItem;
        float height = blockItem ? BLOCK_ITEM_SIZE : FLAT_ITEM_SIZE;
        double translationPixels = blockItem ? 3.0 : 2.0;
        double centerY = tablePos.getY() + PREVIEW_CENTER_Y
                + translationPixels / 16.0 * OUTER_RENDER_SCALE
                + Math.sin(level.getGameTime() / BOB_PERIOD) * BOB_AMPLITUDE;
        return centerY - height / 2.0;
    }

    /** Garante exatamente um alvo por mesa com resultado; remove-o quando não há resultado. */
    public static void syncForTable(ServerLevel level, BlockPos tablePos, boolean shouldExist) {
        BlockPos immutablePos = tablePos.immutable();
        List<RealCraftPreviewTarget> targets = level.getEntitiesOfClass(
                RealCraftPreviewTarget.class,
                searchArea(immutablePos),
                target -> target.getTablePos().equals(immutablePos));

        if (!shouldExist) {
            for (RealCraftPreviewTarget target : targets) target.discard();
            return;
        }

        RealCraftPreviewTarget keep = null;
        for (RealCraftPreviewTarget target : targets) {
            if (keep == null) keep = target;
            else target.discard();
        }

        double targetX = immutablePos.getX() + 0.5;
        double targetY = spawnBottomY(level, immutablePos);
        double targetZ = immutablePos.getZ() + 0.5;
        if (keep == null) {
            keep = RealCraftMod.PREVIEW_TARGET.create(level, EntitySpawnReason.TRIGGERED);
            if (keep == null) return;
            keep.setPos(targetX, targetY, targetZ);
            keep.refreshDimensions();
            keep.setInvulnerable(true);
            if (!level.addFreshEntity(keep)) keep.discard();
        } else {
            keep.refreshDimensions();
            if (Math.abs(keep.getX() - targetX) > 1.0E-5
                    || Math.abs(keep.getY() - targetY) > 1.0E-5
                    || Math.abs(keep.getZ() - targetZ) > 1.0E-5) {
                keep.setPos(targetX, targetY, targetZ);
            }
        }
    }
}
