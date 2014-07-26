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
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
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
    private ListView mDataListView;
    private EditText mDataOutEdit;
    private Button mSendButton;

    // Class Member Variables
    private String mConnectedDeviceName = null;
    private ArrayAdapter<String> mDataArrayAdapter;
    private StringBuffer mOutStringBuffer;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BTTerminalService mTerminalService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DBG) Log.d(TAG, "onCreate()");

        setContentView(R.layout.activity_btterminal);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            if (DBG) Log.d(TAG, "No Bluetooth");
            Toast.makeText(this, "Bluetooth is unavailable", Toast.LENGTH_LONG).show();
            finish();
            return;
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
            if (mTerminalService.getState() == BTTerminalService.STATE_NONE) {
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

        // Init array adapter for Data List view
        mDataArrayAdapter = new ArrayAdapter<String>(this, R.layout.arrayadapter_data);
        mDataListView = (ListView) findViewById(R.id.list_data_in);
        mDataListView.setAdapter(mDataArrayAdapter);

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

        mTerminalService = new BTTerminalService(this, mHandler);
    }

    private void sendData(String data) {
        if (DBG) Log.d(TAG, "sendData(" + data + ")");

        if (mTerminalService.getState() != BTTerminalService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.toast_not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        if (data.length() > 0) {
            byte[] send = data.getBytes();
            mTerminalService.write(send);

            mOutStringBuffer.setLength(0);
            mDataOutEdit.setText(mOutStringBuffer);
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

                    String title = getResources().getText(R.string.app_name) + " ";

                    switch (msg.arg1) {
                        case BTTerminalService.STATE_CONNECTED:
                            title += getResources().getText(R.string.title_connected_to) +
                                     " " +
                                     mConnectedDeviceName;
                            getActionBar().setTitle(title);

                            mDataArrayAdapter.clear();
                            break;

                        case BTTerminalService.STATE_CONNECTING:
                            title += getResources().getText(R.string.title_connecting);
                            getActionBar().setTitle(title);
                            break;

                        case BTTerminalService.STATE_LISTEN:
                        case BTTerminalService.STATE_NONE:
                            title += getResources().getText(R.string.title_not_connected);
                            getActionBar().setTitle(title);
                            break;
                    }
                    break;

                case MSG_WRITE:
                    if (DBG) Log.d(TAG, "MSG_WRITE: " + msg.obj.toString());
                    byte[] outBuf = (byte[]) msg.obj;
                    String outMsg = new String(outBuf);
                    mDataArrayAdapter.add("> " + outMsg);
                    break;

                case MSG_READ:
                    if (DBG) Log.d(TAG, "MSG_READ: " + msg.obj.toString());
                    byte[] inBuf = (byte[]) msg.obj;
                    String inMsg = new String(inBuf);
                    mDataArrayAdapter.add("< " + inMsg);
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
        }

        return false;
    }
}
