package com.qwerty.homenet.block;

import com.qwerty.homenet.blockentity.DeviceBlockEntity;
import com.qwerty.homenet.item.HomeLinkerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 제어 블록. 월패드에 링크해서 켜고 끌 수 있는 기기.
 * 켜지면 사방으로 레드스톤 신호 15를 낸다 (조명, 피스톤, 문 등 연결).
 * 맨손 우클릭 → 이름 / 기기 종류 설정.
 */
public class ControlBlock extends BaseEntityBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private final boolean redstoneOutput;

    public ControlBlock(Properties props, boolean redstoneOutput) {
        super(props);
        this.redstoneOutput = redstoneOutput;
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DeviceBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof HomeLinkerItem) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof DeviceBlockEntity be && player instanceof ServerPlayer sp) {
            be.openConfig(sp);
        }
        return InteractionResult.CONSUME;
    }

    /** 기본 기기 종류 (스마트 조명은 LIGHT) */
    public DeviceType defaultType() {
        return DeviceType.OTHER;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isSignalSource(BlockState state) {
        return redstoneOutput;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return redstoneOutput && state.getValue(POWERED) ? 15 : 0;
    }
}
