// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.common;

public class Math3d {
  public static double computeDistanceSquared(
      double x1, double y1, double z1, double x2, double y2, double z2) {
    double dx = x1 - x2;
    double dy = y1 - y2;
    double dz = z1 - z2;
    return dx * dx + dy * dy + dz * dz;
  }

  public static double computeDistance(
      double x1, double y1, double z1, double x2, double y2, double z2) {
    return Math.sqrt(computeDistanceSquared(x1, y1, z1, x2, y2, z2));
  }
}
