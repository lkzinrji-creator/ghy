package com.realcraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RealCraftingTableBE extends BlockEntity {
    public record Match(CraftingRecipe recipe, CraftingInput input, int[] src) {}

    private final NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
    /** Calculado no servidor e enviado ao cliente para o renderer. */
    private ItemStack preview = ItemStack.EMPTY;
    private long lastCraftGameTime = Long.MIN_VALUE;
    private long lastTargetSyncGameTime = Long.MIN_VALUE;

    public RealCraftingTableBE(BlockPos pos, BlockState state) {
        super(RealCraftMod.TABLE_BE, pos, state);
    }

    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    public NonNullList<ItemStack> getItems() {
        return items;
    }

    public ItemStack getPreview() {
        return preview;
    }

    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= items.size()) return;
        items.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        if (level instanceof ServerLevel serverLevel) recomputePreview(serverLevel);
        setChanged();
    }

    private void recomputePreview(ServerLevel serverLevel) {
        Match match = findMatch(serverLevel);
        preview = match == null ? ItemStack.EMPTY : match.recipe().assemble(match.input()).copy();
        syncPreviewTarget(serverLevel);
    }

    private void syncPreviewTarget(ServerLevel serverLevel) {
        RealCraftPreviewTarget.syncForTable(serverLevel, worldPosition, !preview.isEmpty());
        lastTargetSyncGameTime = serverLevel.getGameTime();
    }

    /** Repara alvos removidos e reconstitui-os quando o chunk carrega novamente. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, RealCraftingTableBE table) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        long now = serverLevel.getGameTime();
        if (table.lastTargetSyncGameTime == Long.MIN_VALUE) {
            table.recomputePreview(serverLevel);
            table.setChanged();
        } else if (now - table.lastTargetSyncGameTime >= 20L) {
            table.syncPreviewTarget(serverLevel);
        }
    }

    /** Faz dois pacotes de interação da mesma mesa no mesmo tick produzirem no máximo um craft. */
    public boolean claimCraftForTick(long gameTime) {
        if (lastCraftGameTime == gameTime) return false;
        lastCraftGameTime = gameTime;
        return true;
    }

    /** Consome os ingredientes/remainders da receita e recalcula o preview uma única vez. */
    public void consumeMatch(Match match) {
        for (int i = 0; i < 9; i++) {
            int sourceSlot = match.src()[i];
            ItemStack used = items.get(sourceSlot);
            if (used.isEmpty()) continue;
            ItemStack craftingRemainder = used.getCraftingRemainder().create();
            items.set(sourceSlot, craftingRemainder.isEmpty()
                    ? ItemStack.EMPTY : craftingRemainder.copy());
        }
        if (level instanceof ServerLevel serverLevel) recomputePreview(serverLevel);
        setChanged();
    }

    /** Busca receita 3×3; testa as quatro rotações e mantém o mapeamento dos slots de origem. */
    public Match findMatch(ServerLevel serverLevel) {
        boolean any = false;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                any = true;
                break;
            }
        }
        if (!any) return null;

        int[] sourceSlots = new int[9];
        for (int i = 0; i < 9; i++) sourceSlots[i] = i;

        for (int rotation = 0; rotation < 4; rotation++) {
            List<ItemStack> grid = new ArrayList<>(9);
            for (int i = 0; i < 9; i++) grid.add(items.get(sourceSlots[i]).copy());
            CraftingInput input = CraftingInput.of(3, 3, grid);
            Optional<RecipeHolder<CraftingRecipe>> recipe = serverLevel.getServer()
                    .getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, serverLevel);
            if (recipe.isPresent()) {
                return new Match(recipe.get().value(), input, sourceSlots.clone());
            }

            int[] rotated = new int[9];
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    rotated[row * 3 + col] = sourceSlots[(2 - col) * 3 + row];
                }
            }
            sourceSlots = rotated;
        }
        return null;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.store("Preview", ItemStack.OPTIONAL_CODEC, preview);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (int i = 0; i < 9; i++) items.set(i, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        preview = input.read("Preview", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        lastCraftGameTime = Long.MIN_VALUE;
        lastTargetSyncGameTime = Long.MIN_VALUE;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level == null) return;
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_ALL);
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel) {
            RealCraftPreviewTarget.syncForTable(serverLevel, worldPosition, false);
        }
        super.setRemoved();
    }
}
