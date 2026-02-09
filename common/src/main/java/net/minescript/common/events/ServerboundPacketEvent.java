// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common.events;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minescript.common.Jsonable;

public class ServerboundPacketEvent extends Jsonable {
  public String type = "serverbound_packet";
  public Connection connection;
  public Packet<?> packet;
  public ChannelFutureListener listener;
  public boolean flush;
  public double time;

  public boolean cancelled() {
    return cancelled;
  }

  public void cancel() {
    cancelled = true;
  }

  private boolean cancelled = false;
}
