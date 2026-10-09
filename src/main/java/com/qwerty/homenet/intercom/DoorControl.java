package com.qwerty.homenet.intercom;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * 문 열기/닫기. "open" 상태가 있는 블록이면 모두 대상이 된다
 * (나무·철 문, 다른 모드의 문, 다락문, 울타리 문 등).
 */
public final class DoorControl {
    private DoorControl() {}

    public static boolean isDoor(BlockState state) {
        return state.hasProperty(BlockStateProperties.OPEN);
    }

    /** 2칸 문은 아래 칸 위치로 저장 */
    public static BlockPos normalize(BlockState state, BlockPos pos) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return pos.below();
        }
        return pos.immutable();
    }

    public static boolean isOpen(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return isDoor(s) && s.getValue(BlockStateProperties.OPEN);
    }

    public static void setOpen(ServerLevel level, BlockPos pos, boolean open) {
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!isDoor(state) || state.getValue(BlockStateProperties.OPEN) == open) return;
        if (state.getBlock() instanceof DoorBlock door) {
            // 위아래 두 칸과 소리를 문 블록이 직접 처리
            door.setOpen(null, level, state, pos, open);
            return;
        }
        level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
        var sound = state.getBlock() instanceof FenceGateBlock
                ? (open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE)
                : state.getBlock() instanceof TrapDoorBlock
                ? (open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE)
                : (open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE);
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.gameEvent(null, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
    }
}
