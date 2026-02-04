// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.dataclasses;

import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minescript.common.Jsonable;

public class ItemStackData extends Jsonable {
  public String item;
  public int count;
  public String nbt = null;
  public Integer slot = null;
  public Boolean selected = null;

  public ItemStackData(String item, int count) {
    this.item = item;
    this.count = count;
  }

  public static ItemStackData of(ItemStack itemStack, OptionalInt slot, boolean markSelected) {
    if (itemStack.getCount() == 0) {
      return null;
    } else {
      var minecraft = Minecraft.getInstance();
      var nbt = itemStack.save(minecraft.level.registryAccess());

      var out = new ItemStackData(itemStack.getItem().toString(), itemStack.getCount());
      if (nbt != null) {
        out.nbt = nbt.toString();
      }
      if (slot.isPresent()) {
        out.slot = slot.getAsInt();
      }
      if (markSelected) {
        out.selected = true;
      }
      return out;
    }
  }
}
