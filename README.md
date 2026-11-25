# TaCZ Spray Paint

Spray paint for [Timeless and Classics Zero](https://modrinth.com/mod/timeless-and-classics-zero) guns.

A spray can gives the gun in your off hand a random finish: the pattern, its grade and how worn it looks are rolled
every time, and no two guns come out the same. Each spray uses up one can, and a gun can be sprayed again as often as
you like. Paint remover takes the finish off, one bottle at a time.

Finishes only change colours. Models, animations, sounds and stats stay exactly as they are.

## For server owners and modpack makers

Everything is plain files in `config/taczspray/`, with no code needed, and players install nothing extra:

- **Your own finishes**: drop a small tiling `pattern.png` and a few lines of `finish.json` into a folder. The server
  sends the pattern to players when they join.
- **Series**: sets of finishes with their own odds, like weapon cases.
- **Cans that give one finish**, and `/taczspray give` for shops, crates and rewards.
- **Switches**: whether cans can be crafted, the grade and wear odds, and what plain cans give.

See the [server owners' guide](docs/server-owners.md) ([中文](docs/server-owners.zh_cn.md)). The first start also
writes it, with examples, into `config/taczspray/examples/`.

Work in progress: nothing is released yet.

## Requirements

- Minecraft 1.20.1, Forge 47
- Timeless and Classics Zero 1.1.8-hotfix, 1.1.8-hotfix2 or 1.2.0-hotfix (the unmodified release)
- Install on both the server and the clients

## License

Code: GPL-3.0, the same as Timeless and Classics Zero. The default gun pack is not redistributed: finishes are
composited onto the textures already installed with TaCZ, on the player's own machine.
