import os
import sys

def main(args):
  if len(args) not in (3, 4):
    print("Error: paste command requires 3 (x y z) or 4 (x y z label) params.", file=sys.stderr)
    return

  x = int(args[0])
  y = int(args[1])
  z = int(args[2])

  if len(args) == 3:
    label = "__default__"
  else:
    label = args[3].replace("/", "_").replace("\\", "_").replace(" ", "_")

  paste_file = open(os.path.join("minescript", "copies", label + ".txt"))
  for line in paste_file.readlines():
    line = line.rstrip()
    if line.startswith("#"):
      continue

    fields = line.split(" ", 4)
    if fields[0] != "/setblock":
      print(
          "Error: paste works only with setblock commands, but got the following instead:\n",
          file=sys.stderr)
      print(line)
      return

    # Apply coordinate offsets:
    fields[1] = str(int(fields[1]) + x)
    fields[2] = str(int(fields[2]) + y)
    fields[3] = str(int(fields[3]) + z)

    print(" ".join(fields))

if __name__ == "__main__":
  main(sys.argv[1:])
