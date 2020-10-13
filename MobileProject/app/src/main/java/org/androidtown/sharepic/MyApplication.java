package org.androidtown.sharepic;

import android.app.Application;
import android.bluetooth.BluetoothDevice;

public class MyApplication extends Application {
    private boolean isOn; //자동전송
    private String album_title;
    private BluetoothDevice device;

    public String getAlbum_title() {
        return album_title;
    }

    public void setAlbum_title(String album_title) {
        this.album_title = album_title;
    }

    public BluetoothDevice getDevice(){return device;}

    public void setDevice(BluetoothDevice bluetoothDevice){
        this.device = bluetoothDevice;
    }

    public boolean isOn() {
        return isOn;
    }

    public void setOn(boolean on) {
        isOn = on;
    }

}
