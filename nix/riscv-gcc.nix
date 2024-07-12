{ pkgs }:
pkgs.stdenv.mkDerivation {
  pname = "riscv-rv32i-toolchain";
  version = "9.2.0";
  src = pkgs.fetchFromGitHub {
    owner = "riscv";
    repo = "riscv-gnu-toolchain";
    rev = "a03290eab661e2aa58288ad164f908bbbcc2169c";
    sha256 = "sha256-QTjDc7uqnJP3bUo1h2s++yQGKiuAJVY1j+6BVMWb9gU=";
    fetchSubmodules = true;
  };

  configureFlags = [
    "--with-arch=rv32i"
    "--with-abi=ilp32"
    "--enable-multilib"
  ];

  installPhase = ":"; # 'make' installs on its own
  hardeningDisable = [ "all" ];
  enableParallelBuilding = true;

  # Stripping/fixups break the resulting libgcc.a archives, somehow.
  # Maybe something in stdenv that does this...
  dontStrip = true;
  dontFixup = true;

  nativeBuildInputs = with pkgs; [
    curl
    gawk
    texinfo
    bison
    flex
    gperf
  ];
  buildInputs = with pkgs; [
    libmpc
    mpfr
    gmp
    expat
  ];
}
