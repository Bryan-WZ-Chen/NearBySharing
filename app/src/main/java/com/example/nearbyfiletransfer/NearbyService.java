package com.example.nearbyfiletransfer;

import android.app.Activity;
import android.net.Uri;

import com.google.android.gms.nearby.connection.Payload;

// This is an utility class for Activity, Sender, and Receiver.
public class NearbyService {
    private Sender mSender;
    private static Uri uri;
    private static String fileName;
    private String senderID;
    private Activity mActivity;
    private String strategy;

    private Receiver mReceiver;

    public void setSender(String DEVICE_ADVERTISE_ID, Activity activity, String strategy){
        mSender = new Sender(DEVICE_ADVERTISE_ID, activity, strategy);
    }

    public void setReceiver(String DEVICE_DISCOVER_ID, Activity activity, String strategy){
        mReceiver = new Receiver(DEVICE_DISCOVER_ID, activity, strategy);
    }

    public void senderSetMetaData(Uri uri, String fileName){
        this.uri = uri;
        this.fileName = fileName;
    }

    public static Uri getUri(){
        return uri;
    }

    public static String getFileName(){
        return fileName;
    }

    public void nearbyAdvertise(){
        if(mSender != null){
            mSender.startAdvertising();
        }
    }

    public void nearbyDiscover(){
        if(mReceiver != null){
            mReceiver.startDiscovery();
        }
    }
}
