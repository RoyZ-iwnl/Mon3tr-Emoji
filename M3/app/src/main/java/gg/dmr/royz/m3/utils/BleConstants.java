package gg.dmr.royz.m3.utils;

import java.util.UUID;

public class BleConstants {
    public static final String DEVICE_NAME = "RoyZ-Mon3tr";
    //不是，切利尼娜你听我解释····

    public static final UUID SERVICE_UUID =
            UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    public static final UUID CHARACTERISTIC_COMMAND_UUID =
            UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");
    public static final UUID CHARACTERISTIC_DATA_UUID =
            UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a9");

    // Command types
    public static final byte CMD_START_TRANSFER = 0x01;
    public static final byte CMD_IMAGE_DATA = 0x02;
    public static final byte CMD_END_TRANSFER = 0x03;
    public static final byte CMD_DELETE_IMAGE = 0x04;
    public static final byte CMD_REORDER_IMAGES = 0x05;
    public static final byte CMD_GET_IMAGE_LIST = 0x06;
    public static final byte CMD_SET_DISPLAY = 0x07;
    public static final byte CMD_GET_STATUS = 0x08;
}