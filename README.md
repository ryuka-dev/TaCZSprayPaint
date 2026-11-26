# TaCZ Spray Paint

An add-on for [Timeless and Classics Zero](https://modrinth.com/mod/timeless-and-classics-zero) (TaCZ) that adds
spray paint finishes for guns.

## Items

- **Spray Can**: gives a gun a random finish. Each finish has a pattern, a grade and an amount of wear, all rolled
  separately, so no two guns come out the same. A gun can be sprayed again any number of times.
- **Spray Can Case**: gives ten finishes at once; the player picks one of them for the gun.
- **Paint Remover**: removes the finish from a gun.

To use one, hold it in the main hand and a gun in the off hand, then press and hold right-click. Each use consumes one
item. After spraying, a screen shows the painted gun and its wear; any key skips it, and the result is posted in chat.

Finishes only change colours. Models, animations, sounds and stats are unchanged.

## Configuration

The mod is configured through files in `config/taczspray/`. No code is needed, and clients do not need anything else
installed: the server sends custom finishes to players when they join.

- **Custom finishes**: a folder with a tiling `pattern.png` and a short `finish.json`.
- **Series**: groups of finishes with their own odds, like weapon cases.
- **Cans for one specific finish**, and the `/taczspray give` command for shops, crates and rewards.
- **Settings**: crafting switches, grade and wear odds, and what plain spray cans draw from.

Details are in the [configuration guide](docs/server-owners.md) ([中文](docs/server-owners.zh_cn.md)). On the first
start the mod also writes the guide and some examples into `config/taczspray/examples/`.

Not released yet.

## Languages

English and Simplified Chinese are written by hand. The other 18 are the languages TaCZ ships with, machine translated
and then checked by translating each line back. They use TaCZ's word for "gun" and Minecraft's words for "main hand"
and "off hand", so both mods use the same terms in each language.

Corrections are welcome. Each language is one file in `src/main/resources/assets/taczspray/lang/`; F3+T reloads it in
game. Lines missing from a file are shown in English.

## Requirements

- Minecraft 1.20.1, Forge 47
- Timeless and Classics Zero 1.1.8-hotfix, 1.1.8-hotfix2 or 1.2.0-hotfix (the unmodified release)
- Install on both the server and the clients

## License

Code: GPL-3.0, the same as Timeless and Classics Zero.

The finish data in `src/main/resources/assets/taczspray/finishes/` is made from the models of TaCZ's default gun pack.
For each gun that can be painted, it keeps the corners and texture coordinates of the faces that get painted, and
which texture pixels to paint, in a format of this mod's own. The pack is by Serene Wave Studio and Timeless Squad
(art by NekoCrane, Receke and Pos_2333) and is licensed under
[CC BY-NC-ND 4.0](https://creativecommons.org/licenses/by-nc-nd/4.0/), so this data is shared under the same license,
for non-commercial use only. It holds none of their texture pixels: finishes are painted onto the textures already
installed with TaCZ, on the player's own machine.
