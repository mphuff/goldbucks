package com.goldbucks;

import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.goldbucks.economy.ItemKind;

/** Sorts items into the kinds that job tasks care about. */
public final class ItemKinds {
	private ItemKinds() {
	}

	public static ItemKind of(ItemStack stack) {
		ItemKind farm = farm(stack);

		if (farm != null) {
			return farm;
		}

		if (stack.has(DataComponents.FOOD)) {
			return ItemKind.FOOD;
		}

		if (stack.is(ItemTags.BOATS) || stack.is(ItemTags.CHEST_BOATS) || stack.is(ItemTags.RAILS)
				|| stack.is(ItemTags.HARNESSES) || stack.is(Items.MINECART) || stack.is(Items.CHEST_MINECART)
				|| stack.is(Items.SADDLE)) {
			return ItemKind.VEHICLE;
		}

		if (stack.is(ItemTags.BOOKSHELF_BOOKS)) {
			return ItemKind.BOOK;
		}

		return ItemKind.OTHER;
	}

	/** Raw crops and animal products count as farm produce, for the Farmer (even the ones you can eat). */
	private static ItemKind farm(ItemStack stack) {
		return stack.is(Items.WHEAT) || stack.is(Items.CARROT) || stack.is(Items.POTATO) || stack.is(Items.BEETROOT)
				|| stack.is(Items.MELON_SLICE) || stack.is(Items.MELON) || stack.is(Items.PUMPKIN) || stack.is(Items.SUGAR_CANE)
				|| stack.is(Items.EGG) || stack.is(Items.MILK_BUCKET) || stack.is(ItemTags.WOOL) || stack.is(Items.WHEAT_SEEDS)
				|| stack.is(Items.APPLE) || stack.is(Items.SWEET_BERRIES) ? ItemKind.FARM : null;
	}
}
