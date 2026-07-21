// SPDX-FileCopyrightText: © 2022-2026 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import com.mojang.serialization.JsonOps;
import java.util.Comparator;
import net.minecraft.Optionull;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minescript.common.dataclasses.TabListData;
import net.minescript.common.dataclasses.TabListObjectiveData;
import net.minescript.common.dataclasses.TabListPlayerData;
import net.minescript.common.dataclasses.TabListTextData;
import net.minescript.common.mixin.PlayerTabOverlayAccessor;

/** Exports the same network-backed entries and metadata rendered by the player-list overlay. */
public final class TabListExporter {
  private static final Comparator<PlayerInfo> PLAYER_COMPARATOR =
      Comparator.comparingInt((PlayerInfo player) -> -player.getTabListOrder())
          .thenComparingInt(player -> player.getGameMode() == GameType.SPECTATOR ? 1 : 0)
          .thenComparing(
              player -> Optionull.mapOrDefault(player.getTeam(), PlayerTeam::getName, ""))
          .thenComparing(player -> player.getProfile().name(), String::compareToIgnoreCase);

  private TabListExporter() {}

  public static TabListData export(Minecraft minecraft) {
    var overlay = minecraft.gui.hud.getTabList();
    var overlayAccessor = (PlayerTabOverlayAccessor) overlay;
    var scoreboard = minecraft.level.getScoreboard();
    var objective = scoreboard.getDisplayObjective(DisplaySlot.LIST);

    var players =
        minecraft.player.connection.getListedOnlinePlayers().stream()
            .sorted(PLAYER_COMPARATOR)
            .limit(80)
            .map(
                info -> {
                  var data = new TabListPlayerData();
                  var profile = info.getProfile();
                  data.uuid = profile.id().toString();
                  data.name = profile.name();
                  data.display_name = textData(minecraft, overlay.getNameForDisplay(info));
                  data.latency = info.getLatency();
                  data.game_mode = info.getGameMode().getName();
                  data.team = info.getTeam() == null ? null : info.getTeam().getName();
                  data.order = info.getTabListOrder();
                  data.skin_texture = info.getSkin().body().texturePath().toString();
                  data.show_hat = info.showHat();

                  if (objective != null) {
                    var scoreInfo =
                        scoreboard.getPlayerScoreInfo(ScoreHolder.fromGameProfile(profile), objective);
                    if (scoreInfo != null) {
                      data.score = scoreInfo.value();
                      if (objective.getRenderType() != ObjectiveCriteria.RenderType.HEARTS) {
                        data.score_display =
                            textData(
                                minecraft,
                                scoreInfo.formatValue(
                                    objective.numberFormatOrDefault(
                                        StyledFormat.PLAYER_LIST_DEFAULT)));
                      }
                    }
                  }
                  return data;
                })
            .toArray(TabListPlayerData[]::new);

    var objectiveData =
        objective == null
            ? null
            : new TabListObjectiveData(
                objective.getName(),
                textData(minecraft, objective.getDisplayName()),
                objective.getRenderType().getSerializedName());

    return new TabListData(
        players,
        textData(minecraft, overlayAccessor.minescript$getHeader()),
        textData(minecraft, overlayAccessor.minescript$getFooter()),
        objectiveData);
  }

  private static TabListTextData textData(Minecraft minecraft, Component component) {
    if (component == null) {
      return null;
    }
    var json =
        ComponentSerialization.CODEC
            .encodeStart(
                minecraft.level.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                component)
            .getOrThrow();
    return new TabListTextData(component.getString(), json);
  }
}
