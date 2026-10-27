package org.papiricoh.nationsandwar.war;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A town's heart. Protection keeps it whole outside sieges; once its home chunk has fallen, each time an enemy breaks it
 * counts one hit ({@link WarService#hitCore}) and the block stays until the last.
 */
public final class TownCoreBlock extends Block {
    public static final MapCodec<TownCoreBlock> CODEC = simpleCodec(TownCoreBlock::new);
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    public TownCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
