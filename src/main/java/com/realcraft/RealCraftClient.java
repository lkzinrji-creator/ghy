package com.realcraft;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

public class RealCraftClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRenderers.register(RealCraftMod.TABLE_BE, RealCraftingTableRenderer::new);
        EntityRendererRegistry.register(RealCraftMod.PREVIEW_TARGET, InvisiblePreviewTargetRenderer::new);
    }

    /** Renderer intencionalmente vazio: o alvo só existe para o raycast de interação. */
    private static final class InvisiblePreviewTargetRenderer
            extends EntityRenderer<RealCraftPreviewTarget, EntityRenderState> {
        private InvisiblePreviewTargetRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.shadowRadius = 0.0f;
            this.shadowStrength = 0.0f;
        }

        @Override
        public EntityRenderState createRenderState() {
            return new EntityRenderState();
        }

        @Override
        public void submit(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                           CameraRenderState cameraState) {
            // Nada é desenhado; o item do resultado continua sendo desenhado pelo BlockEntityRenderer.
        }
    }
}
