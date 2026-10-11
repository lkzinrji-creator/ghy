package com.realcraft;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class RealCraftMod implements ModInitializer {
    public static final String MODID = "realcraft";

    public static final ResourceKey<Block> TABLE_KEY = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath(MODID, "real_crafting_table"));

    public static final Block TABLE = Registry.register(BuiltInRegistries.BLOCK, TABLE_KEY,
            new RealCraftingTableBlock(
                    BlockBehaviour.Properties.ofFullCopy(Blocks.CRAFTING_TABLE).setId(TABLE_KEY)));

    public static final BlockEntityType<RealCraftingTableBE> TABLE_BE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(MODID, "real_crafting_table"),
            FabricBlockEntityTypeBuilder.<RealCraftingTableBE>create(RealCraftingTableBE::new, TABLE).build());

    public static final ResourceKey<EntityType<?>> PREVIEW_TARGET_KEY = ResourceKey.create(
            Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(MODID, "preview_target"));

    /** Caixa compacta centrada na mesma posição do resultado flutuante; salva junto com o chunk. */
    public static final EntityType<RealCraftPreviewTarget> PREVIEW_TARGET = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            PREVIEW_TARGET_KEY,
            EntityType.Builder.<RealCraftPreviewTarget>of(RealCraftPreviewTarget::new, MobCategory.MISC)
                    .sized(0.16f, 0.16f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .noLootTable()
                    .build(PREVIEW_TARGET_KEY));

    public static final ResourceKey<Item> STONE_HAMMER_KEY = ResourceKey.create(
            Registries.ITEM, Identifier.fromNamespaceAndPath(MODID, "stone_hammer"));
    public static final Item STONE_HAMMER = Registry.register(
            BuiltInRegistries.ITEM,
            STONE_HAMMER_KEY,
            new Item(new Item.Properties().setId(STONE_HAMMER_KEY).stacksTo(1)));

    @Override
    public void onInitialize() {
        // SUCCESS nos dois lados impede a UI vanilla; no servidor, a primeira interação também
        // é encaminhada para a mesa nova, portanto colocar um item não exige um segundo clique.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            BlockPos pos = hit.getBlockPos();
            ItemStack held = player.getItemInHand(hand);
            boolean usingHammer = held.is(STONE_HAMMER);
            if (level.getBlockState(pos).is(Blocks.CRAFTING_TABLE)) {
                if (level.isClientSide()) return InteractionResult.SUCCESS;

                level.setBlockAndUpdate(pos, TABLE.defaultBlockState());
                if (level.getBlockEntity(pos) instanceof RealCraftingTableBE table) {
                    if (usingHammer) {
                        return RealCraftingTableBlock.craftPreviewWithHammer(table, level, pos, player);
                    }
                    if (!held.isEmpty()) {
                        int slot = RealCraftingTableBlock.slotUnderLook(player, pos);
                        RealCraftingTableBlock.placeOne(table, level, pos, player, hand, slot);
                    }
                }
                return InteractionResult.SUCCESS;
            }

            if (!level.getBlockState(pos).is(TABLE)) {
                return InteractionResult.PASS;
            }
            if (usingHammer) {
                if (level.isClientSide()) return InteractionResult.SUCCESS;
                if (level.getBlockEntity(pos) instanceof RealCraftingTableBE table) {
                    return RealCraftingTableBlock.craftPreviewWithHammer(table, level, pos, player);
                }
                return InteractionResult.SUCCESS;
            }
            if (held.isEmpty()) {
                if (level.isClientSide()) return InteractionResult.SUCCESS;
                if (player.isShiftKeyDown()) return InteractionResult.PASS;
                if (level.getBlockEntity(pos) instanceof RealCraftingTableBE table
                        && !table.getPreview().isEmpty()) {
                    return RealCraftingTableBlock.craftPreviewDropped(table, level, pos, player);
                }
                // Sem resultado, deixa o bloco tratar a retirada normal de ingrediente.
                return InteractionResult.PASS;
            }
            if (level.getBlockEntity(pos) instanceof RealCraftingTableBE table) {
                int slot = RealCraftingTableBlock.slotUnderLook(player, pos);
                RealCraftingTableBlock.placeOne(table, level, pos, player, hand, slot);
            }
            // Mesmo fora dos slots, não deixar o BlockItem vanilla ser colocado em cima da mesa.
            return InteractionResult.SUCCESS;
        });

        // O renderer não é um alvo. Este callback só age quando o raycast selecionou a entidade
        // invisível posicionada sobre um preview e a mira também cruza a região visual do resultado.
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(entity instanceof RealCraftPreviewTarget target)) return InteractionResult.PASS;
            BlockPos pos = target.getTablePos();
            boolean usingHammer = player.getItemInHand(hand).is(STONE_HAMMER);
            // A própria colisão da entidade já confirma que a mira atingiu o alvo do preview.
            // Não aplique uma segunda projeção, que podia rejeitar o clique válido com o martelo.
            if (!usingHammer && !target.isAimedAtVisiblePreview(player, pos)) return InteractionResult.PASS;

            if (level.isClientSide()) {
                // Confirma o clique do cliente para que o pacote de interação chegue ao servidor.
                return InteractionResult.SUCCESS;
            }
            if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
            if (!(serverLevel.getBlockEntity(pos) instanceof RealCraftingTableBE table)
                    || table.getPreview().isEmpty()
                    || !serverLevel.getBlockState(pos).is(TABLE)) {
                target.discard();
                return InteractionResult.SUCCESS;
            }

            if (usingHammer) {
                return RealCraftingTableBlock.craftPreviewWithHammer(table, level, pos, player);
            }

            // Item na mão: interação com o alvo não crafta; coloca no slot sob o raio.
            if (!player.getItemInHand(hand).isEmpty()) {
                int slot = RealCraftingTableBlock.slotUnderLook(player, pos);
                InteractionResult placed = RealCraftingTableBlock.placeOne(
                        table, level, pos, player, hand, slot);
                return placed == InteractionResult.PASS ? InteractionResult.SUCCESS : placed;
            }

            // Mão vazia: receita revalidada e executada uma única vez no servidor.
            return RealCraftingTableBlock.craftPreviewDropped(table, level, pos, player);
        });

        // Ataque esquerdo no item flutuante: revalida e fabrica no servidor, soltando o produto.
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (!(entity instanceof RealCraftPreviewTarget target)) return InteractionResult.PASS;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;

            BlockPos pos = target.getTablePos();
            if (!target.isAimedAtVisiblePreview(player, pos)) return InteractionResult.SUCCESS;
            if (!(serverLevel.getBlockEntity(pos) instanceof RealCraftingTableBE table)
                    || table.getPreview().isEmpty()
                    || !serverLevel.getBlockState(pos).is(TABLE)) {
                target.discard();
                return InteractionResult.SUCCESS;
            }
            return RealCraftingTableBlock.craftPreviewDropped(table, level, pos, player);
        });

        // Ingredientes caem ao quebrar a mesa; o BlockEntity remove o alvo invisível no setRemoved.
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (!level.isClientSide() && blockEntity instanceof RealCraftingTableBE table) {
                for (ItemStack stack : table.getItems()) {
                    if (!stack.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0,
                                pos.getZ() + 0.5, stack.copy());
                    }
                }
                if (level instanceof ServerLevel serverLevel) {
                    RealCraftPreviewTarget.syncForTable(serverLevel, pos, false);
                }
            }
            return true;
        });
    }
}
