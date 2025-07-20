## How to work with Minecraft mappings

### Contents

- [What are obfuscated symbols and mappings?](#what-are-obfuscated-symbols-and-mappings)
- [How does Minescript 5.0 handle mappings in Pyjinn scripts?](#how-does-minescript-50-handle-mappings-in-pyjinn-scripts)
- [Download and install the Official Mojang Mappings](#download-and-install-the-official-mojang-mappings)
- [Download and install the Fabric Intermediary Mappings](#download-and-install-the-fabric-intermediary-mappings)
- [Making scripts self-contained with their own mappings](#making-scripts-self-contained-with-their-own-mappings)
- [Getting help](#getting-help)

### What are obfuscated symbols and mappings?

Minecraft code is obfuscated, meaning that in the original Java source code, references to human-readable class, method, and field names are replaced with gibberish names in the released version of Minecraft. E.g. the class `net.minecraft.client.Minecraft` is renamed to `fud` and the method `Minecraft.getInstance()` is renamed to `fud.R()`. These mappings change from one Minecraft release to the next.

Since Minecraft 1.20.2, NeoForge remaps the game's Java symbols (class, method, and field names) to use the Official Mojang Mappings (see [NeoForge discussion](https://github.com/neoforged/NeoForge/discussions/199)). So mods and scripts that need to look up game symbols at runtime with recent versions of NeoForge don't require any deobfuscation or remapping.

As of game version 1.21.7, Fabric continues to use intermediary mappings  which are different from the game's obfuscated symbols. For example, `net.minecraft.class_310` is Fabric's intermediary name that corresponds to the obfuscated class name `fud`. The main purpose of intermediary mappings is to provide consistency of class, method, and field names across game versions, unlike obfuscated names which generally change arbitrarily across game versions. So when referring to a game symbol at runtime for the Fabric version of the mod, the mapping to its intermediary name needs to be available.


### How does Minescript 5.0 handle mappings in Pyjinn scripts?

When writing Pyjinn scripts, refer to Java classes (via `JavaClass(...)`), methods, and fields using the Official Mojang names. These names work directly with NeoForge. But for Fabric, mappings are needed to map the official names used in your script to the Fabric intermediary names used by the game. There are 2 options for doing this:

1. Download the Official Mojang Mappings that map from official names (like `net.minecraft.client.Minecraft`) to obfuscated names (like `fud`), and download the Fabric intermediary mappings that map from obfuscated names (again, like `fud`) to intermediary names (like `net.minecraft.class_310`) that the game uses at runtime. See instructions below for how to find and install these for Minescript.

2. Specify the mappings as metadata comments in your script, which provides the mapping from official Mojang names to Fabric intermediary names. This option allows script developers to package only the mappings needed by their script into the script itself so that users of their script don't need to download or install the entire mappings themselves. See below for an example of adding mappings to your script.

When developing a script, you'll generally want to use option 1 and let Minescript figure out the mappings for you based on the Official and Fabric mappings, then copy those generated mappings into your script.


#### Download and install the Official Mojang Mappings

Script developers can download the Official Mojang Mappings by first visiting [https://piston-meta.mojang.com/mc/game/version_manifest_v2.json](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) and looking for the version of Minecraft you're using. For example for 1.21.7, the corresponding entry looks like:

```
"versions": [{"id": "1.21.7", "type": "release", "url": "https://piston-meta.mojang.com/v1/packages/5d22e5893fd9c565b9a3039f1fc842aef2c4aefc/1.21.7.json", ...}
```

Visit the URL in that entry (e.g. [https://piston-meta.mojang.com/v1/packages/5d22e5893fd9c565b9a3039f1fc842aef2c4aefc/1.21.7.json](https://piston-meta.mojang.com/v1/packages/5d22e5893fd9c565b9a3039f1fc842aef2c4aefc/1.21.7.json)) and look for `"client_mappings"` which should have a `"url"` value like `"https://piston-data.mojang.com/v1/objects/8d83af626cae1865deaf55fbf96934be4886fd45/client.txt"`. Download that file as `client.txt` and move it into your `minecraft/minescript/mappings/<mc_version>/` folder, e.g. `minecraft/minescript/mappings/1.21.7/`.


#### Download and install the Fabric Intermediary Mappings

Script developers can download the Fabric intermediary mappings for a specific version of the game at [https://github.com/FabricMC/intermediary/tree/master/mappings](https://github.com/FabricMC/intermediary/tree/master/mappings). For example for Minecraft 1.21.7, the Fabric mappings are at [https://github.com/FabricMC/intermediary/blob/master/mappings/1.21.7.tiny](https://github.com/FabricMC/intermediary/blob/master/mappings/1.21.7.tiny). On the GitHub page, click the 3-dot menu on the right and select "Download". Move the file (e.g. `1.21.7.tiny`) to your `minecraft/minescript/mappings/<mc_version>/` folder, e.g. `minecraft/minescript/mappings/1.21.7/`.


#### Making scripts self-contained with their own mappings

To generate mappings for your script, add this comment to the top of your script:

```
 @dump_active_mappings
```

And with the official and Fabric mappings installed as described in the previous sections, run your script from within the game. All the mappings resolved by Minescript will be dumped to the Minecraft log file (`minecraft/logs/latest.log`) with messages like:


```
[12:34:56] [Render thread/INFO]: Dumping script mapping for your_script.pyj:
 <mappings Fabric 1.21.7>  # timestamp=1234567890123
[12:34:56] [Render thread/INFO]: Dumping script mapping for your_script.pyj:
   class net.minecraft.client.Minecraft net.minecraft.class_310  # timestamp=1234567890123
```

The timestamp portion of the comments refer to the time that the script was launched, to help identify the generated mappings for a specific run of your script in case you need to run it multiple times.

Copy the log lines starting with `#` to the top of your Pyjinn script. You can drop the `# timestamp=...` portion of the line when copying to your script. Then, add this extra comment line immediately after the other lines that you copied from the log file:

```
 </mappings>
```

For example:

```
 <mappings Fabric 1.21.7>
   class net.minecraft.client.Minecraft net.minecraft.class_310
   method net.minecraft.class_310 getInstance method_1551
 </mappings>
```

**NOTE:** When you run your script to generate mappings with `@dump_active_mappings`, you need to exercise all of your script's code, because script code that isn't run will not get its symbols resolved. You might need to run your script multiple times with different parameters to exercise all of its code.

You can provide multiple sets of mappings in the same script, one for each version of Minecraft that you want to support for Fabric users.


### Getting help

If you need help working with mappings, join the [Minescript Discord](https://discord.gg/NjcyvrHTze) and feel free to seek help there.
