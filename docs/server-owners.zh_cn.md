# TaCZ Spray Paint 配置说明

本文说明本 mod 的配置文件与指令：合成开关、概率、系列、自定义外观，以及发放喷漆罐。配置全部通过文件完成，不需要编写代码；客户端也不需要额外安装任何内容。

## 配置目录

配置文件放在 `config/taczspray/` 中。专用服务端是服务端目录下的 `config/taczspray/`，单人游戏是游戏目录下的 `config/taczspray/`。

```
config/taczspray/
  settings.toml          设置：合成开关、概率、普通喷漆罐的抽取范围
  series/<编号>.json     系列（类似武器箱）
  finishes/<编号>/       自定义外观，每个外观一个文件夹：pattern.png 与 finish.json
  examples/              本说明与示例（不会被加载）
```

玩家进入服务器时，服务端会把系列名、自定义外观的名字和图案发送给客户端。

## 生效方式

修改后执行 `/reload` 或重启即可生效。某个文件有错误时只跳过该文件，日志中会写明文件名和错误原因，其他文件照常加载。

## 示例

首次启动时，mod 会把本说明和几个示例写入 `config/taczspray/examples/`。这个文件夹里的内容不会被加载。要启用某个示例，把它复制到 `examples/` 外的对应位置：

- `examples/series/metal.json` 复制到 `config/taczspray/series/metal.json`
- `examples/finishes/example_copper/` 整个文件夹复制到 `config/taczspray/finishes/example_copper/`

删除 `examples/` 后不会再次生成。

## 设置（`settings.toml`）

该文件由 Forge 在首次启动时生成，文件中附有各项说明。

| 设置 | 默认值 | 说明 |
|---|---|---|
| `[crafting] spray_can` | `true` | 是否允许合成喷漆罐。关闭后配方不存在（配方书和 JEI 中也不显示），喷漆罐只能通过 `/taczspray give` 获得 |
| `[crafting] spray_case` | `true` | 是否允许合成喷漆箱（8 个喷漆罐加 1 颗钻石）。喷漆箱一次给出十个外观，玩家从中选择一个 |
| `[crafting] paint_remover` | `true` | 是否允许合成去漆剂 |
| `[odds] grades` | `[]` | 五档外观等级（制式、改型、定制、珍藏、孤品）的权重。留空时使用默认值 `[79.92, 15.98, 3.2, 0.64, 0.26]`。权重可以为 0，总和不必为 100。权重为 0 的等级不会从普通喷漆罐中出现，也不会从没有设置 `grades` 的系列中出现；指定外观的喷漆罐不受此项影响 |
| `[odds] wear_bands` | `[]` | 五档成色（崭新出厂、略有磨损、久经沙场、破损不堪、战痕累累）的权重。留空时使用默认值 `[3, 24, 33, 24, 16]`。例如 `[3, 24, 33, 0, 0]` 表示不出现最旧的两档。设置了 `wear` 范围的自定义外观，只在该范围内按这些权重取值；范围全部落在权重为 0 的档内时，以范围为准 |
| `[plain_can] series` | `""` | 普通喷漆罐从哪个系列中抽取。普通喷漆罐指合成得到的喷漆罐，以及发放时没有指定系列或外观的喷漆罐。留空时从全部外观中抽取（设置了 `"plain_cans": false` 的自定义外观除外）。填写的系列不存在时，普通喷漆罐无法使用，并会提示原因 |

无效的值（例如负数权重、权重不是五个）不会生效，日志中会写明原因。文件内容保持原样，不会被自动改写。

### 修改配方

配方可以用数据包替换。在 `world/datapacks/my_recipes/` 中放入 `pack.mcmeta` 和 `data/taczspray/recipes/spray_can.json`，即可替换喷漆罐的配方：

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

喷漆箱的配方文件是 `spray_case.json`，去漆剂的是 `paint_remover.json`。替换后的配方不再受上面的合成开关控制；如需保留开关，在配方中加上这一行：
`"conditions": [{"type": "taczspray:craftable", "item": "spray_can"}],`。也可以使用 KubeJS、CraftTweaker 修改配方。

## 指令

### 发放喷漆罐与喷漆箱

```
/taczspray give <玩家> [数量]                  普通喷漆罐
/taczspray give <玩家> series <系列> [数量]    指定系列的喷漆罐
/taczspray give <玩家> look <图案> [数量]      指定外观的喷漆罐
/taczspray give <玩家> case ...                同上，发放的是喷漆箱
```

例如 `/taczspray give @a case series metal 1` 给所有玩家各一个 `metal` 系列的喷漆箱。

- 需要权限等级 2。系列和图案可以用 Tab 补全
- 商店、开箱、投票奖励、任务等任何能执行指令的插件或 mod 都可以调用
- 玩家背包放不下时，物品掉落在玩家脚下

### 选择喷漆箱的结果

`/taczspray choose`：重新打开喷漆箱的选择界面。所有玩家都可以使用，执行时需要手持那把枪。喷漆箱给出的十个外观在玩家选择之前保存在枪上，关闭界面不会丢失。

### 喷漆规则

- 指定外观的喷漆罐，磨损和图案位置仍是随机的，因此每把枪的结果都不相同。指定外观的喷漆箱给出该外观的十种不同磨损
- 喷漆罐无法用于玩家手中的枪时，会提示原因，并且不会被消耗。这些情况包括：系列已被删除、系列中没有这把枪的外观、这把枪没有指定的外观

### 内置图案

`sakura`、`celadon`、`ice`、`rust`、`lava`、`neon`、`hazard`、`candy`、`marble`、`brass`、`dots`、
`sunset`、`waves`、`tartan`、`brushed`、`chrome`、`anodized`、`gold`、`titanium`。自定义外观的图案名是 `custom_<文件夹名>`。

## 系列（`series/<编号>.json`）

系列是一组图案及其概率，类似武器箱。文件名就是系列编号，只能使用小写字母、数字和 `_`。

```json
{
  "v": 1,
  "name": {"en_us": "Metal Collection", "zh_cn": "金属收藏"},
  "looks": ["brushed", "chrome", "anodized", "gold", "titanium", "custom_example_copper"],
  "grades": [0, 50, 35, 15, 0]
}
```

- `name`：系列名，显示在喷漆罐的悬停说明中。可以只写一个名字，也可以按语言分别写。语言使用游戏的语言代码（`en_us`、`zh_cn`、`de_de` 等）。没有写玩家所用语言时，先使用同一语言的其他变体（例如繁体中文玩家看到 `zh_cn` 的名字），再使用英文
- `looks`：系列包含的图案。每把枪只会喷出其中这把枪有的图案。不存在的图案会被跳过，日志中会说明
- `grades`（可选）：五个等级权重，写法与设置中的 `[odds] grades` 相同。不写时使用设置中的值；设置中也没有写时使用默认值

## 自定义外观（`finishes/<编号>/`）

每个外观一个文件夹，内含 `pattern.png` 和 `finish.json` 两个文件。文件夹名只能使用小写字母、数字和 `_`，最多 40 个字符。外观的图案名是 `custom_<文件夹名>`，在系列、指定外观的喷漆罐和指令中都使用这个名字。

**`pattern.png`**：可以无缝平铺的正方形图片，边长 8 到 128 像素，会像内置图案一样在枪上重复铺开。透明度无效。枪原有的明暗、磨损和脏污会叠加在图案上，因此纯色图案也有不错的效果。

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

`v` 和 `grade` 为必填项，其余均可省略：

| 字段 | 默认值 | 说明 |
|---|---|---|
| `name` | 文件夹名 | 外观名。可以只写一个，也可以按语言分别写 |
| `grade` | （必填） | 等级：1 制式、2 改型、3 定制、4 珍藏、5 孤品。决定名字的颜色和出现概率 |
| `cover` | `"shell"` | 喷涂范围。`"shell"` 只喷枪的外壳，与低等级外观相同；`"full"` 喷整把枪 |
| `wear` | `[0, 1]` | 磨损的最小值和最大值 |
| `guns` | 所有枪 | 适用的 TaCZ 枪械编号。只支持有内置外观的枪（即默认枪包中的枪）；其他枪会被跳过，日志中会说明 |
| `projection` | `"box"` | 图案的铺法。`"side"` 从枪的侧面整体投射，条纹、波浪等图案方向一致；`"box"` 每个面按各自的方向铺 |
| `pixel` | `0.25` | 图案中一个像素在枪上的大小，单位是模型单位（1/16 方块），范围 0.05 到 2 |
| `anim` | 无 | 动态效果，最多 8 个，见下文 |
| `material` | 普通漆面 | 在支持 LabPBR 的光影包下的材质：`smoothness` 为光滑度（0 到 1），`metal` 表示是否为金属。不使用这类光影包时没有影响 |
| `plain_cans` | `true` | 设为 `false` 时，普通喷漆罐不会喷出这个外观，只能通过系列、指定外观的喷漆罐或指令获得 |

`anim` 中每一项的 `type` 可以是：

| `type` | 效果 | 参数 |
|---|---|---|
| `sweep` | 沿枪身扫过的亮带，适合金属 | `period` 周期（秒）；`width` 宽度（模型单位，最大 64）；`strength` 强度（0 到 1） |
| `pulse` | 周期性发光 | `period`；`amount` 亮度（0 到 2） |
| `hue` | 颜色来回变化 | `period`；`degrees` 色相变化幅度（-180 到 180） |
| `flow` | 图案流动 | `speed` 速度（每秒移动的图案像素数，-64 到 64） |

`period` 必须大于 0，最大 3600。

自定义外观最多 64 个；超过 1 MB 的 `pattern.png` 不会被读取。执行 `/reload` 后，玩家无需重新进入服务器即可看到新外观，也不需要资源包。

## 其他

- 喷漆罐、喷漆箱和去漆剂每次使用消耗一个，同一把枪可以反复喷涂。创造模式下不消耗
- 外观只改变颜色，不改变模型、动画和枪声。卸载本 mod 后，所有枪恢复原样
- 服务端和所有客户端都需要安装本 mod，且版本一致
