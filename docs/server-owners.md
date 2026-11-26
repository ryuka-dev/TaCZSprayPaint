# TaCZ Spray Paint configuration

This document covers the mod's configuration files and commands: crafting switches, odds, series, custom finishes, and
giving spray cans. Everything is set up through files, with no code. Clients do not need to install anything else.

## Configuration folder

The files are in `config/taczspray/`: under the server's folder on a dedicated server, under the game folder in single
player.

```
config/taczspray/
  settings.toml          settings: crafting switches, odds, what plain spray cans draw from
  series/<id>.json       series (like weapon cases)
  finishes/<id>/         custom finishes, one folder each: pattern.png and finish.json
  examples/              this document and examples (not loaded)
```

When a player joins, the server sends them the series names and the names and patterns of custom finishes.

## Applying changes

Run `/reload` or restart after a change. A file with an error is skipped, and the log gives the file and the reason;
the other files load as normal.

## Examples

On the first start the mod writes this document and some examples into `config/taczspray/examples/`. Nothing in that
folder is loaded. To use an example, copy it to the matching place outside `examples/`:

- `examples/series/metal.json` to `config/taczspray/series/metal.json`
- the whole folder `examples/finishes/example_copper/` to `config/taczspray/finishes/example_copper/`

Once `examples/` is deleted, it is not written again.

## Settings (`settings.toml`)

Forge creates this file on the first start, with a comment on each setting.

| Setting | Default | Description |
|---|---|---|
| `[crafting] spray_can` | `true` | Whether spray cans can be crafted. When off, the recipe does not exist (it is not in the recipe book or JEI either), and spray cans only come from `/taczspray give`. |
| `[crafting] spray_case` | `true` | Whether spray can cases can be crafted (eight spray cans and a diamond). A case gives ten finishes at once and the player picks one. |
| `[crafting] paint_remover` | `true` | Whether paint remover can be crafted. |
| `[odds] grades` | `[]` | Weights for the five grades (standard, modified, custom, collector's, unique). Empty uses the default, `[79.92, 15.98, 3.2, 0.64, 0.26]`. A weight can be 0, and the weights need not add up to 100. A grade weighted 0 never comes from plain spray cans, or from series without their own `grades`. Cans for one specific finish are not affected. |
| `[odds] wear_bands` | `[]` | Weights for the five wear levels (factory new, minimal wear, field-tested, well-worn, battle-scarred). Empty uses the default, `[3, 24, 33, 24, 16]`. For example, `[3, 24, 33, 0, 0]` never gives the two most worn. A custom finish with a `wear` range only comes out within that range, by these weights. If the whole range falls in levels weighted 0, the range takes precedence. |
| `[plain_can] series` | `""` | The series plain spray cans draw from. Plain cans are crafted cans and cans given without a series or a finish. Empty draws from every finish, except custom finishes with `"plain_cans": false`. If the series does not exist, plain cans cannot be used and show the reason. |

An invalid value, such as a negative weight or a number of weights other than five, is not used, and the log gives
the reason. The file is left as written; it is not rewritten automatically.

### Changing recipes

Recipes can be replaced with a data pack. Put a `pack.mcmeta` and `data/taczspray/recipes/spray_can.json` in
`world/datapacks/my_recipes/` to replace the spray can's recipe:

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

The spray can case's recipe file is `spray_case.json`, the paint remover's is `paint_remover.json`. A replaced recipe
is no longer controlled by the crafting switches above; to keep the switch, add this line to the recipe:
`"conditions": [{"type": "taczspray:craftable", "item": "spray_can"}],`. KubeJS and CraftTweaker can also change
recipes.

## Commands

### Giving spray cans and cases

```
/taczspray give <players> [count]                  plain spray cans
/taczspray give <players> series <series> [count]  spray cans of a series
/taczspray give <players> look <pattern> [count]   spray cans for one specific finish
/taczspray give <players> case ...                 the same, giving spray can cases
```

For example, `/taczspray give @a case series metal 1` gives every player one case of the `metal` series.

- Requires permission level 2. Series and patterns tab-complete.
- Any plugin or mod that can run commands can use it: shops, crates, vote rewards, quests.
- Items that do not fit in a player's inventory drop at their feet.

### Picking from a case

`/taczspray choose` reopens the pick screen of a spray can case. Every player can use it, holding the gun the case
was opened on. Until the player picks, the ten finishes are stored on the gun, so closing the screen loses nothing.

### Spraying rules

- A can for one specific finish still rolls the wear and where the pattern sits, so every gun comes out different.
  A case for one specific finish gives that finish with ten different amounts of wear.
- If a can cannot be used on the gun in the player's hand, it shows the reason and is not used up. This happens when
  the series has been deleted, the series has no finish for that gun, or the gun does not have the given finish.

### Built-in patterns

`sakura`, `celadon`, `ice`, `rust`, `lava`, `neon`, `hazard`, `candy`, `marble`, `brass`, `dots`, `sunset`, `waves`,
`tartan`, `brushed`, `chrome`, `anodized`, `gold`, `titanium`. A custom finish's pattern name is `custom_<folder>`.

## Series (`series/<id>.json`)

A series is a set of patterns with its own odds, like a weapon case. The file name is the series id, using only
lower-case letters, digits and `_`.

```json
{
  "v": 1,
  "name": {"en_us": "Metal Collection", "zh_cn": "金属收藏"},
  "looks": ["brushed", "chrome", "anodized", "gold", "titanium", "custom_example_copper"],
  "grades": [0, 50, 35, 15, 0]
}
```

- `name`: the series name, shown in the spray can's tooltip. Either one name, or one per language, using the game's
  language codes (`en_us`, `zh_cn`, `de_de` and so on). If the player's language is missing, another variety of the
  same language is used (a `zh_tw` player sees the `zh_cn` name), then English.
- `looks`: the patterns in the series. A gun only gets the patterns it has. A pattern that does not exist is skipped,
  and the log says so.
- `grades` (optional): five grade weights, written as in `[odds] grades`. When left out, the value from the settings
  is used, or the default if the settings have none.

## Custom finishes (`finishes/<id>/`)

Each finish is a folder holding `pattern.png` and `finish.json`. The folder name uses only lower-case letters, digits
and `_`, at most 40 characters. The finish's pattern name is `custom_<folder>`; use it in series, in cans for one
specific finish, and in commands.

**`pattern.png`**: a square image that tiles seamlessly, 8 to 128 pixels a side. It is repeated over the gun like
the built-in patterns. Transparency is ignored. The gun's own shading, wear and dirt are laid over the pattern, so
flat colours work well too.

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

`v` and `grade` are required; everything else is optional.

| Field | Default | Description |
|---|---|---|
| `name` | the folder name | The finish name. Either one name, or one per language. |
| `grade` | (required) | 1 standard, 2 modified, 3 custom, 4 collector's, 5 unique. Sets the name's colour and how often it comes out. |
| `cover` | `"shell"` | What gets painted. `"shell"` paints only the gun's outer shell, as the lower grades do; `"full"` paints the whole gun. |
| `wear` | `[0, 1]` | The least and the most wear. |
| `guns` | every gun | TaCZ gun ids it applies to. Only guns with built-in finishes (the default gun pack's) are supported; others are skipped, and the log says so. |
| `projection` | `"box"` | How the pattern is laid on. `"side"` projects it from the side of the gun, so stripes and waves keep one direction; `"box"` lays it on each face in that face's own direction. |
| `pixel` | `0.25` | The size of one pattern pixel on the gun, in model units (1/16 of a block), from 0.05 to 2. |
| `anim` | none | Moving effects, at most 8; see below. |
| `material` | plain paint | The material under shader packs that support LabPBR: `smoothness` from 0 to 1, and `metal` for whether it is metal. No effect without such a shader pack. |
| `plain_cans` | `true` | When `false`, plain spray cans never give this finish; it only comes from a series, a can for this finish, or a command. |

The `type` of each `anim` entry can be:

| `type` | Effect | Parameters |
|---|---|---|
| `sweep` | A band of light moving along the gun, good for metal | `period` in seconds; `width` in model units, at most 64; `strength` from 0 to 1 |
| `pulse` | A glow that comes and goes | `period`; `amount` from 0 to 2 |
| `hue` | The colour shifts back and forth | `period`; `degrees`, from -180 to 180 |
| `flow` | The pattern moves | `speed` in pattern pixels per second, from -64 to 64 |

A `period` must be over 0 and at most 3600.

There can be at most 64 custom finishes. A `pattern.png` over 1 MB is not read. After `/reload`, players see new
finishes without rejoining, and no resource pack is needed.

## Other notes

- Each use of a spray can, case or paint remover uses up one. A gun can be sprayed again as often as you like.
  Nothing is used up in creative mode.
- Finishes only change colours, not models, animations or sounds. With the mod removed, every gun is back to normal.
- The server and every client need the mod, at the same version.
