package com.example.konstantin.btterminal;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


public class BTTerminal extends Activity {

    // Debug
    private static final String TAG = "BTTerminal";
    private static final boolean DBG = true;

    // Messages from Data Transfer Handler
    public static final int MSG_STATE_CHANGE = 1;
    public static final int MSG_READ = 2;
    public static final int MSG_WRITE = 3;
    public static final int MSG_DEVICE_NAME = 4;
    public static final int MSG_TOAST = 5;

    // Key Names
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent Request Codes
    private static final int REQ_CONNECT_DEVICE = 1;
    private static final int REQ_ENABLE_BT = 2;

    // Layout Views
    private TextView mDataTextView;
    private EditText mDataOutEdit;
    private Button mSendButton;

    // Class Member Variables
    private String mConnectedDeviceName = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BTConnectionService mTerminalService;
    private boolean mLocalEcho;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DBG) Log.d(TAG, "onCreate()");

        setContentView(R.layout.activity_btterminal);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            if (DBG) Log.d(TAG, "No Bluetooth");
            Toast.makeText(this, R.string.toast_no_bt, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (DBG) Log.d(TAG, "onStart()");

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQ_ENABLE_BT);
        } else {
            if (mTerminalService == null) setupTerminal();
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if (DBG) Log.d(TAG, "onResume()");

        if (mTerminalService != null) {
            if (mTerminalService.getState() == BTConnectionService.STATE_NONE) {
                mTerminalService.startListen();
            }
        }
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
        if (DBG) Log.d(TAG, "onPause()");
    }

    @Override
    public void onStop() {
        super.onStop();
        if (DBG) Log.d(TAG, "onStop()");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (DBG) Log.d(TAG, "onDestroy()");

        if (mTerminalService != null) mTerminalService.stop();
    }

//##################################################################################################

    private void setupTerminal() {
        if (DBG) Log.d(TAG, "setupTerminal()");

        mDataTextView = (TextView) findViewById(R.id.text_data);

        mLocalEcho = false;

        // Init Input Textfield
        mDataOutEdit = (EditText) findViewById(R.id.edit_data_out);
        mDataOutEdit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                    String data = view.getText().toString();
                    sendData(data);
                }
                if (DBG) Log.d(TAG, "onEditorAction()");
                return true;
            }
        });

        // Init Send Button
        mSendButton = (Button) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (DBG) Log.d(TAG, "onClick()");
                TextView view = (TextView) findViewById(R.id.edit_data_out);
                String data = view.getText().toString();
                sendData(data);
            }
        });

        mTerminalService = new BTConnectionService(this, mHandler);
    }

    private void sendData(String data) {
        if (DBG) Log.d(TAG, "sendData(" + data + ")");

        if (mTerminalService.getState() != BTConnectionService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.toast_not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        if (data.length() > 0) {
            byte[] send = data.getBytes();
            mTerminalService.write(send);

            mDataOutEdit.setText("");
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (DBG) Log.d(TAG, "onActivityResult(" + resultCode + ")");

        switch (requestCode) {
            case REQ_CONNECT_DEVICE:
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getExtras().getString(BTDeviceList.EXTRA_DEVICE_ADDRESS);
                    BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                    mTerminalService.connect(device);
                }
                break;

            case REQ_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                    setupTerminal();
                } else {
                    if (DBG) Log.d(TAG, "BlueTooth not enabled!");
                    Toast.makeText(this, R.string.toast_bt_not_enabled, Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            if (DBG) Log.d(TAG, "handleMessage(" + msg.what + ", " + msg.arg1 + ", " + msg.arg2 + ")");

            switch (msg.what) {
                case MSG_STATE_CHANGE:
                    if (DBG) Log.d(TAG, "MSG_STATE_CHANGE: " + msg.arg1);

                    String subtitle;

                    switch (msg.arg1) {
                        case BTConnectionService.STATE_CONNECTED:
                            subtitle = getResources().getText(R.string.title_connected_to) +
                                     " " +
                                     mConnectedDeviceName;
                            getActionBar().setSubtitle(subtitle);

                            mDataTextView.setText("");
                            break;

                        case BTConnectionService.STATE_CONNECTING:
                            subtitle = getResources().getText(R.string.title_connecting).toString();
                            getActionBar().setSubtitle(subtitle);
                            break;

                        case BTConnectionService.STATE_LISTEN:
                        case BTConnectionService.STATE_NONE:
                            subtitle = getResources().getText(R.string.title_not_connected).toString();

                            getActionBar().setSubtitle(subtitle);

                            break;
                    }
                    break;

                case MSG_WRITE:
                    if (DBG) Log.d(TAG, "MSG_WRITE");

                    byte[] outBuf = (byte[]) msg.obj;
                    String outMsg = new String(outBuf);

                    if (mLocalEcho) {
                        mDataTextView.append(outMsg);
                    }
                    break;

                case MSG_READ:
                    if (DBG) Log.d(TAG, "MSG_READ");

                    byte[] inBuf = (byte[]) msg.obj;
                    String inMsg = new String(inBuf);
                    mDataTextView.append(inMsg);
                    break;

                case MSG_DEVICE_NAME:
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to " + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;

                case MSG_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST), Toast.LENGTH_SHORT).show();
                    break;

            }
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.btterminal, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_connect:
                Intent scanIntent = new Intent(this, BTDeviceList.class);
                startActivityForResult(scanIntent, REQ_CONNECT_DEVICE);
                return true;

            case R.id.action_disconnect:
                mTerminalService.disconnect();
                return true;

            case R.id.action_clear:
                mDataTextView.setText("");
                return true;

            case R.id.action_echo:
                mLocalEcho = !mLocalEcho;
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        int state = mTerminalService.getState();

        switch (state) {
            case BTConnectionService.STATE_CONNECTING:
            case BTConnectionService.STATE_CONNECTED:
                menu.findItem(R.id.action_connect).setVisible(false);
                menu.findItem(R.id.action_disconnect).setVisible(true);
                break;

            case BTConnectionService.STATE_NONE:
            case BTConnectionService.STATE_LISTEN:
                menu.findItem(R.id.action_connect).setVisible(true);
                menu.findItem(R.id.action_disconnect).setVisible(false);
                break;
        }

        menu.findItem(R.id.action_echo).setChecked(mLocalEcho);

        return true;
    }

}
