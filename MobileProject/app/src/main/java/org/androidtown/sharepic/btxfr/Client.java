package org.androidtown.sharepic.btxfr;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

public class Client implements Runnable {
    private final String TAG = "android-btxfr/ClientThread";
    private BluetoothSocket socket;
    private final BluetoothDevice mDevice;
    private final Handler handler;
    public Handler incomingHandler;
    Looper mLoop;
    String mFilePath;

    @SuppressLint("LongLogTag")
    public Client(BluetoothDevice device, Handler handler) {
        mDevice = device;
        this.handler = handler;
    }

    public void setmFilePath(String filePath){
        mFilePath = filePath;
    }

    @SuppressLint("LongLogTag")
    public void run() {
        synchronized (this) {
            BluetoothSocket tempSocket = null;

            try {
                tempSocket = mDevice.createRfcommSocketToServiceRecord(UUID.fromString(Constants.UUID_STRING));
                socket = tempSocket;
                try {
                    Log.v(TAG, "Opening client socket");
                    socket.connect();
                    Log.v(TAG, "Connection established");

                } catch (IOException ioe) {
                    handler.sendEmptyMessage(MessageType.COULD_NOT_CONNECT);
                    Log.e(TAG, ioe.toString());
                    try {
                        socket.close();
                    } catch (IOException ce) {
                        Log.e(TAG, "Socket close exception: " + ce.toString());
                    }

                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, e.toString());

                return;
            }


            Looper.prepare();
            mLoop = Looper.myLooper();

            incomingHandler = new Handler() {
                @Override
                public void handleMessage(Message message) {
                    if (message.obj != null) {
                        Log.v(TAG, "Handle received data to send");
                        byte[] payload = (byte[]) message.obj;

                        try {
                            handler.sendEmptyMessage(MessageType.SENDING_DATA);
                            OutputStream outputStream = socket.getOutputStream();

                            // Send the header control first
                            outputStream.write(Constants.HEADER_MSB);
                            outputStream.write(Constants.HEADER_LSB);

                            // write size
                            outputStream.write(Utils.intToByteArray(payload.length));

                            // write digest
                            byte[] digest = Utils.getDigest(payload);
                            outputStream.write(digest);

                            // now write the data
                            outputStream.write(payload);
                            outputStream.flush();

                            Log.v(TAG, "Data sent.  Waiting for return digest as confirmation");
                            InputStream inputStream = socket.getInputStream();
                            byte[] incomingDigest = new byte[16];
                            int incomingIndex = 0;

                            try {
                                while (true) {
                                    byte[] header = new byte[1];
                                    inputStream.read(header, 0, 1);
                                    incomingDigest[incomingIndex++] = header[0];
                                    if (incomingIndex == 16) {
                                        if (Utils.digestMatch(payload, incomingDigest)) {
                                            Log.v(TAG, "Digest matched OK.  Data was received OK.");
                                            handler.sendEmptyMessage(MessageType.DATA_SENT_OK);
                                        } else {
                                            Log.e(TAG, "Digest did not match.  Might want to resend.");
                                            handler.sendEmptyMessage(MessageType.DIGEST_DID_NOT_MATCH);
                                        }

                                        break;
                                    }
                                }
                            } catch (Exception ex) {
                                Log.e(TAG, ex.toString());
                            }

                        } catch (Exception e) {
                            Log.e(TAG, e.toString());
                        }

                        cancel();
                    }
                }
            };

            Message msg = new Message();
            msg.what = MessageType.READY_FOR_DATA;
            msg.obj = mFilePath;
            handler.sendMessage(msg);

            Looper.loop();
        }
    }

    @SuppressLint("LongLogTag")
    public void cancel() {
        try {
            if (socket.isConnected()) {
                socket.close();
            }

            mLoop.quit();
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }
}