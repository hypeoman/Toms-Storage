package com.tom.storagemod.inventory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.tom.storagemod.inventory.IInventoryAccess.IChangeNotifier;
import com.tom.storagemod.inventory.IInventoryAccess.IInventoryChangeTracker;
import com.tom.storagemod.inventory.IInventoryAccess.IMultiThreadedTracker;
import com.tom.storagemod.inventory.InventoryImage.FabricStack;
import com.tom.storagemod.inventory.InventoryImage.InvState;
import com.tom.storagemod.inventory.filter.ItemPredicate;

public class InventoryChangeTracker implements IInventoryChangeTracker, IChangeNotifier, IMultiThreadedTracker<InventoryImage, InvState> {
	public static final InventoryChangeTracker NULL = new InventoryChangeTracker(null);
	private Storage<ItemVariant> storage;
	private long lastUpdate, lastVersion, lastChange;
	private Map<StorageView<ItemVariant>, StoredItemStack> lastItems = new HashMap<>();

	public InventoryChangeTracker(Storage<ItemVariant> itemHandler) {
		storage = itemHandler;
	}

	@Override
	public long getChangeTracker(Level level) {
		Storage<ItemVariant> h = storage;
		if (h == null)return 0L;
		if (lastUpdate != level.getGameTime()) {
			long ver = h.getVersion();
			if (ver != lastVersion) {
				boolean change = false;
				for (StorageView<ItemVariant> is : h) {
					change |= updateChange(is);
				}
				if (change)lastChange = System.nanoTime();
				lastUpdate = level.getGameTime();
				lastVersion = ver;
			}
		}
		return lastChange;
	}

	protected boolean checkFilter(StorageView<ItemVariant> stack) {
		return checkFilter(new StoredItemStack(stack.getResource().toStack(), stack.getAmount(), stack.getResource().hashCode()));
	}

	protected boolean checkFilter(StoredItemStack stack) {
		return true;
	}

	protected int getCount(StorageView<ItemVariant> is) {
		return (int) is.getAmount();
	}

	protected int getCount(FabricStack is) {
		return (int) is.count();
	}

	private boolean updateChange(StorageView<ItemVariant> iv) {
		StorageView<ItemVariant> uv = iv.getUnderlyingView();
		StoredItemStack li = lastItems.get(uv);
		if (!iv.isResourceBlank() && checkFilter(iv)) {
			int cnt = getCount(iv);
			if (li == null || !ItemStack.isSameItemSameComponents(li.getStack(), iv.getResource().toStack())) {
				lastItems.put(uv, new StoredItemStack(iv.getResource().toStack(), cnt, iv.getResource().hashCode()));
				return true;
			} else if(li.getQuantity() != cnt) {
				li.setCount(cnt);
				return true;
			}
		} else if (li != null) {
			lastItems.put(uv, null);
			return true;
		}
		return false;
	}

	protected StorageView<ItemVariant> getSlotHandler(StorageView<ItemVariant> def) {
		return def;
	}

	protected Storage<ItemVariant> getSlotHandler(Storage<ItemVariant> def) {
		return def;
	}

	@Override
	public Stream<StoredItemStack> streamWrappedStacks(boolean parallel) {
		Storage<ItemVariant> h = storage;
		if (h == null)return Stream.empty();
		return lastItems.values().stream().filter(e -> e != null);
	}

	@Override
	public long countItems(StoredItemStack filter) {
		Storage<ItemVariant> h = storage;
		if (h == null)return 0;
		long c = 0;
		for (StoredItemStack is : lastItems.values()) {
			if (is != null && is.equalItem(filter))c += is.getQuantity();
		}
		return c;
	}

	@Override
	public InventorySlot findSlot(ItemPredicate filter, boolean findEmpty) {
		return findSlot(filter, findEmpty, Collections.emptySet());
	}

	private InventorySlot findSlot(ItemPredicate filter, boolean findEmpty, Set<StorageView<ItemVariant>> seen) {
		Storage<ItemVariant> h = storage;
		if (h == null)return null;
		for (var slot : h) {
			if (slot.isResourceBlank()) {
				if (findEmpty) {
					if (seen.contains(slot.getUnderlyingView()))continue;
					return new InventorySlot(getSlotHandler(h), getSlotHandler(slot), this, seen);
				}
				continue;
			}
			if (getCount(slot) < 1)continue;
			if (seen.contains(slot.getUnderlyingView()))continue;
			ItemVariant iv = slot.getResource();
			if(filter.test(new StoredItemStack(iv.toStack(), slot.getAmount(), slot.hashCode()))) {
				try (Transaction tr = Transaction.openOuter()) {
					if (slot.extract(iv, 1, tr) == 1)
						return new InventorySlot(getSlotHandler(h), getSlotHandler(slot), this, seen);
				}
			}
			continue;
		}
		return null;
	}

	@Override
	public InventorySlot findSlotAfter(InventorySlot slotIn, ItemPredicate filter, boolean findEmpty, boolean loop) {
		if (slotIn == null)return findSlot(filter, findEmpty);
		var sl = findSlot(filter, findEmpty, slotIn.nextSlot());
		if (sl == null && loop) {
			return findSlot(filter, findEmpty);
		}
		return sl;
	}

	@Override
	public InventorySlot findSlotDest(StoredItemStack forStack) {
		return findSlotDest(forStack, Collections.emptySet());
	}

	private InventorySlot findSlotDest(StoredItemStack forStack, Set<StorageView<ItemVariant>> seen) {
		Storage<ItemVariant> h = storage;
		if (h == null)return null;
		if (!checkFilter(forStack))return null;
		ItemVariant iv = ItemVariant.of(forStack.getStack());
		try (Transaction tr = Transaction.openOuter()) {
			long c = h.insert(iv, forStack.getQuantity(), tr);
			if (c > 0)
				return new InventorySlot(getSlotHandler(h), null, this, seen);
		}
		return null;
	}

	@Override
	public InventorySlot findSlotDestAfter(InventorySlot slotIn, StoredItemStack forStack, boolean loop) {
		if (slotIn == null)return findSlotDest(forStack);
		if (slotIn.getView() == null) {
			if (loop)return findSlotDest(forStack);
			else return null;
		}
		var sl = findSlotDest(forStack, slotIn.nextSlot());
		if (sl == null && loop) {
			return findSlotDest(forStack);
		}
		return sl;
	}

	@Override
	public void onSlotChanged(InventorySlot slot) {
		if (slot.getView() != null && updateChange(slot.getView())) {
			lastChange = System.nanoTime();
		}
	}

	@Override
	public InventoryImage prepForOffThread(Level level) {
		if (lastUpdate == level.getGameTime())return null;
		Storage<ItemVariant> h = storage;
		if (h == null)return null;
		long ver = h.getVersion();
		if (ver == lastVersion)return null;
		InventoryImage im = new InventoryImage(ver);
		for (StorageView<ItemVariant> is : h)im.addStack(is);
		return im;
	}

	@Override
	public InvState processOffThread(InventoryImage array) {
		boolean change = false;
		var st = array.getStacks();
		Map<StorageView<ItemVariant>, StoredItemStack> mod = new HashMap<>();
		for (int i = 0; i < st.size(); i++) {
			FabricStack s = st.get(i);
			change |= updateChangeM(s, mod);
		}
		if (change) return new InvState(System.nanoTime(), array.getVersion(), mod);
		return new InvState(lastChange, lastVersion, Collections.emptyMap());
	}

	private boolean updateChangeM(FabricStack st, Map<StorageView<ItemVariant>, StoredItemStack> mod) {
		StorageView<ItemVariant> uv = st.uv();
		StoredItemStack li = lastItems.get(uv);
		if (!st.item().isBlank()) {
			ItemStack is = st.item().toStack();
			if (checkFilter(new StoredItemStack(is, st.count(), st.item().hashCode()))) {
				int cnt = getCount(st);
				if (li == null || !ItemStack.isSameItemSameComponents(li.getStack(), is)) {
					mod.put(uv, new StoredItemStack(is, cnt, st.item().hashCode()));
					return true;
				} else if(li.getQuantity() != cnt) {
					li.setCount(cnt);
					return true;
				}
				return false;
			}
		}
		if (li != null) {
			mod.put(uv, null);
			return true;
		}
		return false;
	}

	@Override
	public long finishOffThreadProcess(Level level, InvState ct) {
		if (ct == null)return lastChange;
		lastChange = ct.change();
		lastVersion = ct.version();
		lastUpdate = level.getGameTime();
		lastItems.putAll(ct.mod());
		return lastChange;
	}

	public void refresh(Storage<ItemVariant> h) {
		storage = h;
	}
}
