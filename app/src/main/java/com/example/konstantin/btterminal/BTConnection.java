package com.example.konstantin.btterminal;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BTConnection {
    // Debug
    private static final String TAG = "BTConnection";
    private static final boolean DBG = true;

    // Name for SDP record for Server Socket
    private static final String NAME = "BTConnection";

    // Standard Serial Port UUID
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    // Key Names
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent Request Codes
    public static final int REQ_DEVICE_LIST = 1;
    public static final int REQ_ENABLE_BT = 2;

    // Messages from Data Transfer Handler
    public static final int MSG_STATE_CHANGE = 1;
    public static final int MSG_DEVICE_NAME = 2;
    public static final int MSG_DATA_READ = 3;
    public static final int MSG_DATA_WRITTEN = 4;
    public static final int MSG_TOAST = 5;

    // Connection States
    public static final int STATE_NONE = 0;
    public static final int STATE_LISTEN = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 3;

    // Member Variables
    private Context mContext;
    private Handler mHandler;
    private BluetoothAdapter mBluetoothAdapter;

    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;

    private boolean mBTListening;
    private boolean mBTAvailable;
    private boolean mBTEnabled;

//-- Service Functions -----------------------------------------------------------------------------
    public BTConnection(Context context, Handler handler) {
        if (DBG) Log.d(TAG, "BTConnection()");

        mState = STATE_NONE;
        mBTListening = false;
        mBTAvailable = false;
        mBTEnabled = false;

        mContext = context;
        mHandler = handler;

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter != null) {
            mBTAvailable = true;
            mBTEnabled = mBluetoothAdapter.isEnabled();
        }
    }

    public void onDestroy() {
        if (DBG) Log.d(TAG, "onDestroy()");
        stop();
    }

//-- Setter/Getter Functions -----------------------------------------------------------------------------

    private synchronized void setState(int state) {
        if (DBG) Log.d(TAG, "setState(" + mState + " -> " + state + ")");

        mState = state;
        // Send StateChange MSG to UI Activity for updating

        mHandler.obtainMessage(MSG_STATE_CHANGE, state, -1).sendToTarget();
    }

    public synchronized int getState() {
        if (DBG) Log.d(TAG, "getState() = " + mState);
        return mState;
    }

    public boolean isAvailable() {
        if (DBG) Log.d(TAG, "isAvailable() = " + mBTAvailable);

        return mBTAvailable;
    }

    public boolean isEnabled() {
        if (DBG) Log.d(TAG, "isEnabled()");

        if (mBTAvailable) {
            mBTEnabled = mBluetoothAdapter.isEnabled();
            return mBTEnabled;
        } else {
            return false;
        }
    }

    public void setEnabled(boolean enable) {
        if (DBG) Log.d(TAG, "setEnabled(" + enable + ")");

        if (mBTAvailable && !mBTEnabled && enable) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            ((Activity) mContext).startActivityForResult(enableIntent, REQ_ENABLE_BT);
        } else if (mBTAvailable && mBTEnabled && !enable) {
            mBluetoothAdapter.disable();
        }
    }

    public void showDeviceList() {
        Intent scanIntent = new Intent(mContext, BTDeviceList.class);
        ((Activity) mContext).startActivityForResult(scanIntent, REQ_DEVICE_LIST);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (DBG) Log.d(TAG, "onActivityResult(" + resultCode + ")");

        switch (requestCode) {
            case REQ_DEVICE_LIST:
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getExtras().getString(BTDeviceList.EXTRA_DEVICE_ADDRESS);
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    connect(device);
                }
                break;

            case REQ_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                    mBTEnabled = true;
                } else {
                    mBTEnabled = false;
                }
        }
    }

    public synchronized void listen(boolean enable) {
        if (DBG) Log.d(TAG, "listen(" + enable + ")");

        setState(STATE_NONE);

        mBTListening = enable;

        if (enable) {

            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }

            if (mConnectedThread != null) {
                mConnectedThread.cancel();
                mConnectedThread = null;
            }

            if (mAcceptThread == null) {
                mAcceptThread = new AcceptThread();
                mAcceptThread.start();
            }

            setState(STATE_LISTEN);
        } else {
            if (mAcceptThread != null) {
                mAcceptThread.cancel();
                mAcceptThread = null;
            }
        }
    }

    public synchronized void connect(BluetoothDevice device) {
        if (DBG) Log.d(TAG, "connect(" + device + ")");

        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null)
            {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        if (mConnectedThread != null)
        {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        mConnectThread = new ConnectThread(device);
        mConnectThread.start();

        setState(STATE_CONNECTING);
    }

    public synchronized void disconnect() {
        if (DBG) Log.d(TAG, "disconnect()");

        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null)
            {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        if (mConnectedThread != null) {
            mConnectedThread.close();
            mConnectedThread = null;
        }

        makeToast(mContext.getString(R.string.toast_disconnected));

        listen(mBTListening);
    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        if (DBG) Log.d(TAG, "connected()");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        mConnectedThread = new ConnectedThread(socket);
        mConnectedThread.start();

        // Send name of connected device back to UI Activity
        Message msg = mHandler.obtainMessage(MSG_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    public synchronized void stop() {
        if (DBG) Log.d(TAG, "stop()");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mAcceptThread != null) {
            mAcceptThread.cancel();
            mAcceptThread = null;
        }

        setState(STATE_NONE);
    }

    public void write(byte[] data) {
        if (DBG) Log.d(TAG, "write()");
        ConnectedThread ct;

        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            ct = mConnectedThread;
        }

        ct.write(data);
    }

    private void makeToast(String text) {
        Message msg = mHandler.obtainMessage(MSG_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(TOAST, text);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    private void connectionFailed() {
        if (DBG) Log.d(TAG, "connectionFailed()");

        listen(mBTListening);

        makeToast(mContext.getString(R.string.toast_unable_to_connect));
    }

    private void connectionLost() {
        if (DBG) Log.d(TAG, "connectionLost()");

        listen(mBTListening);

        makeToast(mContext.getString(R.string.toast_lost_connection));
    }

//##################################################################################################

    private class AcceptThread extends Thread {

        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            if (DBG) Log.d(TAG, "AcceptThread()");
            BluetoothServerSocket tmp = null;

            try {
                tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, SPP_UUID);
            } catch (IOException e) {
                if (DBG) Log.d(TAG, "AcceptThread() ServerSocket listen() failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            if (DBG) Log.d(TAG, "BEGIN AcceptThread");
            setName("AcceptThread");

            BluetoothSocket socket;

            while (mState != STATE_CONNECTED) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    if (DBG) Log.d(TAG, "AcceptThread run() ServerSocket accept() failed", e);
                    break;
                }

                if (socket != null) {
                    synchronized (BTConnection.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // Connecting successfull. Start Connected Thread
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Not ready or already connected
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    if (DBG) Log.d(TAG, "AcceptThread run() Socket close() failed", e);
                                }
                                break;
                        }
                    }
                }
            }

            if (DBG) Log.d(TAG, "END AcceptThread");
        }

        public void cancel() {
            if (DBG) Log.d(TAG, "AcceptThread cancel()");

            try {
                mmServerSocket.close();
            } catch (IOException e) {
                if (DBG) Log.d(TAG, "AcceptThread cancel() ServerSocket close() failed", e);
            }
        }
    }

//##################################################################################################

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            if (DBG) Log.d(TAG, "ConnectThread()");
            mmDevice = device;
            BluetoothSocket tmp = null;

            try {
                tmp = device.createRfcommSocketToServiceRecord(SPP_UUID);
            } catch (IOException e) {
                if (DBG) Log.d(TAG, "ConnectThread() Socket create() failed", e);
            }

            mmSocket = tmp;
        }

        public void run() {
            if (DBG) Log.d(TAG, "BEGIN ConnectThread");
            setName("ConnectThread");

            mBluetoothAdapter.cancelDiscovery();

            try {
                mmSocket.connect();
            } catch (IOException e) {

                try {
                    mmSocket.close();
                } catch (IOException e1) {
                    if (DBG) Log.d (TAG, "ConnectThread run() Socket close() failed", e1);
                }

                connectionFailed();

                return;
            }

            // Reset ConnectThread
            synchronized (BTConnection.this) {
                mConnectThread = null;
            }

            // Start ConnectedThread
            connected(mmSocket, mmDevice);

            if (DBG) Log.d(TAG, "END ConnectThread");
        }

        public void cancel() {
            if (DBG) Log.d(TAG, "ConnectThread cancel()");
            try {
                mmSocket.close();
            } catch (IOException e) {
                if (DBG) Log.d(TAG, "ConnectThread cancel() Socket close() failed", e);
            }
        }
    }

//##################################################################################################

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        private boolean mmConnected = false;

        public ConnectedThread(BluetoothSocket socket) {
            if (DBG) Log.d(TAG, "ConnectedThread()");

            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                if (DBG) Log.d(TAG, "Connected Thread() Socket getStream() failed", e);
            }

            mmConnected = true;

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            if (DBG) Log.d(TAG, "BEGIN ConnectedThread");
            setName("ConnectedThread");

            byte[] buffer;
            int bytes;

            while (mmConnected) {
                try {
                    buffer = new byte[1024];
                    bytes = mmInStream.read(buffer);

                    mHandler.obtainMessage(MSG_DATA_READ, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    if (DBG) Log.d(TAG, "ConnectedThread run() inStream read() failed", e);
                    if (mmConnected) {
                        // Only report connection loss if unintentional disconnect
                        connectionLost();
                    }
                    break;
                }
            }

            if (DBG) Log.d(TAG, "END ConnectedThread");
        }

        public void write(byte[] buffer) {
            if (DBG) Log.d(TAG, "ConnectedThread write()");
            try {
                mmOutStream.write(buffer);

                mHandler.obtainMessage(MSG_DATA_WRITTEN, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {
                if (DBG) Log.d(TAG, "ConnectedThread write() outStream write() failed", e);
            }
        }

        public void close() {
            if (DBG) Log.d(TAG, "ConnectedThread close()");

            mmConnected = false;

            cancel();
        }

        public void cancel() {
            if (DBG) Log.d(TAG, "ConnectedThread cancel()");

            try {
                mmSocket.close();
            } catch (IOException e) {
                if (DBG) Log.d(TAG, "ConnectedThread cancel() Socket close() failed", e);
            }
        }
    }
}

