package com.example.nearbyfiletransfer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class Sender {
    //This is sender's endpoint ID
    private String DEVICE_ADVERTISE_ID = null;
    private Activity mActivity;

    // Our handle to Nearby Connections
    private ConnectionsClient connectionsClient;
    //connection service id
    private final String SERVICE_ID = "com.example.d2d";
    //set P2P strategy
    private String strategy = null;
    //counter for printing less log
    private int print_once_every_times = 0;
    //todo: may need fix
    //list of connected discoverer
    private ArrayList<String> connectedDevices = new ArrayList<>();
    //todo: newly added
    private FileUtils fileUtils;

    public Sender(String DEVICE_ADVERTISE_ID, Activity activity, String strategy){
        this.DEVICE_ADVERTISE_ID = DEVICE_ADVERTISE_ID;
        this.mActivity = activity;
        this.strategy =strategy;
        connectionsClient = Nearby.getConnectionsClient(this.mActivity);
        fileUtils = new FileUtils(mActivity);
    }

    public String getDEVICE_ADVERTISE_ID() {
        return DEVICE_ADVERTISE_ID;
    }

    //In this case, the advertiser is the sender. So the following callback won't be called.
    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String s, @NonNull Payload payload) {
            Log.d("Sender", "onPayloadReceived called with param: " + s);
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endPointId, @NonNull PayloadTransferUpdate payloadTransferUpdate) {
            if(print_once_every_times % 100 == 0){
                Log.d("Sender", "onPayloadTransferUpdate called with param: " + endPointId);
                print_once_every_times = 0;
            }
            print_once_every_times++;

            //todo: newly added(may need to fix)
            switch (payloadTransferUpdate.getStatus()){
                case ConnectionsStatusCodes.STATUS_OUT_OF_ORDER_API_CALL:
                    Log.d("Sender", "endpoint: " + endPointId + " --OUT_OF_ORDER_API_CALL");
                //if there's no active (or pending) connection to the remote endpoint.
                case ConnectionsStatusCodes.STATUS_ENDPOINT_UNKNOWN:
                    Log.d("Sender", "endpoint: " + endPointId + " is pending(not active)");
                    break;
                case ConnectionsStatusCodes.STATUS_OK:
                    Log.d("Sender", "endpoint: " + endPointId + "called,  none of the above errors occurred");
                    break;
            }
        }
    };

    private AdvertisingOptions setP2PStrategy(){
        if("P2P_POINT_TO_POINT".equals(strategy)){
            return new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build();
        }else if("P2P_STAR".equals(strategy)){
            return new AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build();
        }
        return null;
    }

    public void startAdvertising () {
        //todo: use different strategy in different cases
        AdvertisingOptions advertisingOptions = setP2PStrategy();
        Toast.makeText(mActivity, "Sender started to advertise", Toast.LENGTH_LONG).show();

        Log.d("Sender", "started to advertise");
        Log.d("Sender", "Sender endpoint id is: " + DEVICE_ADVERTISE_ID);


        connectionsClient.startAdvertising(DEVICE_ADVERTISE_ID, SERVICE_ID, new ConnectionLifecycleCallback() {
            @Override
            public void onConnectionInitiated(@NonNull String endPointId, @NonNull ConnectionInfo connectionInfo) {
                Log.d("Sender", "onConnectionInitiated param: " + endPointId);
                connectionsClient.acceptConnection(endPointId, payloadCallback);
            }

            @Override
            public void onConnectionResult(@NonNull String endPointId, @NonNull ConnectionResolution connectionResolution) {
                switch (connectionResolution.getStatus().getStatusCode()) {
                    case ConnectionsStatusCodes.STATUS_OK:
                        // We're connected! Can now start sending and receiving data.
                        connectedDevices.add(endPointId);

                        Uri uri = NearbyService.getUri();
                        Payload fullFilePayload = fileUtils.uri2Payload(uri);
                        Log.e("Sender", "STATUS_OK with endpoint: " + endPointId + " and payload id: " + fullFilePayload.getId());
                        String fileName = NearbyService.getFileName();

                        if(fullFilePayload != null & fileName != null){
                            createPayloadAndSend(endPointId, fullFilePayload, fileName);
                        }

                        break;
                    case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                        // The connection was rejected by one or both sides.
                        Log.d("Sender","CONNECTION_REJECTED -- called from advertiser");
                        break;
                    case ConnectionsStatusCodes.STATUS_ERROR:
                        // The connection broke before it was able to be accepted.
                        Log.d("Sender","connection broke before it was able to be accepted -- called from advertiser");
                        break;
                    default:
                        // Unknown status code
                        Log.d("Sender", "unknown state code ---- called from advertiser");
                }
            }

            @Override
            public void onDisconnected(@NonNull String endpointId) {
                Toast.makeText(mActivity, "Sender disconnected", Toast.LENGTH_LONG).show();
                connectedDevices.remove(endpointId);
                Log.d("Sender", "Sender disconnected");
            }
        }, advertisingOptions);
    }


    private void sendFile(String endPointId, Payload senderFilenameBytesPayload, Payload senderFilePayload) {

        Log.d("Sender", "sendFile function param endpointId" + endPointId);
        Log.e("Sender", "send file to: " + endPointId);

        //todo: change first argument from endpoint to list(connectedDevices)
        connectionsClient.sendPayload(connectedDevices, senderFilenameBytesPayload).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                Log.e("Sender", "sending file name payload to " + endPointId);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                e.printStackTrace();
                Log.e("Sender", "send file name payload to " + endPointId + " failed.");
            }
        });

        //todo:換換payload id?
        //todo: change first argument from endpoint to list(connectedDevices)
        Log.e("Sender", "endpoint is " + endPointId);
        connectionsClient.sendPayload(connectedDevices, senderFilePayload).addOnSuccessListener(new OnSuccessListener<Void>() {
                @Override
                public void onSuccess(Void unused) {
                    Log.e("Sender", "sending file payload to " + endPointId);
                }
        }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    e.printStackTrace();
                    Log.e("Sender", "send file payload to " + endPointId + " failed.");
                }
        });

    }

    //filenameMessage is the id of payload for whole file content + fileName
    public void createPayloadAndSend(String endpointId, Payload fullFilePayload, String fileName){
        String filenameMessage = fullFilePayload.getId() + ":" + fileName;
        Log.d("Sender", "sent file message is " + filenameMessage);
        Payload senderFilenameBytesPayload = Payload.fromBytes(filenameMessage.getBytes(StandardCharsets.UTF_8));

        sendFile(endpointId, senderFilenameBytesPayload, fullFilePayload);
//        SendFileThread fileThread = new SendFileThread(endpointId, senderFilenameBytesPayload, fullFilePayload);
//        fileThread.run();
    }

    //todo: newly added, using thread(and payload using list[different payload)??)
//    private class SendFileThread extends Thread{
//        private String inner_endpointId;
//        private Payload inner_senderFilenameBytesPayload;
//        private Payload inner_fullFilePayload;
//        private SendFileThread(String endpointId, Payload senderFilenameBytesPayload, Payload fullFilePayload){
//            super();
//            inner_endpointId = endpointId;
//            inner_senderFilenameBytesPayload = senderFilenameBytesPayload;
//            inner_fullFilePayload = fullFilePayload;
//        }
//        public void run() {
//            sendFile(inner_endpointId, inner_senderFilenameBytesPayload, inner_fullFilePayload);
//        }
//    }
}
