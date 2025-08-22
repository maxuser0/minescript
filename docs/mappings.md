## How to work with Minecraft mappings

### Contents

- [What are obfuscated symbols and mappings?](#what-are-obfuscated-symbols-and-mappings)
- [How does Minescript 5.0 handle mappings in Pyjinn scripts?](#how-does-minescript-50-handle-mappings-in-pyjinn-scripts)
- [Install the mappings](#install-the-mappings)
- [Getting help](#getting-help)

### What are obfuscated symbols and mappings?

Minecraft code is obfuscated, meaning that in the original Java source code, references to
human-readable class, method, and field names are replaced with gibberish names in the released
version of Minecraft. E.g. the class `net.minecraft.client.Minecraft` is renamed to `fud` and the
method `Minecraft.getInstance()` is renamed to `fud.R()`. These mappings change from one Minecraft
release to the next.

Since Minecraft 1.20.2, NeoForge remaps the game's Java symbols (class, method, and field names) to
use the Official Mojang Mappings (see [NeoForge
discussion](https://github.com/neoforged/NeoForge/discussions/199)). So mods and scripts that need
to look up game symbols at runtime with recent versions of NeoForge don't require any deobfuscation
or remapping.

As of game version 1.21.8, Fabric continues to use intermediary mappings  which are different from
the game's obfuscated symbols. For example, `net.minecraft.class_310` is Fabric's intermediary name
that corresponds to the obfuscated class name `fud`. The main purpose of intermediary mappings is to
provide consistency of class, method, and field names across game versions, unlike obfuscated names
which generally change arbitrarily across game versions. So when referring to a game symbol at
runtime for the Fabric version of the mod, the mapping to its intermediary name needs to be
available.


### How does Minescript 5.0 handle mappings in Pyjinn scripts?

When writing Pyjinn scripts, refer to Java classes (via `JavaClass(...)`), methods, and fields using
the Official Mojang names. These names work directly with NeoForge. But for Fabric, mappings are
needed to map the official names used in your script to the Fabric intermediary names used by the
game.

The Official Mojang Mappings map from official names (like `net.minecraft.client.Minecraft`) to
obfuscated names (like `fud`), and the Fabric intermediary mappings map from obfuscated names
(again, like `fud`) to intermediary names (like `net.minecraft.class_310`) that the game uses at
runtime when using the Fabric mod loader. See instructions below for how to install the mappings
files for Minescript.


### Install the mappings

In Minescript 5.0 beta 4 (5.0b4) and higher, you can run the pre-installed command
`\install_mappings` to download, install, and load the mappings for your current version Minecraft
and your mod loader. The Fabric version of Minescript needs the mappings, while the NeoForge version
does not.

If the mappings are already installed for your version of Minecraft and Fabric, `\install_mappings`
will not download them again, but it will reload the ones previously downloaded.

The code for `\install_mappings` is located at
`<minecraft>/minescript/system/exec/install_mappings.pyj`.  Here's what the script does when you run
it:

#### 1. Downloads and installs the Official Mojang Mappings.

`\install_mappings` first visits
[https://piston-meta.mojang.com/mc/game/version_manifest_v2.json](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
and looks for the version of Minecraft that you're currently using. For example for 1.21.7, the
corresponding entry looks like:

```
"versions": [{"id": "1.21.7", "type": "release", "url": "https://piston-meta.mojang.com/v1/packages/5d22e5893fd9c565b9a3039f1fc842aef2c4aefc/1.21.7.json", ...}
```

It then visits the URL in that entry (e.g.
[https://piston-meta.mojang.com/v1/packages/5d22e5893fd9c565b9a3039f1fc842aef2c4aefc/1.21.7.json](https://piston-meta.mojang.com/v1/packages/5d22e5893fd9c565b9a3039f1fc842aef2c4aefc/1.21.7.json))
and looks for `"client_mappings"` which should have a `"url"` value like
`"https://piston-data.mojang.com/v1/objects/8d83af626cae1865deaf55fbf96934be4886fd45/client.txt"`.
It downloads that file and saves it as `client.txt` in your
`<minecraft>/minescript/system/mappings/<mc_version>/` folder, e.g.
`minecraft/minescript/system/mappings/1.21.7/client.txt`.


#### 2. Downloads and installs the Fabric Intermediary Mappings.

`\install mappings` then downloads the Fabric intermediary mappings for your current version of the
Minecraft at
[https://github.com/FabricMC/intermediary/tree/master/mappings](https://github.com/FabricMC/intermediary/tree/master/mappings).
For example for Minecraft 1.21.7, the Fabric mappings are at
[https://github.com/FabricMC/intermediary/blob/master/mappings/1.21.7.tiny](https://github.com/FabricMC/intermediary/blob/master/mappings/1.21.7.tiny).
You can find this on the GitHub page by clicking the 3-dot menu on the right and selecting
"Download". The command saves the file (e.g.  `1.21.7.tiny`) to your
`<minecraft>/minescript/system/mappings/<mc_version>/` folder, e.g.
`minecraft/minescript/system/mappings/1.21.7/1.21.7.tiny`.

#### 3. Loads the mappings into the Minescript mod.

After downloading the Official and Fabric mappings (if they weren't already installed),
`\install_mappings` reloads the mappings that are saved on your computer. Minescript loads the
mappings into optimized in-memory data structures so that scripts written using the Mojang official
names of classes, methods, or fields get those names mapped automatically to their runtime names as
fast as possible.

This step is equivalent to running the built-in command `\reload_mappings`.


### Getting help

If you need help working with mappings, join the [Minescript Discord](https://discord.gg/NjcyvrHTze) and feel free to seek help there.
