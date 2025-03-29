package com.tom.storagemod.block;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.serialization.MapCodec;

import com.tom.storagemod.block.entity.StorageTerminalBlockEntity;
import com.tom.storagemod.client.ClientUtil;

public class StorageTerminalBlock extends AbstractStorageTerminalBlock {
	public static final MapCodec<StorageTerminalBlock> CODEC = simpleCodec(StorageTerminalBlock::new);

	public StorageTerminalBlock(Block.Properties pr) {
		super(pr);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new StorageTerminalBlockEntity(pos, state);
	}

	@Override
	public void appendHoverText(ItemStack itemStack, TooltipContext tooltipContext, Consumer<Component> tooltip,
			TooltipFlag tooltipFlag) {
		ClientUtil.tooltip("storage_terminal", tooltip);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}
}
