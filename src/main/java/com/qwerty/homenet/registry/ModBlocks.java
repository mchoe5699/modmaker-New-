package com.qwerty.homenet.registry;

import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.block.ControlBlock;
import com.qwerty.homenet.block.DoorStationBlock;
import com.qwerty.homenet.block.SmartLightBlock;
import com.qwerty.homenet.block.WallpadBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HomeNet.MODID);

    /** 월패드: 세대 내부 벽에 다는 홈네트워크 단말기 */
    public static final RegistryObject<WallpadBlock> WALLPAD = BLOCKS.register("wallpad",
            () -> new WallpadBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(1.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(WallpadBlock.RINGING) ? 7 : 2)));

    /** 인터폰(로비폰/세대현관 도어폰): 링크 안 되면 로비폰, 월패드에 링크되면 세대현관 도어폰 */
    public static final RegistryObject<DoorStationBlock> DOOR_STATION = BLOCKS.register("door_station",
            () -> new DoorStationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(1.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> 1)));

    /** 제어 블록: 월패드에서 켜고 끄면 레드스톤 신호를 내보냄 */
    public static final RegistryObject<ControlBlock> CONTROL_BLOCK = BLOCKS.register("control_block",
            () -> new ControlBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0f)
                    .sound(SoundType.METAL), true));

    /** 스마트 조명: 월패드에서 켜고 끄는 조명 (레드스톤 출력 없음) */
    public static final RegistryObject<SmartLightBlock> SMART_LIGHT = BLOCKS.register("smart_light",
            () -> new SmartLightBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ)
                    .strength(0.6f)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> state.getValue(BlockStateProperties.POWERED) ? 15 : 0)));

    private ModBlocks() {}
}
