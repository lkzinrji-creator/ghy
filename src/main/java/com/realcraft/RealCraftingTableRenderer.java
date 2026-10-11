package com.realcraft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

public class RealCraftingTableRenderer implements BlockEntityRenderer<RealCraftingTableBE, RealCraftingTableRenderState> {

    public RealCraftingTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public RealCraftingTableRenderState createRenderState() {
        return new RealCraftingTableRenderState();
    }

    @Override
    public void extractRenderState(RealCraftingTableBE be, RealCraftingTableRenderState state, float tickProgress,
                                   Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(be, state, tickProgress, cameraPos, crumblingOverlay);

        Level level = be.getLevel();
        if (level == null) return;
        ItemModelResolver resolver = Minecraft.getInstance().getItemModelResolver();
        int seed = (int) be.getBlockPos().asLong();

        // luz do ar logo acima da mesa (a luz do proprio bloco opaco seria 0)
        BlockPos above = be.getBlockPos().above();
        state.lightAbove = (level.getBrightness(LightLayer.SKY, above) << 20)
                | (level.getBrightness(LightLayer.BLOCK, above) << 4);
        state.time = level.getGameTime() + tickProgress;

        for (int i = 0; i < 9; i++) {
            ItemStack s = be.getItem(i);
            if (s.isEmpty()) {
                state.items[i].clear();
            } else {
                resolver.updateForTopItem(state.items[i], s, ItemDisplayContext.FIXED, level, null, seed + i);
                state.flat[i] = !(s.getItem() instanceof BlockItem);
            }
        }

        ItemStack prev = be.getPreview();
        if (prev.isEmpty()) {
            state.preview.clear();
        } else {
            resolver.updateForTopItem(state.preview, prev, ItemDisplayContext.GROUND, level, null, seed);
        }
    }

    @Override
    public void submit(RealCraftingTableRenderState state, PoseStack pose, SubmitNodeCollector collector,
                       CameraRenderState cameraState) {
        // 9 itens deitados em cima da mesa
        for (int i = 0; i < 9; i++) {
            if (state.items[i].isEmpty()) continue;
            int r = i / 3, c = i % 3;
            // Centros visuais ajustados um pouco para dentro nas casas externas da textura.
            double x = (5.0 + 3.0 * c) / 16.0;
            double z = (5.0 + 3.0 * r) / 16.0;

            pose.pushPose();
            if (state.flat[i]) {
                pose.translate(x, 1.0 + 0.02, z);
                pose.mulPose(Axis.XP.rotationDegrees(-90));
            } else {
                pose.translate(x, 1.0 + 0.05, z);
            }
            // Mantem os modelos dentro da celula (4/16 do tampo), sem que blocos ou itens longos se sobreponham.
            float itemScale = state.flat[i] ? 0.18f : 0.20f;
            pose.scale(itemScale, itemScale, itemScale);
            state.items[i].submit(pose, collector, state.lightAbove, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }

        // Previa do resultado flutuando e girando
        if (!state.preview.isEmpty()) {
            pose.pushPose();
            pose.translate(0.5, 1.45 + Math.sin(state.time / 10.0) * 0.04, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(state.time * 3f));
            pose.scale(0.6f, 0.6f, 0.6f);
            state.preview.submit(pose, collector, state.lightAbove, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
