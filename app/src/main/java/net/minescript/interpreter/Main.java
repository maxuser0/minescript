// SPDX-FileCopyrightText: © 2022-2024 Greg Christiana <maxuser@minescript.net>
// SPDX-License-Identifier: GPL-3.0-only

package net.minescript.interpreter;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class Main {
  public static void main(String[] args) {
    JsonElement jsonAst =
        JsonParser.parseString(
            new BufferedReader(new InputStreamReader(System.in))
                .lines()
                .collect(Collectors.joining("\n")));

    var interpreter = new Interpreter();
    interpreter.parse(jsonAst);
    var func = args.length == 0 ? interpreter.getFunction() : interpreter.getFunction(args[0]);
    System.out.println(func);

    var output = interpreter.invoke(func);
    System.out.println(output);
  }
}
