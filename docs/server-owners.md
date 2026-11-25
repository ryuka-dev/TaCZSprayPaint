# TaCZ Spray Paint: guide for server owners and modpack makers

Everything here is plain files in one folder, `config/taczspray/`. On a dedicated server that is the server's
`config` folder; in single player it is the game's. Nothing needs code, and players install nothing extra: the server
sends them the names and your own patterns when they join.

After changing anything, run `/reload` (or restart). A file that has a mistake in it is skipped, and the log says
which one and why; everything else loads as normal.

The first time the mod runs it writes this guide and some examples into `config/taczspray/examples/`. Nothing loads
from `examples/`. To use one, copy it to the same place outside `examples/`: `examples/series/metal.json` goes to
`config/taczspray/series/metal.json`, and the folder `examples/finishes/example_copper/` goes to
`config/taczspray/finishes/example_copper/`. If you delete `examples/`, it is not written again.

```
config/taczspray/
  settings.toml          switches: crafting, odds, what plain cans give
  series/<id>.json       your series ("cases")
  finishes/<id>/         your own finishes: pattern.png and finish.json
  examples/              this guide and examples (not loaded)
```

## Settings (`settings.toml`)

Forge writes this file with comments on the first start.

| Setting | Default | What it does |
|---|---|---|
| `[crafting] spray_can` | `true` | Whether players can craft spray cans. Off: the recipe is gone (also from the recipe book and JEI), and cans only come from `/taczspray give`. |
| `[crafting] spray_case` | `true` | The same for spray can cases (eight spray cans and a diamond). A case rolls ten finishes at once and the player keeps one of them. |
| `[crafting] paint_remover` | `true` | The same for paint remover. |
| `[odds] grades` | `[]` | Five weights for the grades, standard to unique. Empty means the default, `[79.92, 15.98, 3.2, 0.64, 0.26]`. A weight may be 0, and they need not add up to 100. A grade weighted 0 never comes out of plain cans, or of series that have no `grades` of their own. A can for one finish still gives it. |
| `[odds] wear_bands` | `[]` | Five weights for how worn a finish comes out: factory new, minimal wear, field-tested, well-worn, battle-scarred. Empty means `[3, 24, 33, 24, 16]`. `[3, 24, 33, 0, 0]` never gives the two most worn. A finish with its own `wear` range only comes out inside that range, by these odds. If the whole range lies in bands weighted 0, the range wins. |
| `[plain_can] series` | `""` | A series that plain spray cans draw from. Plain cans are crafted ones, and those given without a series or a finish. Empty means every finish, except your own finishes marked `"plain_cans": false`. If the series is not there, plain cans spray nothing and say why. |

A value that is wrong, such as a negative weight or four weights instead of five, is not used, and the log says why.
The file is left as you wrote it.

To change a recipe, use an ordinary data pack. Put this in `world/datapacks/my_recipes/` (with a `pack.mcmeta`) as
`data/taczspray/recipes/spray_can.json`, and it replaces ours:

```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": [" N ", "IDI", " I "],
  "key": {
    "N": {"item": "minecraft:iron_nugget"},
    "I": {"item": "minecraft:iron_ingot"},
    "D": {"tag": "forge:dyes"}
  },
  "result": {"item": "taczspray:spray_can"}
}
```

The spray can case's recipe is `spray_case.json`, the paint remover's `paint_remover.json`. Your recipe replaces ours whole, so the crafting switch above only
applies to it if you add our line `"conditions": [{"type": "taczspray:craftable", "item": "spray_can"}],`. KubeJS
and CraftTweaker work too.

## Giving cans

```
/taczspray give <players> [count]                  plain cans
/taczspray give <players> series <series> [count]  cans of a series
/taczspray give <players> look <pattern> [count]   cans that give one finish
/taczspray give <players> case ...                 the same, as spray can cases
```

A spray can case rolls ten finishes at once and the player keeps one of them, for example
`/taczspray give @a case series metal 1`. A case for one finish gives ten of that finish, worn differently. Until the
player picks one, the ten stay on the gun; `/taczspray choose`, which every player can use, opens them again.

This needs permission level 2. Series and patterns tab-complete. Anything that can run a command can use it: shops,
crates, vote rewards, quests. What does not fit in a player's inventory drops at their feet.

A can that gives one finish still rolls its wear and where the pattern sits, so no two guns come out the same. If a
can cannot paint the gun in a player's hand, it says why and is not used up. That happens when the series was
removed, the series has nothing for that gun, or the gun does not have that finish.

Built-in patterns: `sakura`, `celadon`, `ice`, `rust`, `lava`, `neon`, `hazard`, `candy`, `marble`, `brass`, `dots`,
`sunset`, `waves`, `tartan`, `brushed`, `chrome`, `anodized`, `gold`, `titanium`. Your own are `custom_<folder>`.

## Series (`series/<id>.json`)

A series is a set of patterns with its own odds, like a weapon case. The file's name is its id: lower-case letters,
digits and `_`.

```json
{
  "v": 1,
  "name": {"en_us": "Metal Collection", "zh_cn": "金属收藏"},
  "looks": ["brushed", "chrome", "anodized", "gold", "titanium", "custom_example_copper"],
  "grades": [0, 50, 35, 15, 0]
}
```

- `name`: one text, or one per language. A can's tooltip shows it. Languages are the game's codes (`en_us`, `zh_cn`,
  `de_de`...). A player whose language you left out gets another variety of it if you wrote one (`zh_cn` for a
  `zh_tw` player), then English.
- `looks`: the patterns. On each gun, a can gives one of the patterns that gun has. A pattern that does not exist is
  skipped, and the log says so.
- `grades` (optional): five weights, as in the settings. Leave it out to use the settings' grade weights, or the
  default.

## Your own finishes (`finishes/<id>/`)

A folder per finish, holding two files. The folder's name (lower-case letters, digits and `_`, at most 40 characters)
becomes the pattern `custom_<folder>`. Use that name in series, in `look` cans and in commands.

**`pattern.png`**: a small square image that tiles seamlessly, 8 to 128 pixels a side. It is repeated over the gun,
like the built-in patterns. Transparency is ignored. The gun's shading and the wear and dirt are added on top, so
flat colours look fine.

**`finish.json`**:

```json
{
  "v": 1,
  "name": {"en_us": "Polished Copper", "zh_cn": "抛光紫铜"},
  "grade": 3,
  "cover": "full",
  "wear": [0.0, 0.5],
  "guns": ["tacz:ak47", "tacz:m4a1", "tacz:deagle"],
  "projection": "side",
  "anim": [{"type": "sweep", "period": 3.0, "width": 4, "strength": 0.5}],
  "material": {"smoothness": 0.8, "metal": true},
  "plain_cans": false
}
```

Only `v` and `grade` are needed. Everything else is optional:

| Field | Default | Meaning |
|---|---|---|
| `name` | the folder's name | One text, or one per language. |
| `grade` | (needed) | 1 standard, 2 modified, 3 custom, 4 collector, 5 unique. Sets the colour and how often it comes out. |
| `cover` | `"shell"` | `"shell"` paints the gun's outer shell, the way the low grades do; `"full"` paints the whole gun. |
| `wear` | `[0, 1]` | The least and the most wear it comes out with. |
| `guns` | every gun | TaCZ gun ids. It goes only on guns that have built-in finishes (the default gun pack's); others are skipped, and the log says so. |
| `projection` | `"box"` | `"side"` lays the pattern on from the gun's side, so stripes and waves keep one direction. `"box"` turns it with each face. |
| `pixel` | `0.25` | How big one pattern pixel is on the gun, in model units (1/16 of a block). 0.05 to 2. |
| `anim` | none | Up to 8 moving effects. `sweep` (`period` in seconds, `width` in model units up to 64, `strength` 0 to 1) is a band of light along the gun, good for metal. `pulse` (`period`, `amount` 0 to 2) is a glow. `hue` (`period`, `degrees` up to 180 either way) shifts the colour back and forth. `flow` (`speed`, pattern pixels a second, up to 64 either way) moves the pattern. A `period` is over 0 and at most 3600. |
| `material` | plain paint | How it looks under a shader pack that reads LabPBR maps: `smoothness` 0 to 1, and `metal`. Without such a shader pack it changes nothing. |
| `plain_cans` | `true` | `false` keeps it out of plain spray cans, so it only comes from a series, a `look` can or a command. |

At most 64 finishes. A `pattern.png` over 1 MB is not read. Players see a new finish after `/reload` with no
restart, and nothing goes in a resource pack.

## Good to know

- Spray cans and the remover are used up one at a time, but a gun can be resprayed as often as you like. In creative
  mode nothing is used up.
- A finish only changes colours: models, animations and sounds stay as they are. Remove the mod and every gun is a
  normal gun again.
- Both the server and every player need the mod, at the same version.
