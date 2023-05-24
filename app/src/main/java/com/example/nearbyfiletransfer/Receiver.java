package com.example.nearbyfiletransfer;

import android.app.Activity;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class Receiver {
    private Activity mActivity;
    // Our handle to Nearby Connections
    private ConnectionsClient connectionsClient;
    //connection service id
    private final String SERVICE_ID = "com.example.d2d";
    //set P2P strategy
    private String strategy = null;
    //file content payload id
    private Long filePayloadId;
    //file content payload
    private Payload mPayload;
    //received file name
    private String recvFileName;
    //file name payload id
    private Long fileNamePayloadId;
    private String DEVICE_DISCOVER_ID = null;

    public Receiver(String DEVICE_DISCOVER_ID, Activity mActivity, String strategy){
        this.DEVICE_DISCOVER_ID = DEVICE_DISCOVER_ID;
        this.mActivity = mActivity;
        this.strategy = strategy;
        connectionsClient = Nearby.getConnectionsClient(this.mActivity);
    }

    /**
     * Extracts the payloadId for whole file content and filename from the message
     * The format is payloadId:filename.
     */
    private String[] parseIDAndFileName(String payloadFilenameMessage) {
        Log.d("Receiver", "parseIDAndFileName called with param: " + payloadFilenameMessage);

        String[] parts = payloadFilenameMessage.split(":");

        Log.d("Receiver", "parseIDAndFileName parsed name result: " + parts[1]);
        return parts;
    }

    private DiscoveryOptions setP2PStrategy(){
        if("P2P_POINT_TO_POINT".equals(strategy)){
            return new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build();
        }else if ("P2P_STAR".equals(strategy)){
            return new DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build();
        }
        return null;
    }

    private void postProcessing(){
        Uri uri = Objects.requireNonNull(mPayload.asFile()).asUri();
        try {
            // Copy the file to a new location.
            InputStream in = mActivity.getContentResolver().openInputStream(uri);
            String path = recvFileName;

            // todo:can change to other paths
            // todo:use relative path?
            File file = new File("/storage/self/primary/Download/" + path);
            Log.d("Receiver", "file exists: " + file.exists());
            if(file.createNewFile()){
                FileUtils.copyStream(in, new FileOutputStream(file));
                Log.d("Receiver", "file created");
            }

        } catch (IOException e) {
            // Log the error.
            Log.e("Receiver", "--------------");
            e.printStackTrace();
        } finally {
            // Delete the original file.
            Log.d("Receiver", "Finally statement executed ----");
            //mActivity.getContentResolver().delete(uri, null, null);

            //todo initialize all data structure again if needed
            connectionsClient.stopAllEndpoints();
        }
    }

    private final PayloadCallback mPayloadcallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            Log.d("Receiver", "onPayloadReceived called with payload id: " + payload.getId());

            if (payload.getType() == Payload.Type.BYTES) {
                Log.d("Receiver", "onPayloadReceived called and type is bytes");
                fileNamePayloadId = payload.getId();
                String payloadFilenameMessage = new String(payload.asBytes(), StandardCharsets.UTF_8);

                String[] mIDAndName = parseIDAndFileName(payloadFilenameMessage);
                //get id of payload for file content
                filePayloadId = Long.parseLong(mIDAndName[0]);
                recvFileName = mIDAndName[1];
                Log.e("Receiver", "received file name is :" + recvFileName);

            } else if (payload.getType() == Payload.Type.FILE) {
                Log.d("Receiver", "onPayloadReceived called and type is file");

                if(filePayloadId != payload.getId()){
                    Log.e("Receiver", "Something went wrong");
                    connectionsClient.stopAllEndpoints();
                }

                //because arguments of onPayloadTransferUpdate aren't payload, so we need to store it here
                mPayload = payload;
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {
            Log.d("Receiver", "discoverer onPayloadTransferUpdate called");
            Log.d("Receiver", "discoverer onPayloadTransferUpdate payload id: " + update.getPayloadId());

            if(update.getStatus() == PayloadTransferUpdate.Status.SUCCESS && fileNamePayloadId == update.getPayloadId()){
                Toast.makeText(mActivity, "Discoverer received file name payload", Toast.LENGTH_LONG).show();
                Log.d("Receiver", "Discoverer onPayloadTransferUpdate: file name payload received");
            }
            else if(update.getStatus() == PayloadTransferUpdate.Status.SUCCESS && filePayloadId == update.getPayloadId()){
                Toast.makeText(mActivity, "Discoverer received file payload", Toast.LENGTH_LONG).show();
                postProcessing();
            }
        }
    };

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo connectionInfo) {
            connectionsClient.acceptConnection(endpointId, mPayloadcallback);
            Log.d("Receiver",  "onConnectionInitiated: accepting connection");
        }


        @Override
        public void onConnectionResult(String endpointId, ConnectionResolution result) {
            switch (result.getStatus().getStatusCode()) {
                case ConnectionsStatusCodes.STATUS_OK:
                    // We're connected! Can now start sending and receiving data.
                    Log.d("Receiver", "Connected! Can send/receive data now!");
                    Toast.makeText(mActivity, "Connection succeed", Toast.LENGTH_SHORT).show();

                    connectionsClient.stopDiscovery();
                    break;
                case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                    // The connection was rejected by one or both sides.
                    Log.d("Receiver", "Connection rejected!");
                    break;
                case ConnectionsStatusCodes.STATUS_ERROR:
                    // The connection broke before it was able to be accepted.
                    Log.d("Receiver", "error occurred before connection!");
                    break;
                default:
                    // Unknown status code
                    Log.d("Receiver", "unknown state");
            }
        }


        @Override
        public void onDisconnected(String endpointId) {
            // We've been disconnected from this endpoint. No more data can be
            // sent or received.
            Log.d("Receiver", "onDisconnected called");
            Toast.makeText(mActivity, "discoverer is disconnected", Toast.LENGTH_SHORT).show();
        }
    };


    //ref:https://codertw.com/%E7%A8%8B%E5%BC%8F%E8%AA%9E%E8%A8%80/706892/
    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
            // An endpoint was found. We request a connection to it.
            Log.d("Receiver", "onEndpointFound called with param: " + endpointId);

            connectionsClient.requestConnection(DEVICE_DISCOVER_ID, endpointId, connectionLifecycleCallback)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.d("Receiver", "Request connection succeeds.");
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        // Nearby Connections failed to request the connection.
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e("Receiver", "Error (called by discoverer) " + e);
                        }
                    });
        }

        @Override
        public void onEndpointLost(@NonNull String endpointId) {
            // A previously discovered endpoint has gone away.
            Log.d("Receiver", "A previously discovered endpoint has gone away --- called by discoverer");
        }
    };

    /**
     * Discoverers begin by invoking startDiscovery(), passing in an EndpointDiscoveryCallback
     * which will be notified whenever a nearby Advertiser is found via the onEndpointFound() callback.
     */
    public void startDiscovery() {
        DiscoveryOptions discoveryOptions = setP2PStrategy();

        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
                .addOnSuccessListener(
                        // We're discovering!
                        new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void unusedResult) {
                                Log.d("Receiver", "detected from discoverer");
                                Toast.makeText(mActivity, "detected from " + DEVICE_DISCOVER_ID, Toast.LENGTH_SHORT).show();
                            }
                        }
                )
                .addOnFailureListener(
                        // We're unable to start discovering.
                        new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception e) {
                                Log.d("Receiver", "discovering failed");
                            }
                        }
                );
    }

}
