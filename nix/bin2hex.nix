{ pkgs }:
pkgs.writers.writePython3Bin "bin2hex" { } ''
  import sys

  with open(sys.argv[1], "rb") as f:
      cnt = 3
      s = ["00"]*4
      while True:
          data = f.read(1)
          if not data:
              print("".join(s))
              exit(0)
          s[cnt] = "{:02X}".format(data[0])
          if cnt == 0:
              print("".join(s))
              s = ["00"]*4
              cnt = 4
          cnt -= 1
''
