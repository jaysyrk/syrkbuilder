# SyrkBuilder

A building editor for Minecraft 1.21.11 & 26.2 (Fabric). Shapes, terrain, sculpting brushes, trees, paths, fills,
biomes, colour gradients, text, model, heightmap and schematic import, scripting and noclip, all in one in-game
editor, with a live preview and an undo history you never lose.

Works in singleplayer on its own. On a server, install the SyrkBuilder plugin (coming soon) on the server as well.

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
- **Terrain in one click.** Mountains, hills, mesas, volcanoes, craters, canyons, dunes and islands, with
  erosion and styles. Or turn any greyscale image into terrain, and paint biomes on top.
- **Select smarter.** Magic select grabs a whole build by clicking it. Move it, stack it, hollow it, smooth it.
- **Works with your other tools.** Opens and saves WorldEdit, FAWE and Axiom `.schem` files and Litematica
  `.litematic` files, and picks up your WorldEdit and Litematica schematic folders automatically.

## The editor (F7)

Hold right-click over the world to look around, move with WASD, space and shift, and left-click to use the
current tool. Middle-click copies the block you aim at into the tool's Blocks field.

| Tool | What it does |
|---|---|
| Shapes | sphere, ellipsoid, dome, cylinder, cone, pyramid, circle, disc, torus, helix, text, arch |
| Terrain | 8 generators with radius, height, erosion, roughness, peaks, style and seed; snow, thaw, green, drain; biome painting |
| Brushes | 26 brushes in three groups: blocks (including a clipboard stamp), sculpting and terrain |
| Fill | hole (fills to the brim), connected (paint bucket), room (enclosed 3D) |
| Trees | oak, birch, spruce, pine, jungle, dark oak, acacia, cherry, willow, palm, dead, swamp, and a forest brush |
| Paths | drag or click points, then build a road, wall, tunnel, river, bridge or line |
| Selection | set, walls, outline, replace, line, move, stack, hollow, overlay, naturalize, smooth, count, magic select, expand / contract / shift |
| Clipboard | copy, cut, paste, rotate, mirror, templates, export to `.schem` / `.litematic` |
| Import | OBJ (with MTL textures), glTF / GLB, MagicaVoxel `.vox`, and `.png` / `.jpg` heightmaps as terrain |
| Scripts | run JavaScript build scripts |
| History | the branching timeline; click to jump, shift+click to restore your selection |
| Settings | rebind keys, noclip toggle or hold, fly speed, look sensitivity, quiet chat, particles |

Every Blocks field has a colour picker: choose a colour to see the closest blocks, or build a gradient between
two colours. Blocks can be mixes (`60%stone,40%andesite`) or gradients (`grad:`, `gradx:`, `gradz:`, `gradr:`).

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
/sb mask <blocks|!blocks|off>                   /sb symmetry <x|z|xz|off>
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

## Servers (coming soon)

Install the Fabric mod on your client and the SyrkBuilder Paper plugin on a Paper 1.21.11 server. Players need
`syrkbuilder.use`. Noclip on servers uses spectator mode and needs `syrkbuilder.noclip`. Scripts run on the
server, so they need `syrkbuilder.script`; only give it to players you trust. Limits (edit size, blocks per
tick, history size, upload size, script time) are in `plugins/SyrkBuilder/config.yml`.
