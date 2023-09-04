package com.huangli.utils;

import com.mojang.brigadier.Command;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.lang.Math.*;

public class Creator {

    static int Radius = 20;
    static int Height = 6;

    public static int islandSettings(ServerCommandSource source, int radius, int height) {
        Radius = radius;
        Height = height;

        source.sendMessage(
                Text.literal("The island radius will be %d and the height will be %d"
                .formatted(radius, height))
                        .setStyle(Style.EMPTY.withColor(Formatting.GREEN)));

        source.sendMessage(
                Text.literal("Use /island confirm to create this island")
                        .setStyle(Style.EMPTY.withColor(Formatting.GOLD)));

        return Command.SINGLE_SUCCESS;
    }


    public static void genIsland(PlayerEntity player, World world, boolean async) {

        // const val
        final float A = (float) Height / (Radius * Radius);
        final int h = (int) (A * Radius * Radius);  // max(y) - y value of the highest block of island
        final int noiseMapSize = (int) (Radius * 1.7F);
        final int area = 3;
        final Vec3i originPos = new Vec3i(
                (int) player.getPos().x,
                (int) player.getPos().y - 2 - h,
                (int) player.getPos().z
        );

        calcIsland(area, noiseMapSize, A, h, originPos, world, async);
        
    }

    private static void calcIsland(int area, int noiseMapSize, float A, int h, Vec3i originPos, World world, boolean async) {
        final float[][] noiseMap = new float[noiseMapSize][noiseMapSize];
        final float[][] noiseMap2 = new float[noiseMapSize][noiseMapSize];

        final List<IslandBlockData> blockDataList = Collections.synchronizedList(new ArrayList<>(2048));

        long start = System.currentTimeMillis();

        if (async) {
            final int piece = noiseMapSize / 8;
            final List<Thread> buildThreads = new ArrayList<>(8);

            for (int c = 0; c < 8; c++) {
                final int count = c;

                Thread buildThread = new Thread(() -> {
                    final int maxVal = min(noiseMapSize, piece * (count + 1));

                    for (int i = piece * count; i < maxVal; i++) {
                        for (int j = piece * count; j < maxVal; j++) {
                            float mapValue1 = (float) (random() - 0.5F) * ((float) pow(Height, 0.5F) * 1.5F + 3);
                            float mapValue2 = (float) (random() - 0.5F) * ((float) pow(Height, 0.7F) * 0.6F + 3);

                            synchronized ("Map generator") {
                                noiseMap[i][j] = mapValue1;
                                noiseMap2[i][j] = mapValue2;
                            }

                        }
                    }
                });

                buildThread.start();

                buildThreads.add(buildThread);
            }

            for (Thread thread : buildThreads) {
                try {
                    thread.join();
                }
                catch (InterruptedException e) {
                    return;
                }
            }

            System.out.println("异步噪声生成耗时：" + (System.currentTimeMillis() - start) + "ms");
        }
        else {
            for (int i = 0; i < noiseMapSize; i++) {
                for (int j = 0; j < noiseMapSize; j++) {
                    noiseMap[i][j] = (float) (random() - 0.5F) * ((float) pow(Height, 0.5F) * 1.5F + 3);
                    noiseMap2[i][j] = (float) (random() - 0.5F) * ((float) pow(Height, 0.7F) * 0.6F + 3);
                }
            }

            System.out.println("同步噪声生成耗时：" + (System.currentTimeMillis() - start) + "ms");
        }

        start = System.currentTimeMillis();
        for (int i = 0; i < pow(noiseMapSize, 1.25); i++) {
            int x = (int) (random() * (noiseMapSize - 1 - area * 2)) + area;
            int z = (int) (random() * (noiseMapSize - 1 - area * 2)) + area;
            float r = (float) sqrt((x * x + z * z) / 2F);
            if (random() > r / noiseMapSize) continue;
            float w = (noiseMapSize - r) / noiseMapSize;
            noiseMap[x][z] += random() * Height * area * area * w * 0.5F;
            for (int p = 0; p <= 1; p++)
                for (int q = 0; q <= 1; q++)
                    noiseMap[x + p][z + q] += random() * Height * area * area * w * 0.25F;
        }
        for (int i = 0; i < noiseMapSize; i++)
            for (int j = 0; j < noiseMapSize; j++) {
                float v1 = 0, v2 = 0;
                int s = 0;
                for (int p = -area; p <= area; p++) {
                    for (int q = -area; q <= area; q++) {
                        if (i + p >= 0 && i + p < noiseMapSize && j + q >= 0 && j + q < noiseMapSize) {
                            v1 += noiseMap[i + p][j + q];
                            v2 += noiseMap2[i + p][j + q];
                            s++;
                        }
                    }
                }
                noiseMap[i][j] = v1 / s;
                noiseMap2[i][j] = v2 / s;
            }
        for (int i = 0; i < noiseMapSize; i++) {
            for (int j = 0; j < noiseMapSize; j++) {
                noiseMap[i][j] += random() * 2;
            }
        }

        System.out.println("同步噪声运算耗时：" + (System.currentTimeMillis() - start) + "ms");
        start = System.currentTimeMillis();

        if (async) {
            final int piece = (2 * Radius) / 8;
            final List<Thread> buildThreads = new ArrayList<>(8);

            for (int c = 0; c < 8; c++) {
                final int count = c;
                Thread buildThread = new Thread(() -> {
                    final int maxVal = min(Radius, (-Radius + piece * (count + 1)));

                    for (int x = -Radius + (count * piece); x < maxVal; x++) {
                        int z0 = (int) sqrt(Radius * Radius - x * x);
                        for (int z = -z0; z <= z0; z++) {

                            int nx = (int) ((float) (x + Radius) / (2 * Radius + 1) * noiseMapSize);
                            int nz = (int) ((float) (z + z0) / (2 * z0 + 1) * noiseMapSize);

                            int y0 = (int) (A * (x * x + z * z) - noiseMap[nx][nz]);
                            int y1 = (int) noiseMap2[nx][nz] + h;
                            float r = (float)sqrt(x * x + z * z);
                            float EdgeSmoothArea = 0.1F;
                            if (r > Radius * (1 - EdgeSmoothArea))
                            {
                                float R = Radius * EdgeSmoothArea;
                                r = r - Radius * (1 - EdgeSmoothArea);
                                y1 -= -sqrt(R * R - r * r) + R;
                            }

                            for (int y = y0; y <= y1; y++) {
                                BlockPos blockPos = new BlockPos(originPos.add(x, y, z));
                                BlockState blockState;
                                if (y == y1) {
                                    blockState = Blocks.GRASS_BLOCK.getDefaultState();
                                }
                                else if (y >= y1 - 4 - random() * 4) {
                                    blockState = Blocks.DIRT.getDefaultState();
                                }
                                else {
                                    blockState = Blocks.STONE.getDefaultState();
                                }

                                blockDataList.add(new IslandBlockData(blockPos, blockState));
                            }
                        }
                    }
                });

                buildThread.start();

                buildThreads.add(buildThread);
            }

            for (Thread thread : buildThreads) {
                try {
                    thread.join();
                }
                catch (InterruptedException e) {
                    return;
                }
            }

            System.out.println("异步岛屿生成耗时：" + (System.currentTimeMillis() - start) + "ms");
            start = System.currentTimeMillis();

            for (IslandBlockData blockData : blockDataList) {
                world.setBlockState(blockData.getBlockPos(), blockData.getBlockState() , 2);
                world.updateNeighbors(blockData.getBlockPos(), blockData.getBlock());
            }

            System.out.println("异步岛屿建造耗时：" + (System.currentTimeMillis() - start) + "ms");
        }

        else {
            for (int x = -Radius; x < Radius; x++) {
                int z0 = (int) sqrt(Radius * Radius - x * x);
                for (int z = -z0; z <= z0; z++) {

                    int nx = (int) ((float) (x + Radius) / (2 * Radius + 1) * noiseMapSize);
                    int nz = (int) ((float) (z + z0) / (2 * z0 + 1) * noiseMapSize);

                    int y0 = (int) (A * (x * x + z * z) - noiseMap[nx][nz]);
                    int y1 = (int) noiseMap2[nx][nz] + h;
                    float r = (float)sqrt(x * x + z * z);
                    float EdgeSmoothArea = 0.1F;
                    if (r > Radius * (1 - EdgeSmoothArea))
                    {
                        float R = Radius * EdgeSmoothArea;
                        r = r - Radius * (1 - EdgeSmoothArea);
                        y1 -= -sqrt(R * R - r * r) + R;
                    }

                    for (int y = y0; y <= y1; y++) {
                        BlockPos blockPos = new BlockPos(originPos.add(x, y, z));
                        BlockState blockState;
                        if (y == y1) {
                            blockState = Blocks.GRASS_BLOCK.getDefaultState();
                        }
                        else if (y >= y1 - 4 - random() * 4) {
                            blockState = Blocks.DIRT.getDefaultState();
                        }
                        else {
                            blockState = Blocks.STONE.getDefaultState();
                        }
                        world.setBlockState(blockPos, blockState , 2);
                        world.updateNeighbors(blockPos, blockState.getBlock());
                    }
                }
            }

            System.out.println("同步岛屿生成 & 建造耗时：" + (System.currentTimeMillis() - start) + "ms");
        }
    }
}

class IslandBlockData {
    private final BlockPos blockPos;
    private final BlockState blockState;

    public IslandBlockData(BlockPos blockPos, BlockState blockState) {
        this.blockPos = blockPos;
        this.blockState = blockState;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public BlockState getBlockState() {
        return blockState;
    }

    public Block getBlock() {
        return blockState.getBlock();
    }
}
