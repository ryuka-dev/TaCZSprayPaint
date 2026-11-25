# TaCZ Spray Paint：服主与整合包作者说明

这里的一切都是同一个文件夹 `config/taczspray/` 里的普通文件。专用服务端是服务端的 `config` 文件夹，单人游戏是游戏目录的
`config` 文件夹。不用写代码，玩家也不用多装任何东西：玩家进服时，服务端会把名字和你自己做的图案发给他们。

改完任何东西后执行 `/reload`（或者重启）。写错的那个文件会被跳过，日志里会写明是哪个文件、错在哪里，其余照常加载。

第一次运行时，mod 会把这份说明和几个示例写到 `config/taczspray/examples/`。`examples/` 里的东西不会加载。要用哪个，就把它复制到 `examples/` 外面对应的位置：`examples/series/metal.json` 复制到 `config/taczspray/series/metal.json`，`examples/finishes/example_copper/` 整个文件夹复制到 `config/taczspray/finishes/example_copper/`。删掉 `examples/` 后不会再写一份。

```
config/taczspray/
  settings.toml          开关：合成、概率、普通罐出什么
  series/<编号>.json     你的系列（类似武器箱）
  finishes/<编号>/       你自己的外观：pattern.png 和 finish.json
  examples/              这份说明和示例（不加载）
```

## 开关（`settings.toml`）

第一次启动时 Forge 自动生成这个文件，里面带说明。

| 设置 | 默认 | 作用 |
|---|---|---|
| `[crafting] spray_can` | `true` | 玩家能不能合成喷漆罐。关掉后配方就不存在了（配方书和 JEI 里也没有），喷漆罐只能靠 `/taczspray give` 获得 |
| `[crafting] spray_case` | `true` | 十连喷漆箱，同上（8 个喷漆罐加 1 颗钻石）。一箱一次出十个外观，玩家选一个留下 |
| `[crafting] paint_remover` | `true` | 去漆剂，同上 |
| `[odds] grades` | `[]` | 五档外观等级的权重，从制式到孤品。留空就用默认的 `[79.92, 15.98, 3.2, 0.64, 0.26]`。权重可以是 0，合计不必是 100。权重为 0 的档不会从普通喷漆罐出，也不会从没写自己 `grades` 的系列出；只出一款外观的喷漆罐照样给 |
| `[odds] wear_bands` | `[]` | 五个外观成色的权重：崭新出厂、略有磨损、久经沙场、破损不堪、战痕累累。留空就是 `[3, 24, 33, 24, 16]`。写成 `[3, 24, 33, 0, 0]` 就永远不出最旧的两档。自己写了 `wear` 范围的外观，只在那个范围里按这些权重出；如果范围整个落在权重为 0 的档里，以范围为准 |
| `[plain_can] series` | `""` | 普通喷漆罐从这个系列里抽。普通喷漆罐指合成出来的，以及发放时没指定系列或外观的。留空就是全部外观，但标了 `"plain_cans": false` 的自定义外观除外。写了但这个系列不存在时，普通喷漆罐一律不喷，并说明原因 |

写错的值（比如负数的权重、只写了四个权重）不会被使用，日志里会说明原因。文件保持你写的样子，不会被改掉。

要改配方，用普通的数据包。在 `world/datapacks/my_recipes/` 里（带上 `pack.mcmeta`）放一个
`data/taczspray/recipes/spray_can.json`，它会替换掉我们的配方：

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

十连喷漆箱的配方是 `spray_case.json`，去漆剂的是 `paint_remover.json`。你的配方会整个替换掉我们的，所以上面的合成开关只有在你加上我们这一行时才对它起作用：
`"conditions": [{"type": "taczspray:craftable", "item": "spray_can"}],`。KubeJS、CraftTweaker 也可以改配方。

## 发放喷漆罐

```
/taczspray give <玩家> [数量]                  普通喷漆罐
/taczspray give <玩家> series <系列> [数量]    某个系列的喷漆罐
/taczspray give <玩家> look <图案> [数量]      只出某一款外观的喷漆罐
/taczspray give <玩家> case ...                同上，换成十连喷漆箱
```

十连喷漆箱一次出十个外观，玩家选一个留下，例如 `/taczspray give @a case series metal 1`。只出一款外观的箱子，出的是这款外观的十种磨损。玩家选之前，十个结果存在枪上；所有玩家都能用 `/taczspray choose` 重新打开来选。

需要权限等级 2。系列和图案都能按 Tab 补全。能执行指令的东西都能用它：商店、开箱、投票奖励、任务。玩家背包装不下的会掉在脚边。

只出一款外观的喷漆罐，磨损和图案的位置照样随机，所以每把枪都不一样。喷漆罐喷不了玩家手里的枪时，会说明原因，并且不消耗。这种情况包括：系列被删了、系列里没有给这把枪的外观、这把枪没有指定的外观。

内置图案：`sakura`、`celadon`、`ice`、`rust`、`lava`、`neon`、`hazard`、`candy`、`marble`、`brass`、`dots`、
`sunset`、`waves`、`tartan`、`brushed`、`chrome`、`anodized`、`gold`、`titanium`。你自己的图案叫 `custom_<文件夹名>`。

## 系列（`series/<编号>.json`）

一个系列是一组图案加上它自己的概率，类似武器箱。文件名就是它的编号，只能用小写字母、数字和 `_`。

```json
{
  "v": 1,
  "name": {"en_us": "Metal Collection", "zh_cn": "金属收藏"},
  "looks": ["brushed", "chrome", "anodized", "gold", "titanium", "custom_example_copper"],
  "grades": [0, 50, 35, 15, 0]
}
```

- `name`：可以写一句，也可以按语言各写一句。喷漆罐的悬停说明里会显示。语言用游戏的代码（`en_us`、`zh_cn`、
  `de_de`……）。玩家的语言没写时，先用同一种语言的另一个版本（繁中玩家看到 `zh_cn` 的），再用英文。
- `looks`：包含哪些图案。每把枪能喷出的，是这些图案里这把枪有的那些。不存在的图案会被跳过，日志里会说明。
- `grades`（可选）：五个权重，写法同开关里的。不写就用开关里的等级权重，开关里也没写就用默认的。

## 自己的外观（`finishes/<编号>/`）

每个外观一个文件夹，里面放两个文件。文件夹名（小写字母、数字和 `_`，最多 40 个字符）就是图案名 `custom_<文件夹名>`。在系列、`look` 喷漆罐和指令里都用这个名字。

**`pattern.png`**：一张能无缝平铺的正方形小图，边长 8 到 128 像素。它会像内置图案一样在枪上重复铺开。透明度不起作用。枪原来的明暗、磨损和脏污会叠在上面，所以纯色块也好看。

**`finish.json`**：

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

只有 `v` 和 `grade` 必须写，其余都可以不写：

| 字段 | 默认 | 意思 |
|---|---|---|
| `name` | 文件夹名 | 可以写一句，也可以按语言各写一句 |
| `grade` | （必须写） | 1 制式、2 改型、3 定制、4 珍藏、5 孤品。决定颜色和出现的概率 |
| `cover` | `"shell"` | `"shell"` 只漆枪的外壳，和低档外观一样；`"full"` 整把都漆 |
| `wear` | `[0, 1]` | 磨损最少和最多到多少 |
| `guns` | 所有枪 | TaCZ 枪的编号。只会出现在有内置外观的枪上（也就是默认枪包的枪）；其他枪会被跳过，日志里会说明 |
| `projection` | `"box"` | `"side"` 从枪的侧面把图案整体铺上去，条纹、波浪的方向会保持一致；`"box"` 每个面各按各的方向铺 |
| `pixel` | `0.25` | 图案的一个像素在枪上多大，单位是模型单位（1/16 方块），0.05 到 2 |
| `anim` | 没有 | 会动的效果，最多 8 个。`sweep`（`period` 秒数、`width` 宽度，模型单位，最多 64、`strength` 强度 0 到 1）是一条沿枪身扫过的亮带，适合金属；`pulse`（`period`、`amount` 0 到 2）是发光一闪一闪；`hue`（`period`、`degrees` 正负 180 以内）让颜色来回变；`flow`（`speed`，每秒几个图案像素，正负 64 以内）让图案流动。`period` 大于 0、最多 3600 |
| `material` | 普通漆 | 在读 LabPBR 贴图的光影包下的材质：`smoothness` 光滑度 0 到 1，`metal` 是不是金属。没有这种光影包时没有任何影响 |
| `plain_cans` | `true` | 写 `false` 时普通喷漆罐不会出这款，只能从系列、`look` 喷漆罐或指令获得 |

最多 64 个。超过 1 MB 的 `pattern.png` 不读。`/reload` 之后玩家不用重进就能看到新外观，也不用放进资源包。

## 其他

- 喷漆罐和去漆剂每用一次消耗一个，但同一把枪想重喷多少次都可以。创造模式不消耗。
- 外观只改颜色，模型、动画、枪声都不变。卸掉这个 mod 后，所有枪就是普通的枪。
- 服务端和每个玩家都要装这个 mod，而且版本要一致。
