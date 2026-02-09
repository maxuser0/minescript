// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.events;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minescript.common.Jsonable;

public class ClientboundPacketEvent extends Jsonable {
  public String type = "clientbound_packet";
  public ClientPacketListener listener;
  public Packet<?> packet;
  public double time;

  public boolean cancelled() {
    return cancelled;
  }

  public void cancel() {
    cancelled = true;
  }

  private boolean cancelled = false;
}
