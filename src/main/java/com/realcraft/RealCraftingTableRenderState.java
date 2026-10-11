package com.realcraft;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public class RealCraftingTableRenderState extends BlockEntityRenderState {
    public final ItemStackRenderState[] items = new ItemStackRenderState[9];
    public final boolean[] flat = new boolean[9];
    public final ItemStackRenderState preview = new ItemStackRenderState();
    public int lightAbove;
    public float time;

    public RealCraftingTableRenderState() {
        for (int i = 0; i < 9; i++) items[i] = new ItemStackRenderState();
    }
}
