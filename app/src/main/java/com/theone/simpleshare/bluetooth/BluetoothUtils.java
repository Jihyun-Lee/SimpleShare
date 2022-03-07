package com.theone.simpleshare.bluetooth;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;

public final class BluetoothUtils {

    public static final int MINOR_DEVICE_CLASS_POINTING =
            Integer.parseInt("0000010000000", 2);
    public static final int MINOR_DEVICE_CLASS_JOYSTICK =
            Integer.parseInt("0000000000100", 2);
    public static final int MINOR_DEVICE_CLASS_GAMEPAD =
            Integer.parseInt("0000000001000", 2);
    public static final int MINOR_DEVICE_CLASS_KEYBOARD =
            Integer.parseInt("0000001000000", 2);
    public static final int MINOR_DEVICE_CLASS_REMOTE =
            Integer.parseInt("0000000001100", 2);

    public static boolean isInputDevice(BluetoothDevice device) {

        int devClass = device.getBluetoothClass().getDeviceClass();

        if ((devClass & MINOR_DEVICE_CLASS_POINTING) != 0) {
            return true;
        } else if ((devClass & MINOR_DEVICE_CLASS_JOYSTICK) != 0) {
            return true;
        } else if ((devClass & MINOR_DEVICE_CLASS_GAMEPAD) != 0) {
            return true;
        } else if ((devClass & MINOR_DEVICE_CLASS_KEYBOARD) != 0) {
            return true;
        }else if ((devClass & MINOR_DEVICE_CLASS_REMOTE) != 0) {
            return true;
        }

        return false;
    }
    public static boolean isA2dpDevice(BluetoothDevice device){
        int devClass = device.getBluetoothClass().getDeviceClass();

        if(devClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
                devClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES ||
                devClass == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER ||
                devClass == BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO ||
                devClass == BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO)
        return true;
        return false;
    }
    public final static int HID_HOST = 4;
}
