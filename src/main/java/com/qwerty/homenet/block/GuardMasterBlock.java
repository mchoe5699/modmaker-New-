package com.qwerty.homenet.block;

import com.qwerty.homenet.blockentity.GuardMasterBlockEntity;
import com.qwerty.homenet.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * KOCOM ASTRO KGP-70K 경비실기 (책상용).
 * OFFHOOK = 송수화기를 든 상태(통화 중) → 모델에서 수화기가 사라짐.
 * POWERED = 문열림(DC 접점) 키를 눌렀을 때 몇 초간 레드스톤 신호.
 */
public class GuardMasterBlock extends WallMountedBlock {
    public static final BooleanProperty OFFHOOK = BooleanProperty.create("offhook");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** 북쪽(화면이 -z) 기준 상자들: 받침대, 기울어진 본체 아래 / 위 */
    private static final double[][] BOXES = {{2, 0, 3.4, 14, 0.7, 14.6}, {1, 0.7, 2.8, 15, 6.5, 7.4}, {1, 6.5, 5.0, 15, 13, 10.5}};
    private static final VoxelShape NORTH = shape(Direction.NORTH), SOUTH = shape(Direction.SOUTH),
            EAST = shape(Direction.EAST), WEST = shape(Direction.WEST);

    private static VoxelShape shape(Direction facing) {
        VoxelShape out = Shapes.empty();
        for (double[] b : BOXES) {
            double x1 = b[0], y1 = b[1], z1 = b[2], x2 = b[3], y2 = b[4], z2 = b[5];
            VoxelShape v = switch (facing) {
                case SOUTH -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1);
                case EAST -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2);
                case WEST -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1);
                default -> Block.box(x1, y1, z1, x2, y2, z2);
            };
            out = Shapes.or(out, v);
        }
        return out;
    }

    public GuardMasterBlock(Properties props) {
        super(props, 7, 0, 10, 12);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WallpadBlock.RINGING, false)
                .setValue(OFFHOOK, false).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WallpadBlock.RINGING, OFFHOOK, POWERED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NORTH;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // 책상 위: 화면이 플레이어 쪽을 보게
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GuardMasterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.GUARD_MASTER.get(), GuardMasterBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (holdingLinker(player, hand)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof GuardMasterBlockEntity be && player instanceof ServerPlayer sp) be.openFor(sp);
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) ? 15 : 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_ALL);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GuardMasterBlockEntity be) be.onBroken();
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
