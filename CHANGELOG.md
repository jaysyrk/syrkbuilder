# Changelog

## 1.1.0

- Selection edits: `move` (takes the selection along), `stack`, `hollow`, `overlay`, `naturalize`
- Magic select: `/sb select` grabs everything connected to the block you aim at (`-a` for a whole build of mixed blocks)
- Selection tools: `expand`, `contract`, `shift` (by direction, `vert` or `all`), `size`, `count <blocks>` and `distr` for a block breakdown
- All of these are in the Selection tab of the F7 editor too
- `cut`, `smooth` (evens out terrain inside the selection), and around where you look: `drain`, `snow`, `thaw`, `green`
- `text` writes words in blocks (pixel font, any size, standing or flat), `arch` builds an arch facing you, `replacenear` swaps blocks around where you look
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
