var depth = parseInt(args[0] || '4');
var stone = args[1] || '70%cobblestone,30%mossy_cobblestone';
var x0 = origin.x, y0 = origin.y, z0 = origin.z;

fill(x0 - 2, y0 - depth, z0 - 2, x0 + 2, y0 + 1, z0 + 2, stone);
fill(x0 - 1, y0 - depth + 1, z0 - 1, x0 + 1, y0 + 1, z0 + 1, 'air');
fill(x0 - 1, y0 - depth + 1, z0 - 1, x0 + 1, y0 - 1, z0 + 1, 'water');
var posts = [[-2, -2], [2, -2], [-2, 2], [2, 2]];
for (var i = 0; i < posts.length; i++) {
    fill(x0 + posts[i][0], y0 + 2, z0 + posts[i][1], x0 + posts[i][0], y0 + 3, z0 + posts[i][1], 'oak_fence');
}
fill(x0 - 2, y0 + 4, z0 - 2, x0 + 2, y0 + 4, z0 + 2, 'spruce_slab');
fill(x0 - 1, y0 + 5, z0 - 1, x0 + 1, y0 + 5, z0 + 1, 'spruce_slab');
set(x0, y0 + 3, z0, 'oak_fence');
set(x0, y0 + 2, z0, 'lantern[hanging=true]');
print('Well, ' + depth + ' deep');
