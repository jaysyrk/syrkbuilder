# SyrkBuilder

A building editor for Minecraft 1.21.11 & 26.2 (Fabric). Shapes, terrain, sculpting brushes, trees, paths, fills,
biomes, colour gradients, text, model, heightmap and schematic import, scripting and noclip, all in one in-game
editor, with a live preview and an undo history you never lose.

Works in your own singleplayer worlds, in creative mode only. It does nothing on servers yet, and nothing in survival.

## Versions

| Minecraft | Branch | Java |
|---|---|---|
| 1.21.11 | `main` | 21 |
| 26.2 | `mc-26.2` | 25 |

Downloads are on Modrinth and on the Releases page.

## Highlights

- **History you can't lose.** Undo survives restarts. Undo, then build something else, and both versions stay
  as branches. See them as a graph, jump to any point, name checkpoints, or rewind just one area.
- **Live preview.** The last thing you place stays adjustable. Arrow keys move it, R turns it, Delete removes
  it, Enter keeps it.
- **Click and drag to build.** Left-click places at the crosshair; hold and drag to keep placing. A whole drag
  is one undo step, and it doesn't flood your chat.
- **Terrain in one click.** Fifteen generators: mountains, hills, mesas, volcanoes, craters, canyons, dunes,
  floating islands, buttes, valleys with a river, fjords, lakes, atolls, archipelagos and swamps, with erosion and
  styles. Brushes grow terrain from noise, erode it, and add boulders, cliffs, rock arches and caves. Or turn any
  greyscale image into terrain, and paint biomes on top.
- **Select smarter.** Magic select grabs a whole build by clicking it. Move it, stack it, hollow it, smooth it.
- **Works with your other tools.** Opens and saves WorldEdit, FAWE and Axiom `.schem` files and Litematica
  `.litematic` files, and picks up your WorldEdit and Litematica schematic folders automatically.

## The editor (F7)

Left-click uses the current tool on the block under your mouse pointer, which is outlined with sparks. Hold
right-click over the world to look around (then it aims at the crosshair), and move with WASD, space and shift.
Settings → Aim at switches back to the crosshair if you prefer it. Middle-click copies the block you aim at into the tool's Blocks field.

| Tool | What it does |
|---|---|
| Shapes | sphere, ellipsoid, dome, cylinder, cone, pyramid, circle, disc, torus, helix, text, arch |
| Terrain | 15 generators (mountain, hills, mesa, volcano, crater, canyon, dunes, island, buttes, valley with a river, fjord, lake, atoll, archipelago, swamp) with radius, height, erosion, roughness, peaks, style and seed; snow, thaw, green, drain; biome painting |
| Brushes | 33 brushes in three groups: blocks (including a clipboard stamp, boulders and cliffs), sculpting and terrain (including a noise terrain generator and erosion) |
| Fill | hole (fills to the brim), connected (paint bucket), room (enclosed 3D) |
| Trees | oak, birch, spruce, pine, jungle, dark oak, acacia, cherry, willow, palm, dead, swamp, and a forest brush |
| Paths | drag or click points, then build a road, wall, tunnel, river, bridge or line |
| Selection | set, walls, outline, replace, line, move, stack, hollow, overlay, naturalize, smooth, count, magic select, expand / contract / shift |
| Clipboard | copy, cut, paste, rotate, mirror, templates, export to `.schem` / `.litematic` |
| Import | OBJ (with MTL textures), glTF / GLB, MagicaVoxel `.vox`, and `.png` / `.jpg` heightmaps as terrain |
| Scripts | run JavaScript build scripts |
| History | the branching timeline; click to jump, shift+click to restore your selection |
| Settings | rebind keys, noclip toggle or hold, fly speed, look sensitivity, quiet chat, particles |

Every Blocks field has a colour picker: choose a colour to see the closest blocks and use one or the whole
palette, or build a gradient between two colours. Blocks can be mixes (`60%stone,40%andesite`) or gradients:

- `grad:stone,andesite,diorite` runs from bottom to top across whatever you're building
- `grad(down):`, `grad(east):`, `grad(west):`, `grad(north):`, `grad(south):` pick another direction, `grad(out):`
  and `grad(in):` run from the centre or towards it, `grad(look):` follows where you're facing, and `grad(1/0/1):`
  takes any direction
- a brush stroke keeps the gradient of its first dab, so overlapping dabs don't lay new bands over each other
- add a range to pin the ends to the world, e.g. `grad(up,60..90):stone,andesite,snow_block` puts stone at y 60 and
  snow at y 90, however far the stroke goes. In the colour picker, aim and click Start at aim and End at aim

Brushes take `rx=`, `ry=` and `rz=` to give them their own size on each axis, e.g. `/sb brush sphere 8 stone ry=2`
paints a flat disc. In the editor, turn on Stretch per axis.

### Noise terrain, masks, erosion and rocks

- `/sb brush terragen 20 strength=24 noise=ridged` grows terrain from noise around the height you aim at. `noise=` is
  `simplex` (even relief), `fractal` (hills with fine detail), `billowy` (rounded lumps, creased valleys) or `ridged`
  (sharp crests). `strength=` is the height in blocks, `scale=` the feature size, `octaves=` how much fine detail, and
  `-n` adds extra small bumps. Drag it for a long range
- `preset=` sets all of that at once and gives the new ground matching surface blocks: `alpine`, `rolling`, `dunes`,
  `mesa`, `islands` or `craggy`, e.g. `/sb brush terragen 30 preset=alpine`. `style=` picks other surface blocks
  (`alpine`, `grassy`, `desert`, `mesa`, `volcanic`, `rocky`, `snowy`)
- `/sb brush lake 12 strength=6` digs a bowl and floods it with water up to the height you aim at, with a sandy bed
- `/sb brush erode 20 strength=0.5` runs water and slope erosion over what is there: gullies, loose scree and softened
  peaks without flattening them
- `/sb brush rockarch 8 stone,andesite rx=12 ry=9 rz=3` builds a natural arch (the longer of `rx`/`rz` is the span). `/sb brush caves 14 strength=0.2` carves winding tunnels through solid ground (`strength=` is the tunnel width)
- `/sb brush boulder 4 stone,andesite ry=3` sets a lumpy rock into the ground (`strength=` is how rough, `rx= ry= rz=`
  its size). `/sb brush cliff 8 stone,andesite,granite depth=3` stacks those blocks as strata layers on steep faces and
  juts out ledges (`depth=` is the layer height, `strength=` how many ledges)
- `/sb terrain archipelago`, `atoll`, `lake`, `fjord`, `valley` and `swamp` fill their low ground with water up to the height of the ground at the centre. New `coastal` and `swamp` styles give them sand, gravel, mud and moss. `/sb terrain list` shows all of them
- Noise masks make any edit land only in organic patches. `/sb mask fractal:8:0.4` is fractal patches 8 blocks across
  covering 40%, `cell:6:0.5` fills half of the voronoi cells, `voronoi:8:0.15` and `crack:6:0.12` are cell borders and
  cracks, `ygradient:60:90` thins out from y=60 to y=90, and `!` flips any of them. Add `:3d` for patterns that change
  with height (they are the same up a column by default). Combine with blocks, e.g. `/sb mask stone fractal:8:0.4`, and
  add `seed=` to repeat a pattern. `/sb mask off` clears both

### Keys

| Key | Action |
|---|---|
| F7 | open / close the editor |
| N | noclip fly (rebindable, toggle or hold) |
| Ctrl+Z / Ctrl+Y | undo / redo |
| 1-9, 0 | switch tools |
| Enter / Delete | keep / remove the last placement |
| Arrows, Page Up / Down | move the last placement |
| R | turn the last placement |

## Commands

Everything in the editor is also a command. `/sb help` lists them all and every argument has tab completion.

```
/sb sphere <blocks> <radius> [-h] [-a]         /sb terrain <type> [radius=] [height=] [style=]
/sb brush bind <type> [radius] [blocks]         /sb fill <blocks> [radius] [mode=hole|connected|room]
/sb tree <type> [height]                        /sb path add, then /sb path <road|wall|tunnel|river|bridge|line>
/sb set|walls|outline|replace ...               /sb copy, /sb cut, /sb paste [rotate=90] [-a]
/sb move [n] [dir], /sb stack [n] [dir]         /sb hollow, /sb overlay <blocks>, /sb naturalize, /sb smooth
/sb select [-a] (magic select)                  /sb expand|contract|shift <n> [dir|vert|all], /sb size
/sb count <blocks>, /sb distr                   /sb drain|snow|thaw|green [radius], /sb replacenear
/sb text <blocks> <words> [size=] [-f]          /sb arch <blocks> <width> <height>
/sb biome <biome> [radius] [-s]                 /sb template save|export|paste|load <name>
/sb import <file> [size=] [height=]             /sb script <file> [args]
/sb mask <blocks|!blocks|fractal:..|off>          /sb symmetry <x|z|xz|off>
/sb gradient <from> <to> [steps]                /sb nudge, /sb turn, /sb cancel, /sb confirm
/sb undo, /sb redo, /sb history, /sb goto <#id|name>, /sb checkpoint <name>, /sb restore <#id|name>
/sb noclip, /sb settings
```

The golden axe is a selection wand: left-click sets pos1, right-click sets pos2.

## Files

In singleplayer, everything lives in `.minecraft/syrkbuilder/`:

- `models/` for models to import
- `heightmaps/` for greyscale images to import as terrain
- `scripts/` for build scripts (seven examples are added on first run: tower, spiral, forest, maze, house,
  lighthouse, well)
- `templates/` for saved and exported builds; `.minecraft/schematics` and
  `.minecraft/config/worldedit/schematics` are read too
- `history/` for undo history

Settings are saved in `.minecraft/config/syrkbuilder.properties`.

## Scripts

Scripts are JavaScript. They get `origin`, `pos1`, `pos2` (each with `x`, `y`, `z`) and `args`, plus:

```
set(x, y, z, blocks)                   get(x, y, z)            ground(x, z)
fill(x1, y1, z1, x2, y2, z2, blocks)   sphere(x, y, z, r, blocks, hollow)
cylinder(x, y, z, r, h, blocks, hollow) line(x1, y1, z1, x2, y2, z2, blocks, radius)
noise(x, z, scale)                     rand()                  print(...)
```

## Known limits

SyrkBuilder works in singleplayer worlds in creative mode only. Servers and survival are switched off for now.

## Servers (coming soon)

Server support is switched off for now. The mod refuses to send anything to a server and the Paper plugin refuses every
request, so installing both changes nothing. What follows is how it will work once it is released.

Install the Fabric mod on your client and the SyrkBuilder Paper plugin on a Paper 1.21.11 server. Players need
`syrkbuilder.use`. Noclip on servers uses spectator mode and needs `syrkbuilder.noclip`. Scripts run on the
server, so they need `syrkbuilder.script`; only give it to players you trust. Limits (edit size, blocks per
tick, history size, upload size, script time) are in `plugins/SyrkBuilder/config.yml`.
