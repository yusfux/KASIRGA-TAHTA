{
  lib,
  fetchFromGitLab,
  rustPlatform,
  pkg-config,
  openssl,
  wayland,
  autoPatchelfHook,
  libxkbcommon,
  libGL,
  libX11,
  libXcursor,
  libXi,
  stdenv,
}:
rustPlatform.buildRustPackage rec {
  pname = "surfer";
  version = "0.3.0";

  src = fetchFromGitLab {
    owner = "surfer-project";
    repo = "surfer";
    rev = "5eafbf287ade7d40703548f84c628cfed75f9357";
    hash = "sha256-hCrnOT2Bqxa/7uOnSQj0R9FAp6mEL9etD5WNeVMgnpY=";
    fetchSubmodules = true;
  };

  nativeBuildInputs = [
    pkg-config
    autoPatchelfHook
  ];

  buildInputs = [
    openssl
    stdenv.cc.cc.lib
  ];

  # Wayland and X11 libs are required at runtime since winit uses dlopen
  runtimeDependencies = [
    wayland
    libxkbcommon
    libGL
    libX11
    libXcursor
    libXi
  ];

  cargoLock = {
    lockFile = "${src}/Cargo.lock";
    outputHashes = {
      "codespan-0.12.0" = "sha256-3F2006BR3hyhxcUTaQiOjzTEuRECKJKjIDyXonS/lrE=";
      "egui_skia_renderer-0.1.0" = "sha256-K/IRanUbXjOa/8EsBKh7/CsqA60zLAo/g09bLdp3zR8=";
      "spade-0.9.0" = "sha256-ZKUnvh9mUsrDzcj7GUjrDWz6kUJYFRFJ9ziQ5AP69PY=";
    };
  };

  # Avoid the network attempt from skia. See: https://github.com/cargo2nix/cargo2nix/issues/318
  doCheck = false;

  meta = {
    description = "Extensible and Snappy Waveform Viewer";
    homepage = "https://surfer-project.org/";
    changelog = "https://gitlab.com/surfer-project/surfer/-/releases/v${version}";
    license = lib.licenses.eupl12;
    maintainers = with lib.maintainers; [ hakan-demirli ];
    platforms = lib.platforms.linux;
    mainProgram = "surfer";
  };
}
