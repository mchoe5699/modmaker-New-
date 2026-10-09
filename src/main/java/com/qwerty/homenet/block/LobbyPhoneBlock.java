package com.qwerty.homenet.block;

import com.qwerty.homenet.blockentity.LobbyPhoneBlockEntity;
import com.qwerty.homenet.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

/**
 * 공동현관 로비폰.
 * 실제 기기 248 x 279 x 50.2 mm 비율 그대로 블록 1칸 안에 맞춤 → 가로 14.2 x 세로 16 x 두께 2.9 픽셀.
 * 문이 열리면 설정한 시간(1~8초) 동안 레드스톤 신호를 낸다.
 */
public class LobbyPhoneBlock extends WallMountedBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** 본체 크기: 세로 2/3칸, 가로는 실제 비율(248:279), 두께 0.1칸. 블록 면 가운데에 붙음 (픽셀) */
    public static final double HEIGHT = 16.0 * 2 / 3;                 // 10.667
    public static final double HALF_WIDTH = HEIGHT * 248 / 279 / 2;   // 4.741
    public static final double Y1 = 8 - HEIGHT / 2;                   // 2.667
    public static final double Y2 = 8 + HEIGHT / 2;                   // 13.333
    public static final double DEPTH = 1.6;

    public LobbyPhoneBlock(Properties props) {
        super(props, HALF_WIDTH, Y1, Y2, DEPTH);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LobbyPhoneBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.LOBBY_PHONE.get(), LobbyPhoneBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (holdingLinker(player, hand)) return InteractionResult.PASS;
        // 출입 카드를 대면 화면을 열지 않고 카드 접촉으로 처리
        if (player.getItemInHand(hand).getItem() instanceof com.qwerty.homenet.item.RfCardItem) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof LobbyPhoneBlockEntity be) {
                com.qwerty.homenet.item.RfCardItem.tap(be, player.getItemInHand(hand));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.qwerty.homenet.client.ClientHooks.openLobbyPhone(pos));
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof LobbyPhoneBlockEntity be) be.wake();
        return InteractionResult.CONSUME;
    }

    /** 문열림 신호 */
    public static void pulse(ServerLevel level, BlockPos pos, int ticks) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LobbyPhoneBlock block)) return;
        level.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, block, Math.max(20, ticks));
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
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LobbyPhoneBlockEntity be) {
            be.onBroken();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
