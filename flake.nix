{
  description = "Chisel development environment";

  inputs.nixpkgs.url = "github:nixos/nixpkgs/nixpkgs-unstable";
  inputs.pre-commit-hooks.url = "github:cachix/pre-commit-hooks.nix";
  inputs.pre-commit-hooks.inputs.nixpkgs.follows = "nixpkgs";
  inputs.flake-utils.url = "github:numtide/flake-utils";

  outputs = {
    self,
    nixpkgs,
    pre-commit-hooks,
    flake-utils,
  }:
    flake-utils.lib.eachDefaultSystem (
      system: let
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfree = true;
        };
      in {
        checks = {
          pre-commit-check = pre-commit-hooks.lib.${system}.run {
            src = ./.;
            hooks = {
              alejandra.enable = true;
              ruff.enable = true;
              clang-format = {
                enable = true;
                types_or = pkgs.lib.mkForce ["c" "c++"];
              };
              scalafmt = {
                enable = true;
                name = "scalafmt";
                entry = "${pkgs.scalafmt}/bin/scalafmt --respect-project-filters";
                types = ["scala" "sbt"];
              };
              verible = {
                enable = true;
                name = "verible-verilog-vormat";
                entry = "${pkgs.verible}/bin/verible-verilog-format --wrap_spaces 3 --indentation_spaces 3 --inplace";
                types = ["verilog"];
              };
              scalafix = {
                enable = true;
                name = "scalafix";
                entry = "${pkgs.bash}/bin/bash -c '${pkgs.sbt}/bin/sbt --batch -Dsbt.server.forcestart=true scalafix'";
                types = ["scala"];
              };
            };
            settings = {
            };
          };
        };
        devShells.default = pkgs.mkShell {
          CHISEL_FIRTOOL_PATH = "${pkgs.circt}/bin";
          inherit (self.checks.${system}.pre-commit-check) shellHook;
          packages = [
            pkgs.mill
            pkgs.circt
            pkgs.jextract
            pkgs.lit
            pkgs.llvm
            pkgs.scala-cli
            pkgs.sbt
            pkgs.metals
            pkgs.bloop
            pkgs.verilator
            pkgs.espresso
            (pkgs.callPackage ./nix/gtkwave.nix {})
            (pkgs.callPackage ./nix/surfer.nix {})
          ];
        };
      }
    );
}
