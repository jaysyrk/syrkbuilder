var w = parseInt(args[0] || '7');
var d = parseInt(args[1] || '9');
var wall = args[2] || 'oak_planks';
var roof = args[3] || 'spruce';
var h = 4;
var x0 = origin.x - Math.floor(w / 2), z0 = origin.z - Math.floor(d / 2), y0 = origin.y;
var x1 = x0 + w - 1, z1 = z0 + d - 1;

fill(x0, y0, z0, x1, y0, z1, 'cobblestone');
fill(x0, y0 + 1, z0, x1, y0 + h, z1, wall);
fill(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y0 + h, z1 - 1, 'air');
fill(x0 + 1, y0, z0 + 1, x1 - 1, y0, z1 - 1, 'spruce_planks');
var corners = [[x0, z0], [x1, z0], [x0, z1], [x1, z1]];
for (var i = 0; i < corners.length; i++) {
    fill(corners[i][0], y0 + 1, corners[i][1], corners[i][0], y0 + h, corners[i][1], 'stripped_oak_log');
}
for (var z = z0 + 2; z < z1 - 1; z += 3) {
    set(x0, y0 + 2, z, 'glass_pane');
    set(x1, y0 + 2, z, 'glass_pane');
}
var dx = origin.x;
set(dx, y0 + 1, z1, 'oak_door[half=lower,facing=south]');
set(dx, y0 + 2, z1, 'oak_door[half=upper,facing=south]');
set(dx + 1, y0 + 3, z1 + 1, 'lantern[hanging=false]');
fill(x0 - 1, y0, z1 + 1, x1 + 1, y0, z1 + 1, 'air');

var half = Math.ceil((w + 2) / 2);
for (var k = 0; k < half; k++) {
    var y = y0 + h + 1 + k;
    var left = x0 - 1 + k, right = x1 + 1 - k;
    if (left >= right) {
        fill(left, y, z0 - 1, left, y, z1 + 1, roof + '_planks');
        break;
    }
    fill(left, y, z0 - 1, left, y, z1 + 1, roof + '_stairs[facing=east]');
    fill(right, y, z0 - 1, right, y, z1 + 1, roof + '_stairs[facing=west]');
    if (right - left > 1) {
        fill(left + 1, y, z0, right - 1, y, z0, wall);
        fill(left + 1, y, z1, right - 1, y, z1, wall);
    }
}
set(origin.x, y0 + h, origin.z, 'lantern[hanging=true]');
print('House ' + w + 'x' + d + ' - door faces south');
