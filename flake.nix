{
  description = "Chisel development environment";

  inputs.nixpkgs.url = "github:nixos/nixpkgs/nixpkgs-unstable";
  inputs.pre-commit-hooks.url = "github:cachix/pre-commit-hooks.nix";
  inputs.pre-commit-hooks.inputs.nixpkgs.follows = "nixpkgs";
  inputs.flake-utils.url = "github:numtide/flake-utils";

  outputs =
    {
      self,
      nixpkgs,
      pre-commit-hooks,
      flake-utils,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfree = true;
        };
      in
      {
        checks = {
          pre-commit-check = pre-commit-hooks.lib.${system}.run {
            src = ./.;
            hooks = {
              nixfmt = {
                enable = true;
                package = pkgs.nixfmt-rfc-style;
              };
              ruff.enable = true;
              checkmake.enable = true;
              clang-format = {
                enable = true;
                types_or = pkgs.lib.mkForce [
                  "c"
                  "c++"
                ];
              };
              scalafmt = {
                enable = true;
                name = "scalafmt";
                entry = "${pkgs.scalafmt}/bin/scalafmt --respect-project-filters";
                types = [
                  "scala"
                  "sbt"
                ];
              };
              verible = {
                enable = true;
                name = "verible-verilog-format";
                entry = "${pkgs.verible}/bin/verible-verilog-format --wrap_spaces 3 --indentation_spaces 3 --inplace";
                types = [ "verilog" ];
              };
              scalafix = {
                enable = true;
                name = "scalafix";
                entry = "${pkgs.bash}/bin/bash -c '${pkgs.sbt}/bin/sbt --batch -Dsbt.server.forcestart=true scalafix'";
                types = [ "scala" ];
              };
            };
            settings = { };
          };
        };
        devShells.default = pkgs.mkShell {
          CHISEL_FIRTOOL_PATH = "${pkgs.circt}/bin";
          RISCV = "${(pkgs.callPackage ./nix/riscv-gcc.nix { })}";
          RISCV_PREFIX = "${(pkgs.callPackage ./nix/riscv-gcc.nix { })}/bin/riscv32-unknown-elf-";
          CSMITH_INCLUDE = "${pkgs.csmith}/include/csmith-2.3.0/";
          PYTHONPATH = "./src/test/python";

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
            pkgs.verilog
            pkgs.espresso

            pkgs.python311
            pkgs.python311Packages.mypy
            pkgs.python311Packages.pytest
            pkgs.python311Packages.riscof
            (pkgs.python311Packages.cocotb.overrideAttrs (oldAttrs: {
              patches = oldAttrs.patches or [ ] ++ [ ./nix/cocotb_pre_cmd.patch ];
            }))

            (pkgs.spike.overrideAttrs (oldAttrs: {
              configureFlags = oldAttrs.configureFlags or [ ] ++ [
                "--enable-commitlog"
                "--enable-misaligned"
              ];
            }))
            pkgs.dtc

            pkgs.csmith

            (pkgs.callPackage ./nix/bin2hex.nix { })
            (pkgs.callPackage ./nix/dump2vsim.nix { })
            (pkgs.callPackage ./nix/dump2gtkw.nix { })
            (pkgs.callPackage ./nix/asmgen.nix { })
            (pkgs.callPackage ./nix/grouphex.nix { })
            (pkgs.callPackage ./nix/spiketrace2json.nix { })

            (pkgs.callPackage ./nix/gtkwave.nix { })
            (pkgs.callPackage ./nix/surfer.nix { })
            (pkgs.callPackage ./nix/riscv-gcc.nix { })
            (pkgs.callPackage ./nix/questa.nix { })
          ];
        };
      }
    );
}
