#!/bin/bash
# SPDX-FileCopyrightText: © 2026 Minescript Contributors
# SPDX-License-Identifier: MIT

if [[ -z $MINESCRIPT_MINECRAFT_DIR ]]; then
  echo "Error: \$MINESCRIPT_MINECRAFT_DIR is not defined. E.g. define it as:" >&2
  echo "export MINESCRIPT_MINECRAFT_DIR=\"$HOME/.minecraft\"" >&2
  exit 1
fi

if [[ -z $MINESCRIPT_MOD_INSTALLERS_DIR ]]; then
  echo "Error: \$MINESCRIPT_MOD_INSTALLERS_DIR is not defined. E.g. define it as:" >&2
  echo "export MINESCRIPT_MOD_INSTALLERS_DIR=\"$HOME/mod-installers\"" >&2
  exit 1
fi

minecraft_dir="$MINESCRIPT_MINECRAFT_DIR"

run=
if [[ $1 = "-n" ]]; then
  shift
  run="echo [dry run]"
fi

function init_mod_dirs {
  if [[ ! -d $minecraft_dir/mods-fabric
      && ! -d $minecraft_dir/mods-forge
      && ! -d $minecraft_dir/mods-neoforge ]]; then
    # Treat neoforge as the default.
    if [[ ! -d $minecraft_dir/mods ]]; then
      $run mkdir $minecraft_dir/mods
    fi
    $run mkdir $minecraft_dir/mods-fabric
    $run mkdir $minecraft_dir/mods-forge
  fi
}

function get_active_loader {
  if [[ ! -d $minecraft_dir/mods ]]; then
    echo ""
  elif [[ ! -d $minecraft_dir/mods-fabric ]]; then
    echo "fabric"
  elif [[ ! -d $minecraft_dir/mods-forge ]]; then
    echo "forge"
  elif [[ ! -d $minecraft_dir/mods-neoforge ]]; then
    echo "neoforge"
  else
    echo ""
  fi
}

init_mod_dirs
active_loader=$(get_active_loader)
new_loader=$1
new_mc_version=$2
minescript_release_dir=${3:-$new_loader/build/libs}
mod_installs_dir=${4:-$MINESCRIPT_MOD_INSTALLERS_DIR}

function print_info_and_usage {
  echo "Minecraft mods dirs:"
  ls -d $minecraft_dir/mods*
  echo
  echo "Active loader: $active_loader"
  echo
  echo "$minecraft_dir/mods:"
  ls $minecraft_dir/mods
  echo
  echo "Usage:"
  echo "minecraft-mod-loader.sh fabric|forge|neoforge [MC_VERSION [MOD_JAR_DIR [MOD_INSTALLERS_DIR]]]"
}

function handle_no_active_loader {
  echo "No active loader found:"
  ls -d $minecraft_dir/mods*
  echo
}

function swap_mods_dirs {
  if [[ $new_loader = $active_loader ]]; then
    echo "$new_loader mods already active."
  else
    if [[ -d $minecraft_dir/mods && -n $active_loader ]]; then
      $run mv $minecraft_dir/mods $minecraft_dir/mods-$active_loader &&
          echo "Moved mods to backup (mods-${active_loader})." 2>&1
    fi
    if [[ -d $minecraft_dir/mods-$new_loader ]]; then
      $run mv $minecraft_dir/mods-$new_loader $minecraft_dir/mods &&
          echo "Moved mods-$new_loader to primary (mods)." 2>&1
    else
      $run mkdir $minecraft_dir/mods
      echo "Created dir for $new_loader mods: $minecraft_dir/mods" 2>&1
    fi
  fi
}

function update_fabric_api {
  active_fabric_api=$(ls $minecraft_dir/mods/fabric-api-*.jar 2>/dev/null)
  need_new_fabric_api=1
  mc_version_for_fabric_api="${new_mc_version%%-snapshot-*}"
  new_fabric_api=$(ls $mod_installs_dir/fabric-api-*+${mc_version_for_fabric_api}.jar)
  if [[ -n $active_fabric_api ]]; then
    if [[ $active_fabric_api =~ $new_mc_mversion ]]; then
      need_new_fabric_api=0  # The fabric-api version requested is already in the mods dir.
    else
      if [[ -z $new_fabric_api ]]; then
        echo "Requested fabric-api for $new_mc_version not found in dir: $mod_installs_dir" 2>&1
        exit 2
      fi
      $run rm $active_fabric_api &&
          echo "Deleted previous fabric-api:                $(basename $active_fabric_api)" 2>&1
    fi
  fi
  if [[ $need_new_fabric_api == 1 ]]; then
    fabric_api_basename=$(basename ${new_fabric_api})
    $run cp -p $new_fabric_api $minecraft_dir/mods/ &&
        echo "Installed new fabric-api in mods dir:       $fabric_api_basename" 2>&1
  fi
}

function update_mods_for_mc_version {
  old_minescript_mod=$(ls $minecraft_dir/mods/minescript-*.jar)
  if [[ -n $old_minescript_mod ]]; then
    old_mod_basename=$(basename $old_minescript_mod)
    $run rm $old_minescript_mod &&
        echo "Deleted installed minescript mod:           $old_mod_basename" 2>&1
  fi
  if [[ $new_loader = "fabric" ]]; then
    update_fabric_api
  elif [[ $new_loader = "neoforge" ]]; then
    install_neoforge.sh
  fi
  new_minescript_mod_release_path="$(ls -t ${minescript_release_dir}/minescript-${new_loader}-${new_mc_version}-*.jar |grep -v -- '-\(sources\|javadoc\)\.jar$' | head -1)"
  if [[ -f $new_minescript_mod_release_path ]]; then
    new_minescript_mod=$(basename $new_minescript_mod_release_path)
    $run cp -p $new_minescript_mod_release_path $minecraft_dir/mods/ &&
        echo "Installed minescript mod from release dir:  $new_minescript_mod" 2>&1
  else
    echo "Error: could not find minescript mod for mc version: $new_mc_version" 2>&1
  fi
}

function update_mods {
  case $new_loader in
    fabric|forge|neoforge)
      swap_mods_dirs
      ;;
    *)
      echo "Expected fabric, forge, or neoforge but got: \"$new_loader\"" >&2
      exit 1
  esac

  if [[ -n $new_mc_version ]]; then
    update_mods_for_mc_version
  fi
}

function main {
  if [[ -z $new_loader ]]; then
    print_info_and_usage
    return
  fi

  if [[ -z $active_loader ]]; then
    handle_no_active_loader
  fi

  echo "Minecraft mods dirs:"
  ls -d $minecraft_dir/mods*
  echo
  update_mods
}

main
