package com.qwerty.homenet.block;

import com.qwerty.homenet.blockentity.DoorStationBlockEntity;
import com.qwerty.homenet.registry.ModBlockEntities;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 인터폰.
 * - 월패드에 링크되지 않음 → 공동현관 로비폰 (세대 번호를 눌러 호출)
 * - 월패드에 링크됨 → 세대현관 도어폰 (버튼 한 번으로 해당 세대 호출)
 * 월패드에서 "문열기"를 누르면 3초간 레드스톤 신호를 낸다 (옆에 문/피스톤 연결).
 */
public class DoorStationBlock extends WallMountedBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final int OPEN_PULSE_TICKS = 60;

    public DoorStationBlock(Properties props) {
        // 도어카메라: 6 x 9 픽셀, 두께 1
        this(props, 3, 4, 13, 1);
    }

    protected DoorStationBlock(Properties props, double halfWidth, double y1, double y2, double depth) {
        super(props, halfWidth, y1, y2, depth);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DoorStationBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.DOOR_STATION.get(), DoorStationBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (holdingLinker(player, hand)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof DoorStationBlockEntity be && player instanceof ServerPlayer sp) {
            be.openFor(sp);
        }
        return InteractionResult.CONSUME;
    }

    /** 문열림 신호 */
    public static void pulse(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DoorStationBlock block)) return;
        level.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, block, OPEN_PULSE_TICKS);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_ALL);
        }
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
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof DoorStationBlockEntity be) {
            be.onBroken();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
