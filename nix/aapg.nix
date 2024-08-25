{ python3, fetchFromGitLab }:

python3.pkgs.buildPythonPackage {
  pname = "aapg";
  version = "2.2.6";

  src = fetchFromGitLab {
    owner = "shaktiproject";
    repo = "tools/aapg";
    rev = "master";
    sha256 = "sha256-ciktPxf+2QX7R9li5dEut0aR5hX4WI7IznTdDVMIi+E=";
  };

  propagatedBuildInputs = with python3.pkgs; [
    pyyaml
    jinja2
    numpy
  ];

  nativeBuildInputs = with python3.pkgs; [
    setuptools
    wheel
  ];

  doCheck = false;
}
