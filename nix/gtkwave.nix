{
  autoconf,
  autogen,
  automake,
  bzip2,
  ccacheStdenv,
  fetchurl,
  glib,
  gperf,
  gtk3,
  shared-mime-info,
  gtk-mac-integration,
  judy,
  lib,
  meson,
  desktop-file-utils,
  flex,
  ninja,
  pkg-config,
  stdenv,
  tcl,
  tk,
  wrapGAppsHook,
  xz,
  python3,
  gobject-introspection,
  fetchFromGitHub,
}:
stdenv.mkDerivation rec {
  pname = "gtkwave";
  version = "HEAD";

  src = fetchFromGitHub {
    owner = "gtkwave";
    repo = "gtkwave";
    rev = "254cf5c835ad4d638d51e4d76643a2329fb12ebc";
    sha256 = "sha256-jNgtK/Av9RC9lYZ0QNj4j9Bbm0XNMm12sEoAuD09i64=";
  };

  nativeBuildInputs = [pkg-config meson ninja wrapGAppsHook];
  buildInputs =
    [
      bzip2
      glib
      gperf
      gtk3
      desktop-file-utils
      judy
      shared-mime-info
      tcl
      tk
      xz
      flex
      gobject-introspection
    ]
    ++ lib.optional stdenv.isDarwin gtk-mac-integration;

  enableParallelBuilding = true;
  configurePhase = ''
    meson setup build --prefix=$out --datadir=$out/share
  '';

  buildPhase = ''
    meson compile -C build
  '';

  installPhase = ''
    meson install -C build
  '';

  meta = {
    description = "VCD/Waveform viewer for Unix and Win32";
    homepage = "http://gtkwave.sourceforge.net";
    license = lib.licenses.gpl2Plus;
    maintainers = with lib.maintainers; [thoughtpolice jiegec];
    platforms = lib.platforms.linux ++ lib.platforms.darwin;
  };
}
