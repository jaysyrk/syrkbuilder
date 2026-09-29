var r = parseInt(args[0] || '4');
var h = parseInt(args[1] || '20');
var block = args[2] || 'oak_planks';
var x0 = origin.x, y0 = origin.y + 1, z0 = origin.z;

cylinder(x0, y0, z0, 1, h, 'stone_bricks');
for (var y = 0; y < h; y++) {
    var a = y / 8 * Math.PI * 2;
    for (var d = 2; d <= r; d++) {
        for (var s = -0.25; s <= 0.25; s += 0.25) {
            set(Math.round(x0 + Math.cos(a + s) * d), y0 + y, Math.round(z0 + Math.sin(a + s) * d), block);
        }
    }
}
print('Spiral: ' + h + ' steps');
