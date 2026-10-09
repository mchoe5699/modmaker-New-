package com.qwerty.homenet.block;

import com.qwerty.homenet.item.HomeLinkerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 벽에 붙는 얇은 패널형 블록 공통 처리.
 * FACING = 패널 앞면(화면)이 향하는 방향. 뒷면이 벽에 붙는다.
 */
public abstract class WallMountedBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final VoxelShape north, south, east, west;

    /**
     * @param halfWidth 패널 가로 절반 (픽셀)
     * @param y1        패널 아래 (픽셀)
     * @param y2        패널 위 (픽셀)
     * @param depth     두께 (픽셀)
     */
    protected WallMountedBlock(Properties props, double halfWidth, double y1, double y2, double depth) {
        super(props);
        double x1 = 8 - halfWidth, x2 = 8 + halfWidth;
        this.north = Block.box(x1, y1, 16 - depth, x2, y2, 16);
        this.south = Block.box(x1, y1, 0, x2, y2, depth);
        this.east = Block.box(0, y1, x1, depth, y2, x2);
        this.west = Block.box(16 - depth, y1, x1, 16, y2, x2);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> south;
            case EAST -> east;
            case WEST -> west;
            default -> north;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction clicked = ctx.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal() ? clicked : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    protected static boolean holdingLinker(Player player, InteractionHand hand) {
        return player.getItemInHand(hand).getItem() instanceof HomeLinkerItem;
    }
}
