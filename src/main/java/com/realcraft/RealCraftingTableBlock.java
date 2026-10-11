package com.realcraft;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class RealCraftingTableBlock extends BaseEntityBlock {
    public RealCraftingTableBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(RealCraftingTableBlock::new);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RealCraftingTableBE(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, RealCraftMod.TABLE_BE, RealCraftingTableBE::serverTick);
    }

    /**
     * Os centros visuais das casas são 5, 8 e 11/16, conforme o renderer.
     * Cada casa recebe a metade da distância entre centros como sua área de clique.
     */
    private static int slotAt(Vec3 point, BlockPos pos) {
        double x = (point.x - pos.getX()) * 16.0;
        double z = (point.z - pos.getZ()) * 16.0;
        int col = (int) Math.floor((x - 3.5) / 3.0);
        int row = (int) Math.floor((z - 3.5) / 3.0);
        if (col < 0 || col > 2 || row < 0 || row > 2) return -1;
        return row * 3 + col;
    }

    /** Projeta a mira ao plano do tampo; usada tanto para cliques no bloco quanto no alvo do preview. */
    public static int slotUnderLook(Player player, BlockPos pos) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        if (Math.abs(look.y) < 1.0E-5) return -1;
        double distance = (pos.getY() + 1.001 - eye.y) / look.y;
        if (distance <= 0.0) return -1;
        return slotAt(eye.add(look.scale(distance)), pos);
    }

    /** Coloca uma unidade; chamado no servidor para cliques no bloco ou no alvo de preview. */
    public static InteractionResult placeOne(RealCraftingTableBE table, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, int slot) {
        if (slot < 0 || slot >= 9) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) return InteractionResult.PASS;
        if (!table.getItem(slot).isEmpty()) return InteractionResult.SUCCESS;

        if (!level.isClientSide()) {
            table.setItem(slot, held.copyWithCount(1));
            if (!player.getAbilities().instabuild) held.shrink(1);
            level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.6f, 1.3f);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Revalida e executa uma receita somente no servidor. O bloqueio por tick torna idempotentes
     * pacotes duplicados de interação (por exemplo, interact-at seguido de interact).
     */
    public static InteractionResult craftPreview(RealCraftingTableBE table, Level level,
                                                 BlockPos pos, Player player) {
        if (level.isClientSide()) {
            return table.getPreview().isEmpty() ? InteractionResult.PASS : InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

        RealCraftingTableBE.Match match = table.findMatch(serverLevel);
        if (match == null) return InteractionResult.PASS;
        long gameTime = level.getGameTime();
        if (!table.claimCraftForTick(gameTime)) return InteractionResult.SUCCESS;

        ItemStack result = match.recipe().assemble(match.input());
        if (result.isEmpty()) return InteractionResult.PASS;

        table.consumeMatch(match);
        ItemStack remainder = result.copy();
        if (!player.getInventory().add(remainder) && !remainder.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.15,
                    pos.getZ() + 0.5, remainder);
        }
        level.playSound(null, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT,
                SoundSource.BLOCKS, 0.8f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    /** Atacar o resultado usa a mesma validação e consumo, mas lança o produto no mundo. */
    public static InteractionResult craftPreviewDropped(RealCraftingTableBE table, Level level,
                                                        BlockPos pos, Player player) {
        return craftPreviewToWorld(table, level, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT);
    }

    /** O martelo toca a bigorna e, se houver receita pronta, fabrica e solta o resultado. */
    public static InteractionResult craftPreviewWithHammer(RealCraftingTableBE table, Level level,
                                                           BlockPos pos, Player player) {
        if (level.isClientSide()) {
            return table.getPreview().isEmpty() ? InteractionResult.PASS : InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel)) return InteractionResult.PASS;
        return craftPreviewToWorld(table, level, pos, SoundEvents.ANVIL_USE);
    }

    private static InteractionResult craftPreviewToWorld(RealCraftingTableBE table, Level level,
                                                         BlockPos pos, SoundEvent soundOnCraft) {
        if (level.isClientSide()) {
            return table.getPreview().isEmpty() ? InteractionResult.PASS : InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

        RealCraftingTableBE.Match match = table.findMatch(serverLevel);
        if (match == null) return InteractionResult.SUCCESS;
        long gameTime = level.getGameTime();
        if (!table.claimCraftForTick(gameTime)) return InteractionResult.SUCCESS;

        ItemStack result = match.recipe().assemble(match.input());
        if (result.isEmpty()) return InteractionResult.SUCCESS;

        table.consumeMatch(match);
        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.15,
                pos.getZ() + 0.5, result);
        level.playSound(null, pos, soundOnCraft, SoundSource.BLOCKS, 1.0f, 1.0f);
        return InteractionResult.SUCCESS;
    }

    private static int itemSlotAtRay(Player player, BlockPos pos, RealCraftingTableBE table) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getViewVector(1.0f).scale(6.0));
        int hitSlot = -1;
        double nearest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 9; i++) {
            if (table.getItem(i).isEmpty()) continue;
            int row = i / 3;
            int col = i % 3;
            double x = pos.getX() + (5.0 + 3.0 * col) / 16.0;
            double z = pos.getZ() + (5.0 + 3.0 * row) / 16.0;
            AABB itemBounds = new AABB(x - 0.12, pos.getY() + 0.99, z - 0.12,
                    x + 0.12, pos.getY() + 1.25, z + 0.12);
            var intersection = itemBounds.clip(start, end);
            if (intersection.isPresent()) {
                double distance = start.distanceToSqr(intersection.get());
                if (distance < nearest) {
                    nearest = distance;
                    hitSlot = i;
                }
            }
        }
        return hitSlot;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof RealCraftingTableBE table)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (stack.isEmpty()) return useWithoutItem(state, level, pos, player, hit);
        int slot = slotUnderLook(player, pos);
        if (slot >= 0) return placeOne(table, level, pos, player, hand, slot);
        // Um clique fora da grade não pode cair no comportamento vanilla de colocar o bloco na mesa.
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof RealCraftingTableBE table)) {
            return InteractionResult.PASS;
        }

        int slot = itemSlotAtRay(player, pos, table);
        if (slot < 0 && hit.getDirection() == Direction.UP) {
            slot = slotUnderLook(player, pos);
        }
        if (slot < 0 || table.getItem(slot).isEmpty()) return InteractionResult.PASS;

        if (!level.isClientSide()) {
            ItemStack removed = table.getItem(slot).copy();
            table.setItem(slot, ItemStack.EMPTY);
            if (!player.getInventory().add(removed) && !removed.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0,
                        pos.getZ() + 0.5, removed);
            }
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP,
                    SoundSource.BLOCKS, 0.5f, 1.2f);
        }
        return InteractionResult.SUCCESS;
    }
}
