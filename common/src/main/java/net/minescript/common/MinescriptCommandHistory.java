// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MinescriptCommandHistory {
  private final List<String> commandList = new ArrayList<>();
  private int commandPosition;

  public MinescriptCommandHistory() {
    commandList.add("");
    commandPosition = 0;
  }

  public Optional<String> moveBackwardAndGet(String currentCommand) {
    // Temporarily add currentCommand as the final command if current position is at the final
    // command.
    if (commandPosition == 0) {
      return Optional.empty();
    }
    if (commandPosition == lastCommandPosition()) {
      commandList.set(commandPosition, currentCommand);
    }
    commandPosition--;
    return Optional.of(commandList.get(commandPosition));
  }

  public Optional<String> moveForwardAndGet() {
    if (commandPosition == lastCommandPosition()) {
      return Optional.empty();
    }
    commandPosition++;
    return Optional.of(commandList.get(commandPosition));
  }

  public void addCommand(String command) {
    // Command list of size 1 contains only the empty placeholder command. Ignore duplicate
    // consecutive user commands.
    if (commandList.size() == 1 || !command.equals(commandList.get(lastCommandPosition() - 1))) {
      commandList.add(lastCommandPosition(), command);
      moveToEnd();
    }
  }

  public void moveToEnd() {
    commandPosition = lastCommandPosition();
    commandList.set(commandPosition, "");
  }

  private int lastCommandPosition() {
    return commandList.size() - 1;
  }
}
