var r = parseInt(args[0] || '5');
var h = parseInt(args[1] || '24');
var block = args[2] || '80%stone_bricks,15%cracked_stone_bricks,5%mossy_stone_bricks';
var x0 = origin.x, y0 = origin.y + 1, z0 = origin.z;

cylinder(x0, y0, z0, r, h, block, true);
cylinder(x0, y0, z0, r - 1, 1, 'spruce_planks');
for (var y = 3; y < h - 2; y += 4) {
    var a = y * 0.6;
    var wx = Math.round(x0 + Math.cos(a) * r), wz = Math.round(z0 + Math.sin(a) * r);
    set(wx, y0 + y, wz, 'air');
    set(wx, y0 + y + 1, wz, 'air');
}
for (var i = 0; i < 64; i++) {
    var t = i / 64 * Math.PI * 2;
    if (i % 4 < 2) {
        set(Math.round(x0 + Math.cos(t) * r), y0 + h, Math.round(z0 + Math.sin(t) * r), 'stone_bricks');
    }
}
print('Tower: radius ' + r + ', height ' + h);
