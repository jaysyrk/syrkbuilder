# Changelog

## 1.3.1

- The editor aims where your mouse pointer is instead of the crosshair: clicks, drags, brushes and the aim buttons use the block under the pointer, which is outlined with sparks. While you hold right-click to look around, it still aims at the crosshair
- Settings → Aim at switches between the pointer and the crosshair, and a one-time hint explains the pointer the first time you open the editor

## 1.3.0

- Seven new terrain generators: `buttes`, `valley` (with a winding river), `fjord`, `lake`, `atoll`, `archipelago` and `swamp`. The wet ones fill their low ground with water
- Two new terrain styles: `coastal` (sand, gravel, clay, grass) and `swamp` (mud, moss, podzol)
- Terragen brush grows terrain from noise at the height you aim: `/sb brush terragen 20 noise=simplex|fractal|billowy|ridged strength= scale= octaves=`, `-n` for extra fine detail
- Terragen presets set the noise, size and surface blocks in one go: `preset=alpine|rolling|dunes|mesa|islands|craggy`, or pick surface blocks with `style=`
- Lake brush: digs a bowl and floods it with water up to the height you aim at
- Erode brush: water and slope erosion that carves gullies and softens peaks
- Boulder brush: lumpy rocks set into the ground. Cliff brush: strata layers and ledges on steep faces
- Rock arch brush: a natural stone arch standing on the ground. Caves brush: winding tunnels carved through solid ground
- Noise masks: `/sb mask fractal:8:0.4`, `cell`, `voronoi`, `crack`, `ygradient:60:90`, `!` to invert, `:3d` for patterns that change with height, combinable with block masks. They apply to every edit
- Terrain tab: save your favourite settings as named presets and load them again with one click (kept in `syrkbuilder/terrain-presets.txt`)
- The valley's outline is rounded instead of rectangular
- Terrain brushes now see mountains taller than twice their radius instead of treating the cut-off height as the ground
- SyrkBuilder only works in creative mode now: in survival the editor, brushes, wand, noclip and fly speed all stay off, and the engine refuses requests from players who aren't in creative
- Servers are switched off for now: the mod only works in singleplayer worlds (it no longer registers its network channel), and the Paper plugin refuses every request. Server support will come back in a later release
- Brushes take `seed=` to repeat the exact same random result, e.g. `/sb brush roughen 5 seed=7`
- Opening the editor with a key no longer puts the cursor in a text box first, which could swallow F7 and the tool shortcuts

## 1.2.0

- Gradients can run any direction: `grad(down):`, east, west, north, south, `grad(in):` towards the centre, `grad(look):` the way you're facing, or any direction like `grad(1/0/1):`
- Gradient brush strokes keep the gradient of their first dab, so overlapping dabs no longer lay new bands over each other
- Gradient ranges: `grad(up,60..90):stone,snow_block` pins the first and last block to the world, however far a stroke goes
- Painting a wall with a gradient no longer speckles the floor at its foot
- Colour picker: the Gradient tab has all nine directions, plus Start at aim and End at aim to set a range by aiming at blocks
- Colour picker: Use palette puts every block shown into the field as an even mix
- Brushes can be stretched: `rx=`, `ry=` and `rz=` give them their own size on each axis (Stretch per axis in the editor)

## 1.1.1

- Fixed one damaged undo file (for example after a crash mid-save) wiping the whole history for that world. Now only that entry is skipped, and if it was the one you were on, you stay on the closest one that survived instead of jumping back to the start
- History files are now written to a temporary file, flushed to disk and swapped in, so a crash can't leave half-written ones
- Fixed `/sb restore` changing which branch the next redo follows
- Scripts that recurse forever or use too much memory are now stopped with an error instead of crashing the game
- Servers: scripts now need the `syrkbuilder.script` permission
- Servers: a player can only have a few unfinished uploads at once, so they can't fill the server's memory

## 1.1.0

- Selection edits: `move` (takes the selection along), `stack`, `hollow`, `overlay`, `naturalize`
- Magic select: `/sb select` grabs everything connected to the block you aim at (`-a` for a whole build of mixed blocks)
- Selection tools: `expand`, `contract`, `shift` (by direction, `vert` or `all`), `size`, `count <blocks>` and `distr` for a block breakdown
- All of these are in the Selection tab of the F7 editor too
- `cut`, `smooth` (evens out terrain inside the selection), and around where you look: `drain`, `snow`, `thaw`, `green`
- `text` writes words in blocks (pixel font, any size, standing or flat), `arch` builds an arch facing you, `replacenear` swaps blocks around where you look
- Stamp brush: paints your clipboard wherever you click or drag, randomly turned
- Three new example scripts: `house`, `lighthouse` and `well`
- Biome painting: `/sb biome <biome> [radius]` around where you look, or `-s` for the selection (works on servers too)
- Heightmap import: put a greyscale .png/.jpg in `.minecraft/syrkbuilder/heightmaps` and import it as terrain (`/sb import <file> size= height=`, or the Import tab)
- New Settings tab in the F7 editor (also `/sb settings`): rebind the editor and noclip keys, noclip toggle or hold mode, fly speed, look sensitivity, preview on by default, golden axe wand, quiet chat and particle toggles. Saved in `config/syrkbuilder.properties`
- Dragging to place no longer fills chat: the first click reports, the rest show in the status bar. Errors still go to chat
- Fixed editor shortcuts (undo/redo, tool numbers, preview keys) not working after opening the editor, because a text box grabbed focus
- Fixed Ctrl+Z / Ctrl+Y sometimes not registering Ctrl
- Fixed the first command after joining a world being rejected with "history is still loading"
- Fixed the Scripts tab icon being invisible and status bar text overlapping
- Fixed the aimed position occasionally shifting after a command read it

## 1.0.0

First public release.

- F7 editor with tools for shapes, terrain, brushes, fill, trees, paths, selection, clipboard, import, scripts and history
- Click to place, hold and drag to keep placing; every drag is one undo step
- Live preview: move, turn, keep or cancel the last thing you placed
- Branching undo history that survives restarts, with a visual timeline, checkpoints and per-selection restore
- 8 terrain generators with erosion and styles
- 25 brushes, including sculpt, blob, carve, roughen, inflate/deflate, splatter, spikes, crater, terrace and forest
- 12 procedural tree types
- Paths through placed points: road, wall, tunnel, river, bridge, line
- Bucket fill: hole, connected and room modes
- Colour picker, block gradients and eyedropper
- Masks and symmetry
- Import OBJ (with textures), glTF/GLB and MagicaVoxel models
- Read and write WorldEdit/FAWE/Axiom .schem and Litematica .litematic files
- JavaScript build scripts
- Noclip flying
- Works in singleplayer without a server plugin; optional Paper plugin for servers
