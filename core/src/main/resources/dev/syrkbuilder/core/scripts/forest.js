var radius = parseInt(args[0] || '24');
var count = parseInt(args[1] || '30');
var log = args[2] || 'oak_log';
var leaves = (args[3] || 'oak_leaves') + '[persistent=true]';
var planted = 0;

for (var tries = 0; tries < count * 5 && planted < count; tries++) {
    var a = rand() * Math.PI * 2, d = Math.sqrt(rand()) * radius;
    var x = Math.round(origin.x + Math.cos(a) * d), z = Math.round(origin.z + Math.sin(a) * d);
    var g = ground(x, z);
    var top = get(x, g, z);
    if (top.indexOf('grass') < 0 && top.indexOf('dirt') < 0) continue;
    var h = 4 + Math.floor(rand() * 3);
    for (var y = 1; y <= h; y++) set(x, g + y, z, log);
    sphere(x, g + h, z, 2 + Math.floor(rand() * 2), leaves);
    for (var y = 1; y <= h; y++) set(x, g + y, z, log);
    planted++;
}
print('Planted ' + planted + ' trees');
