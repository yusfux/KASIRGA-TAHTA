{
  stdenv,
  lib,
  unstick,
  fetchurl,
}:
let
  version = "23.1std.0.991";

  download =
    { name, sha256 }:
    fetchurl {
      inherit name sha256;
      url = "https://downloads.intel.com/akdlm/software/acdsinst/${lib.versions.majorMinor version}std/${
        lib.elemAt (lib.splitVersion version) 4
      }/ib_installers/${name}";
    };
in
stdenv.mkDerivation rec {
  inherit version;
  pname = "questa";

  src = map download [
    {
      name = "QuestaSetup-${version}-linux.run";
      sha256 = "0f9lyphk4vf4ijif3kb4iqf18jl357z9h8g16kwnzaqwfngh2ixk";
    }
  ];

  nativeBuildInputs = [ unstick ];

  buildCommand =
    let
      installer = builtins.head src;
      copyInstaller = ''
        cp ${installer} $TEMP/${installer.name}
        chmod u+w,+x $TEMP/${installer.name}
        patchelf --interpreter $(cat $NIX_CC/nix-support/dynamic-linker) $TEMP/${installer.name}
      '';
    in
    ''
      ${copyInstaller}

      unstick $TEMP/${installer.name} \
        --mode unattended --installdir $out --accept_eula 1

      rm -r $out/uninstall $out/logs
    '';

  meta = with lib; {
    homepage = "https://fpgasoftware.intel.com";
    description = "FPGA design and simulation software";
    sourceProvenance = with sourceTypes; [ binaryNativeCode ];
    license = licenses.unfree;
    platforms = [ "x86_64-linux" ];
    maintainers = with maintainers; [ kwohlfahrt ];
  };
}
