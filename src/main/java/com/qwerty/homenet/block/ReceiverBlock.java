package com.qwerty.homenet.block;

import com.qwerty.homenet.blockentity.ReceiverBlockEntity;
import com.qwerty.homenet.data.DeviceRegistry;
import com.qwerty.homenet.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 수신기 블록: 비디오폰 / 인터폰 / 경비실기.
 * (월패드는 WallpadBlock)
 */
public class ReceiverBlock extends WallMountedBlock {
    private final DeviceRegistry.Kind kind;
    /** 경비실기는 책상 위에 놓는 콘솔 */
    private final boolean desk;
    private static final VoxelShape DESK_SHAPE = Block.box(1, 0, 2, 15, 11, 14);

    public ReceiverBlock(Properties props, DeviceRegistry.Kind kind, double halfWidth, double y1, double y2, double depth, boolean desk) {
        super(props, halfWidth, y1, y2, depth);
        this.kind = kind;
        this.desk = desk;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WallpadBlock.RINGING, false));
    }

    public DeviceRegistry.Kind kind() {
        return kind;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WallpadBlock.RINGING);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return desk ? DESK_SHAPE : super.getShape(state, level, pos, ctx);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (desk) return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        return super.getStateForPlacement(ctx);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReceiverBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.RECEIVER.get(), ReceiverBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (holdingLinker(player, hand)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof ReceiverBlockEntity be && player instanceof ServerPlayer sp) be.openFor(sp);
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ReceiverBlockEntity be) be.onBroken();
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
