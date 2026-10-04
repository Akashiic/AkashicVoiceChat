package dev.akashic.voice.voice.client.acoustics;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/** Cheap listener-side geometry sampler. Call only from the Minecraft client thread. */
public final class AcousticEnvironmentSampler {

    private static final int[][] DIRECTIONS = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, 1, 0 } };
    private static final int MAX_DISTANCE = 8;

    private AcousticEnvironmentSampler() {}

    public static AcousticProfile sample(EntityPlayer player) {
        if (player == null || player.worldObj == null) return AcousticProfile.OUTDOOR;
        World world = player.worldObj;
        int x = MathHelper.floor_double(player.posX);
        int y = MathHelper.floor_double(player.posY + player.getEyeHeight());
        int z = MathHelper.floor_double(player.posZ);
        Block eyeBlock = world.blockExists(x, y, z) ? world.getBlock(x, y, z) : Blocks.air;
        boolean underwater = eyeBlock.getMaterial() == net.minecraft.block.material.Material.water;
        boolean skyVisible = world.canBlockSeeTheSky(x, y, z);
        int surfaces = 0;
        int distanceSum = 0;
        for (int[] direction : DIRECTIONS) {
            int hit = firstOpaqueDistance(world, x, y, z, direction[0], direction[1], direction[2]);
            if (hit > 0) {
                surfaces++;
                distanceSum += hit;
            }
        }
        return AcousticClassifier.classify(underwater, skyVisible, surfaces, distanceSum);
    }

    private static int firstOpaqueDistance(World world, int x, int y, int z, int dx, int dy, int dz) {
        for (int distance = 1; distance <= MAX_DISTANCE; distance++) {
            int sx = x + dx * distance;
            int sy = y + dy * distance;
            int sz = z + dz * distance;
            if (!world.blockExists(sx, sy, sz)) return 0;
            Block block = world.getBlock(sx, sy, sz);
            if (block != null && block != Blocks.air && block.isOpaqueCube()) return distance;
        }
        return 0;
    }
}
