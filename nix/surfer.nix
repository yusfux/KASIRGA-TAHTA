# Copyright lowRISC contributors.
#
# SPDX-License-Identifier: MIT
{
  lib,
  fetchFromGitLab,
  rustPlatform,
  pkg-config,
  openssl,
  wayland,
  libxkbcommon,
  libGL,
}:
rustPlatform.buildRustPackage rec {
  pname = "surfer";
  version = "0.2.0-dev";

  src = fetchFromGitLab {
    owner = "surfer-project";
    repo = pname;
    rev = "448b8dc7cf7778d4506cc01bdf5df7704a890340";
    hash = "sha256-v158F5iOSki0HQrNqAkZckGuLPuMen1pIc+QuTnU37Y=";
    fetchSubmodules = true;
  };

  nativeBuildInputs = [pkg-config];
  buildInputs = [openssl wayland libxkbcommon libGL];

  # These libraries are dlopen'ed at runtime, but they won't be able to find anything in
  # NixOS's path. So force them to be linked.
  # This could alternatively be a wrapper which adds LD_LIBRARY_PATH.
  RUSTFLAGS = map (a: "-C link-arg=${a}") [
    "-Wl,--push-state,--no-as-needed"
    "-lEGL"
    "-lwayland-client"
    "-lxkbcommon"
    "-Wl,--pop-state"
  ];

  cargoLock = {
    lockFile = "${src}/Cargo.lock";
    outputHashes = {
      "codespan-0.12.0" = "sha256-3F2006BR3hyhxcUTaQiOjzTEuRECKJKjIDyXonS/lrE=";
      "egui_skia-0.5.0" = "sha256-dpkcIMPW+v742Ov18vjycLDwnn1JMsvbX6qdnuKOBC4=";
      "tracing-tree-0.2.0" = "sha256-/JNeAKjAXmKPh0et8958yS7joORDbid9dhFB0VUAhZc=";
    };
  };

  doCheck = false;

  meta = {
    description = "An Extensible and Snappy Waveform Viewer";
    homepage = "http://surfer-project.org/";
    license = lib.licenses.eupl12;
    mainProgram = "surfer";
  };
}
