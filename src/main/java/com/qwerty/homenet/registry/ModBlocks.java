package com.qwerty.homenet.registry;

import com.qwerty.homenet.HomeNet;
import com.qwerty.homenet.block.ControlBlock;
import com.qwerty.homenet.block.DoorPhoneBlock;
import com.qwerty.homenet.block.ReceiverBlock;
import com.qwerty.homenet.data.DeviceRegistry;
import com.qwerty.homenet.block.DoorStationBlock;
import com.qwerty.homenet.block.LobbyPhoneBlock;
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

    /** 도어카메라 (실버 세로형): 4 x 5.88 픽셀 */
    public static final RegistryObject<DoorStationBlock> DOOR_CAMERA_SILVER = BLOCKS.register("door_camera_silver",
            () -> new DoorStationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0f)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(state -> 1), 2.0, 5.059, 10.941, 1.1));

    /** 도어카메라 (화이트 정사각형): 4.6 x 4.92 픽셀 */
    public static final RegistryObject<DoorStationBlock> DOOR_CAMERA_SQUARE = BLOCKS.register("door_camera_square",
            () -> new DoorStationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(1.0f)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(state -> 1), 2.3, 5.539, 10.461, 0.75));

    /** 공동현관 로비폰 (실제 기기 비율, 블록 1칸 안) */
    public static final RegistryObject<LobbyPhoneBlock> LOBBY_PHONE = BLOCKS.register("lobby_phone",
            () -> new LobbyPhoneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLUE)
                    .strength(1.5f)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .lightLevel(state -> 4)));

    /** 도어폰: 세대 현관 버튼형 초인종 */
    public static final RegistryObject<DoorPhoneBlock> DOOR_PHONE = BLOCKS.register("door_phone",
            () -> new DoorPhoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(1.0f).sound(SoundType.METAL).noOcclusion()));

    /** 비디오폰: 세대 안 화면형 수신기 (12 x 8 픽셀) */
    public static final RegistryObject<ReceiverBlock> VIDEO_PHONE = BLOCKS.register("video_phone",
            () -> new ReceiverBlock(BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(1.0f)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(s -> s.getValue(WallpadBlock.RINGING) ? 7 : 1),
                    DeviceRegistry.Kind.VIDEO_PHONE, 6, 4, 12, 1, false));

    /** 인터폰: 세대 안 수화기형 수신기 (6 x 11 픽셀) */
    public static final RegistryObject<ReceiverBlock> INTERPHONE = BLOCKS.register("interphone",
            () -> new ReceiverBlock(BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(1.0f)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(s -> s.getValue(WallpadBlock.RINGING) ? 5 : 0),
                    DeviceRegistry.Kind.INTERPHONE, 3, 3, 14, 2, false));

    /** 경비실기: 경비실 책상 위 콘솔 */
    public static final RegistryObject<ReceiverBlock> GUARD_CONSOLE = BLOCKS.register("guard_console",
            () -> new ReceiverBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.5f)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(s -> s.getValue(WallpadBlock.RINGING) ? 7 : 2),
                    DeviceRegistry.Kind.GUARD_CONSOLE, 7, 0, 7, 12, true));

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
