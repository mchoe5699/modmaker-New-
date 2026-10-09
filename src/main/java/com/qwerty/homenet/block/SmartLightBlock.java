package com.qwerty.homenet.block;

/**
 * 스마트 조명. 월패드에서 켜면 밝기 15로 빛난다. 레드스톤 출력은 없음.
 */
public class SmartLightBlock extends ControlBlock {
    public SmartLightBlock(Properties props) {
        super(props, false);
    }

    @Override
    public DeviceType defaultType() {
        return DeviceType.LIGHT;
    }
}
