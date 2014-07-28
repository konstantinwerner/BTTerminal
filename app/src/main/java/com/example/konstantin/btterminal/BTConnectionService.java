package com.example.konstantin.btterminal;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BTConnectionService {
    // Debug
    private static final String TAG = "BTConnectionService";
    private static final boolean DBG = true;

    // Name for SDP record for Server Socket
    private static final String NAME = "BTTerminal";

    // Standard Serial Port UUID
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");

    // Connection States
    public static final int STATE_NONE = 0;
    public static final int STATE_LISTEN = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_CONNECTED = 3;

    // Member Variables
    private final Context mContext;
    private final BluetoothAdapter mBluetoothAdapter;
    private final Handler mHandler;
    private AcceptThread mAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    private boolean mListening;

    public BTConnectionService(Context context, Handler handler) {
        if (DBG) Log.d(TAG, "BTConnectionService()");

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        mListening = false;

        mContext = context;
        mHandler = handler;
    }

    private synchronized void setState(int state) {
        if (DBG) Log.d(TAG, "setState(" + mState + " -> " + state + ")");

        mState = state;

        // Sen StateChange MSG to UI Activity for updating
        mHandler.obtainMessage(BTTerminal.MSG_STATE_CHANGE, state, -1).sendToTarget();
    }

    public synchronized int getState() {
        if (DBG) Log.d(TAG, "getState()");
        return mState;
    }

    public synchronized void listen(boolean enable) {
        if (DBG) Log.d(TAG, "listen(" + enable + ")");

        setState(STATE_NONE);

        mListening = enable;

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

        Message msg = mHandler.obtainMessage(BTTerminal.MSG_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BTTerminal.TOAST, mContext.getString(R.string.toast_disconnected));
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        listen(mListening);
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
        Message msg = mHandler.obtainMessage(BTTerminal.MSG_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(BTTerminal.DEVICE_NAME, device.getName());
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

    private void connectionFailed() {
        if (DBG) Log.d(TAG, "connectionFailed()");

        listen(mListening);

        Message msg = mHandler.obtainMessage(BTTerminal.MSG_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BTTerminal.TOAST, mContext.getString(R.string.toast_unable_to_connect));
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    private void connectionLost() {
        if (DBG) Log.d(TAG, "connectionLost()");

        listen(mListening);

        Message msg = mHandler.obtainMessage(BTTerminal.MSG_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(BTTerminal.TOAST, mContext.getString(R.string.toast_lost_connection));
        msg.setData(bundle);
        mHandler.sendMessage(msg);
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
                    synchronized (BTConnectionService.this) {
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
            synchronized (BTConnectionService.this) {
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
                    mHandler.obtainMessage(BTTerminal.MSG_READ, bytes, -1, buffer).sendToTarget();
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
                mHandler.obtainMessage(BTTerminal.MSG_WRITE, -1, -1, buffer).sendToTarget();
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

