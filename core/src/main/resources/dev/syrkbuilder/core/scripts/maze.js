if (!pos1 || !pos2) { print('Select an area first'); throw 'no selection'; }
var wall = (args[0] || 'oak_leaves') + (args[0] ? '' : '[persistent=true]');
var h = parseInt(args[1] || '3');
var minX = Math.min(pos1.x, pos2.x), minZ = Math.min(pos1.z, pos2.z);
var y0 = Math.min(pos1.y, pos2.y);
var w = Math.floor((Math.abs(pos1.x - pos2.x) + 1 - 1) / 2), d = Math.floor((Math.abs(pos1.z - pos2.z) + 1 - 1) / 2);
if (w < 2 || d < 2) { print('Selection too small'); throw 'small'; }

var open = {};
function key(x, z) { return x + ',' + z; }
var stack = [[0, 0]], seen = {};
seen[key(0, 0)] = true;
open[key(1, 1)] = true;
while (stack.length) {
    var c = stack[stack.length - 1];
    var dirs = [[1, 0], [-1, 0], [0, 1], [0, -1]].filter(function (v) {
        var nx = c[0] + v[0], nz = c[1] + v[1];
        return nx >= 0 && nz >= 0 && nx < w && nz < d && !seen[key(nx, nz)];
    });
    if (!dirs.length) { stack.pop(); continue; }
    var v = dirs[Math.floor(rand() * dirs.length)];
    var nx = c[0] + v[0], nz = c[1] + v[1];
    seen[key(nx, nz)] = true;
    open[key(c[0] * 2 + 1 + v[0], c[1] * 2 + 1 + v[1])] = true;
    open[key(nx * 2 + 1, nz * 2 + 1)] = true;
    stack.push([nx, nz]);
}
open[key(1, 0)] = true;
open[key(w * 2 - 1, d * 2)] = true;
for (var x = 0; x <= w * 2; x++) {
    for (var z = 0; z <= d * 2; z++) {
        for (var y = 1; y <= h; y++) set(minX + x, y0 + y, minZ + z, open[key(x, z)] ? 'air' : wall);
        set(minX + x, y0, minZ + z, 'grass_block');
    }
}
print('Maze ' + w + 'x' + d + ' cells');
