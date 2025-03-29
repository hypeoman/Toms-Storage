package com.tom.storagemod.menu;

import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import com.tom.storagemod.Content;
import com.tom.storagemod.block.entity.InventoryCableConnectorBlockEntity;
import com.tom.storagemod.inventory.RemoteConnections;
import com.tom.storagemod.inventory.RemoteConnections.Channel;
import com.tom.storagemod.network.NetworkHandler;
import com.tom.storagemod.util.DataSlots;
import com.tom.storagemod.util.IDataReceiver;

public class InventoryLinkMenu extends AbstractContainerMenu implements IDataReceiver {
	private InventoryCableConnectorBlockEntity te;
	private Inventory pinv;
	private boolean sentList;
	public int beaconLvl;

	public InventoryLinkMenu(int id, Inventory playerInv) {
		this(id, playerInv, null);
	}

	public InventoryLinkMenu(int id, Inventory playerInv, InventoryCableConnectorBlockEntity tile) {
		super(Content.inventoryLink.get(), id);
		this.te = tile;
		this.pinv = playerInv;
		addDataSlot(DataSlots.create(v -> beaconLvl = v, () -> te != null ? te.getBeaconLevel() : 0));
	}

	@Override
	public void receive(CompoundTag tag) {
		if(pinv.player.isSpectator() || te == null)return;
		UUID id = tag.read("id", UUIDUtil.CODEC).orElse(null);
		if(id == null) {
			UUID chn = RemoteConnections.get(pinv.player.level()).makeChannel(tag.getStringOr("d", "No Name"), tag.getBooleanOr("p", false), pinv.player);
			te.setChannel(chn);
		} else if(tag.getBooleanOr("select", false)) {
			var c = RemoteConnections.get(pinv.player.level()).getChannel(id);
			if (c != null && c.canAccess(pinv.player))
				te.setChannel(id);
		} else if(tag.contains("p")) {
			RemoteConnections.get(pinv.player.level()).editChannel(id, tag.getBooleanOr("p", false), pinv.player.getUUID());
		} else {
			RemoteConnections.get(pinv.player.level()).removeChannel(id, pinv.player.getUUID());
		}
		sentList = false;
	}

	@Override
	public boolean stillValid(Player p_38874_) {
		return te != null ? te.stillValid(p_38874_) : true;
	}

	@Override
	public void broadcastChanges() {
		if(te == null)return;
		if(!sentList) {
			CompoundTag mainTag = new CompoundTag();
			UUID chn = te.getChannel();
			if(chn != null)
				mainTag.store("selected", UUIDUtil.CODEC, chn);

			ListTag list = new ListTag();
			RemoteConnections.get(pinv.player.level()).streamChannels(pinv.player).map(LinkChannel::new).forEach(c -> {
				CompoundTag t = new CompoundTag();
				c.saveToClient(t);
				list.add(t);
			});
			mainTag.put("list", list);

			NetworkHandler.sendTo((ServerPlayer) pinv.player, mainTag);
			sentList = true;
		}
		super.broadcastChanges();
	}

	@Override
	public ItemStack quickMoveStack(Player p_38941_, int p_38942_) {
		return ItemStack.EMPTY;
	}

	public static class LinkChannel {
		public UUID id;
		public String displayName;
		public boolean publicChannel;
		public UUID owner;
		public String ownerName;

		public LinkChannel(Entry<UUID, Channel> e) {
			Channel c = e.getValue();
			this.id = e.getKey();
			this.displayName = c.displayName;
			this.publicChannel = c.publicChannel;
			this.owner = c.owner;
			this.ownerName = c.ownerName;
		}

		public LinkChannel(CompoundTag tag) {
			this.id = tag.read("id", UUIDUtil.CODEC).orElse(null);
			this.displayName = tag.getStringOr("d", "");
			this.publicChannel = tag.getBooleanOr("p", false);
			this.owner = tag.read("o", UUIDUtil.CODEC).orElse(null);
			this.ownerName = tag.getStringOr("on", "");
		}

		public LinkChannel(boolean isPublic, String name) {
			this.publicChannel = isPublic;
			this.displayName = name;
		}

		public void saveToClient(CompoundTag t) {
			t.store("id", UUIDUtil.CODEC, id);
			t.putString("d", displayName);
			t.putBoolean("p", publicChannel);
			t.store("o", UUIDUtil.CODEC, owner);
			t.putString("on", ownerName == null ? "" : ownerName);
		}

		public void saveToServer(CompoundTag t) {
			t.putBoolean("p", publicChannel);
			t.putString("d", displayName);
		}

		public static void loadAll(ListTag list, Map<UUID, LinkChannel> connections) {
			for (int i = 0; i < list.size(); i++) {
				CompoundTag t = list.getCompoundOrEmpty(i);
				UUID channel = t.read("id", UUIDUtil.CODEC).orElse(null);
				connections.put(channel, new LinkChannel(t));
			}
		}
	}
}
