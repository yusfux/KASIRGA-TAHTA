{
  lib,
  buildFHSEnv,
  callPackage,
  makeDesktopItem,
  writeScript,
  runtimeShell,
  runCommand,
  unwrapped ? callPackage ./questa_files.nix { },
}:
let
in
# I think questa_fse/linux/vlm checksums itself, so use FHSUserEnv instead of `patchelf`
buildFHSEnv rec {
  name = "quartus-prime-lite-questa"; # wrapped

  targetPkgs =
    pkgs: with pkgs; [
      (runCommand "ld-lsb-compat" { } ''
        mkdir -p "$out/lib"
        ln -sr "${glibc}/lib/ld-linux-x86-64.so.2" "$out/lib/ld-lsb-x86-64.so.3"
        ln -sr "${pkgsi686Linux.glibc}/lib/ld-linux.so.2" "$out/lib/ld-lsb.so.3"
      '')
      # quartus requirements
      glib
      xorg.libICE
      xorg.libSM
      xorg.libXau
      xorg.libXdmcp
      libudev0-shim
      bzip2
      brotli
      expat
      dbus
      # qsys requirements
      xorg.libXtst
      xorg.libXi
    ];

  # Also support 32-bit executables.
  multiArch = true;

  multiPkgs =
    pkgs:
    with pkgs;
    let
      # This seems ugly - can we override `libpng = libpng12` for all `pkgs`?
      freetype = pkgs.freetype.override { libpng = libpng12; };
      fontconfig = pkgs.fontconfig.override { inherit freetype; };
      libXft = pkgs.xorg.libXft.override { inherit freetype fontconfig; };
    in
    [
      # questa requirements
      libxml2
      ncurses5
      unixODBC
      libXft
      # common requirements
      freetype
      fontconfig
      xorg.libX11
      xorg.libXext
      xorg.libXrender
      libxcrypt-legacy
    ];

  extraInstallCommands = ''
    mkdir -p $out/share/applications $out/share/icons/hicolor/64x64/apps

    progs_to_wrap=(
      "${unwrapped}"/questa_fse/bin/*
      "${unwrapped}"/questa_fse/linux_x86_64/lmutil
    )

    wrapper=$out/bin/${name}
    progs_wrapped=()
    for prog in ''${progs_to_wrap[@]}; do
        relname="''${prog#"${unwrapped}/"}"
        wrapped="$out/$relname"
        progs_wrapped+=("$wrapped")
        mkdir -p "$(dirname "$wrapped")"
        echo "#!${runtimeShell}" >> "$wrapped"
        case "$relname" in
            questa_fse/*)
                echo "export NIXPKGS_IS_QUESTA_WRAPPER=1" >> "$wrapped"
                ;;
        esac
        echo "$wrapper $prog \"\$@\"" >> "$wrapped"
    done

    cd $out
    chmod +x ''${progs_wrapped[@]}
    # link into $out/bin so executables become available on $PATH
    ln --symbolic --relative --target-directory ./bin ''${progs_wrapped[@]}
  '';

  # Run the wrappers directly, instead of going via bash.
  runScript = "";
}
