import re
p = r'C:\Users\吴广陵\Desktop\EnderCore\mod\src\main\java\com\endercore\PlayerController.java'
with open(p, 'r', encoding='utf-8') as f:
    src = f.read()

# Replace the entire bridge block between "if (bridgeMode) {" and its matching closing brace
new_block = '''        // ---- 连续搭桥：直接构造脚下方块侧面的点击，不移动 ----
        if (bridgeMode) {
            BlockPos below = mc.player.getBlockPos().down();
            float yaw = mc.player.getYaw();
            double rad = Math.toRadians(yaw);
            int dx = (int)Math.round(-Math.sin(rad));
            int dz = (int)Math.round(Math.cos(rad));
            BlockPos placeTarget = below.add(dx, 0, dz);
            if (mc.world.getBlockState(placeTarget).isAir()) {
                Direction side = Direction.getFacing(dx, 0, dz);
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(below).add(side.getOffsetX()*0.5, side.getOffsetY()*0.5, side.getOffsetZ()*0.5), side, below, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }'''

# Find the bridge block: start at "if (bridgeMode) {" and find matching closing brace
idx = src.find('if (bridgeMode) {')
if idx == -1:
    print('NOT FOUND')
    exit(1)
# Walk back to find the comment line start
line_start = src.rfind('\n', 0, idx) + 1
# Find matching closing brace
depth = 0
i = src.find('{', idx)
while i < len(src):
    if src[i] == '{':
        depth += 1
    elif src[i] == '}':
        depth -= 1
        if depth == 0:
            break
    i += 1
end = i + 1
# Find end of line
eol = src.find('\n', end)
if eol == -1:
    eol = len(src)
print(f'Replacing chars {line_start} to {eol}')
src = src[:line_start] + new_block + src[eol:]
with open(p, 'w', encoding='utf-8') as f:
    f.write(src)
print('done')
