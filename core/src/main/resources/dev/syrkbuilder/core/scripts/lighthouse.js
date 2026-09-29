var r = parseInt(args[0] || '4');
var h = parseInt(args[1] || '24');
var a = args[2] || 'white_terracotta';
var b = args[3] || 'red_terracotta';
var x0 = origin.x, y0 = origin.y + 1, z0 = origin.z;

cylinder(x0, y0 - 1, z0, r + 2, 1, 'stone_bricks');
for (var y = 0; y < h; y++) {
    var band = Math.floor(y / 4) % 2 == 0 ? a : b;
    var rr = r - Math.floor(y / (h / 2));
    cylinder(x0, y0 + y, z0, Math.max(2, rr), 1, band, true);
}
var top = y0 + h;
cylinder(x0, top, z0, r + 1, 1, 'polished_andesite');
for (var i = 0; i < 48; i++) {
    var t = i / 48 * Math.PI * 2;
    set(Math.round(x0 + Math.cos(t) * (r + 1)), top + 1, Math.round(z0 + Math.sin(t) * (r + 1)), 'iron_bars');
}
cylinder(x0, top + 1, z0, 2, 3, 'glass', true);
set(x0, top + 1, z0, 'sea_lantern');
set(x0, top + 2, z0, 'sea_lantern');
cylinder(x0, top + 4, z0, 3, 1, b);
cylinder(x0, top + 5, z0, 2, 1, b);
set(x0, top + 6, z0, b);
print('Lighthouse: radius ' + r + ', height ' + h);
