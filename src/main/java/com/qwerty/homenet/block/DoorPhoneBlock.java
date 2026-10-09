package com.qwerty.homenet.block;

import com.qwerty.homenet.blockentity.DoorStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 도어폰: 세대 현관의 버튼형 초인종. 누르면 지정된 세대의 수신기를 모두 호출한다.
 * (화면이 있는 도어카메라와 같은 블록엔티티를 쓴다)
 */
public class DoorPhoneBlock extends DoorStationBlock {
    public DoorPhoneBlock(Properties props) {
        super(props, 2.5, 4, 12, 1);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (holdingLinker(player, hand)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof DoorStationBlockEntity be) {
            if (be.isInCall()) be.hangUpFromCaller();
            else be.call();
        }
        return InteractionResult.CONSUME;
    }
}
