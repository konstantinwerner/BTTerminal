package com.example.konstantin.btterminal;

import android.app.Activity;
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

    // Layout Views
    private TextView mDataTextView;
    private EditText mDataOutEdit;
    private Button mSendButton;

    // Class Member Variables
    private BTConnection mBTConnection;

    private String mConnectedDeviceName;
    private boolean mLocalEcho;
    private boolean mListen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DBG) Log.d(TAG, "onCreate()");

        setContentView(R.layout.activity_btterminal);

        mBTConnection = new BTConnection(this, mBTHandler);

        if (!mBTConnection.isAvailable()) {
            if (DBG) Log.d(TAG, "No Bluetooth");
            Toast.makeText(this, R.string.toast_no_bt, Toast.LENGTH_LONG).show();
            finish();
        } else {
            setupTerminal();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (DBG) Log.d(TAG, "onStart()");

        if (!mBTConnection.isEnabled()) {
            mBTConnection.setEnabled(true);
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        if (DBG) Log.d(TAG, "onResume()");

        if (mBTConnection != null) {
            if (mBTConnection.getState() == BTConnection.STATE_NONE) {
                mBTConnection.listen(mListen);
            }
        }
    }

    @Override
    public synchronized void onPause() {
        if (DBG) Log.d(TAG, "onPause()");

        super.onPause();
    }

    @Override
    public void onStop() {
        if (DBG) Log.d(TAG, "onStop()");

        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (DBG) Log.d(TAG, "onDestroy()");

        if (mBTConnection != null) {
            mBTConnection.setEnabled(false);
            mBTConnection.stop();
        }

        super.onDestroy();
    }

    private void setupTerminal() {
        if (DBG) Log.d(TAG, "setupTerminal()");

        mDataTextView = (TextView) findViewById(R.id.text_data);

        mLocalEcho = false;
        mListen = false;

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
            public void onClick(View view) {
                if (DBG) Log.d(TAG, "onClick()");
                TextView textview = (TextView) findViewById(R.id.edit_data_out);
                String data = textview.getText().toString();
                sendData(data);
            }
        });
    }

    private void sendData(String data) {
        if (DBG) Log.d(TAG, "sendData(" + data + ")");

        if (mBTConnection.getState() != BTConnection.STATE_CONNECTED) {
            Toast.makeText(this, R.string.toast_not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        if (data.length() > 0) {
            byte[] send = data.getBytes();
            mBTConnection.write(send);

            mDataOutEdit.setText("");
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (DBG) Log.d(TAG, "onActivityResult(" + resultCode + ")");

        mBTConnection.onActivityResult(requestCode, resultCode, data);

        if (!mBTConnection.isEnabled()) {
            if (DBG) Log.d(TAG, "BlueTooth not enabled!");
            Toast.makeText(this, R.string.toast_bt_not_enabled, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private final Handler mBTHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (DBG) Log.d(TAG, "handleMessage(" + msg.what + ", " + msg.arg1 + ", " + msg.arg2 + ")");

            switch (msg.what) {
                case BTConnection.MSG_STATE_CHANGE:
                    if (DBG) Log.d(TAG, "MSG_STATE_CHANGE: " + msg.arg1);

                    String subtitle;

                    switch (msg.arg1) {
                        case BTConnection.STATE_CONNECTED:
                            subtitle = getResources().getText(R.string.title_connected_to) + " " + mConnectedDeviceName;
                            mDataTextView.setText("");
                            break;

                        case BTConnection.STATE_CONNECTING:
                            subtitle = getResources().getText(R.string.title_connecting).toString();
                            break;

                        case BTConnection.STATE_LISTEN:
                            subtitle = getResources().getText(R.string.title_listening).toString();
                            break;

                        default:
                        case BTConnection.STATE_NONE:
                            subtitle = getResources().getText(R.string.title_not_connected).toString();
                            break;
                    }

                    getActionBar().setSubtitle(subtitle);
                    break;

                case BTConnection.MSG_DATA_WRITTEN:
                    if (DBG) Log.d(TAG, "MSG_DATA_WRITTEN");

                    byte[] outBuf = (byte[]) msg.obj;
                    String outMsg = new String(outBuf);

                    if (mLocalEcho) {
                        mDataTextView.append(outMsg);
                    }
                    break;

                case BTConnection.MSG_DATA_READ:
                    if (DBG) Log.d(TAG, "MSG_DATA_READ");

                    byte[] inBuf = (byte[]) msg.obj;
                    String inMsg = new String(inBuf);
                    mDataTextView.append(inMsg);
                    break;

                case BTConnection.MSG_DEVICE_NAME:
                    mConnectedDeviceName = msg.getData().getString(BTConnection.DEVICE_NAME);
                    Toast.makeText(getApplicationContext(),
                            "Connected to " + mConnectedDeviceName,
                            Toast.LENGTH_SHORT).show();
                    break;

                case BTConnection.MSG_TOAST:
                    Toast.makeText(getApplicationContext(),
                            msg.getData().getString(BTConnection.TOAST),
                            Toast.LENGTH_SHORT).show();
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
                mBTConnection.showDeviceList();
                return true;

            case R.id.action_disconnect:
                mBTConnection.disconnect();
                return true;

            case R.id.action_clear:
                mDataTextView.setText("");
                return true;

            case R.id.action_echo:
                mLocalEcho = !mLocalEcho;
                return true;

            case R.id.action_listen:
                mListen = !mListen;
                mBTConnection.listen(mListen);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mBTConnection != null) {
            int state = mBTConnection.getState();

            switch (state) {
                case BTConnection.STATE_CONNECTING:
                case BTConnection.STATE_CONNECTED:
                    menu.findItem(R.id.action_connect).setVisible(false);
                    menu.findItem(R.id.action_disconnect).setVisible(true);
                    menu.findItem(R.id.action_listen).setVisible(false);
                    break;

                case BTConnection.STATE_NONE:
                case BTConnection.STATE_LISTEN:
                    menu.findItem(R.id.action_connect).setVisible(true);
                    menu.findItem(R.id.action_disconnect).setVisible(false);
                    menu.findItem(R.id.action_listen).setVisible(true);
                    break;
            }
        }

        menu.findItem(R.id.action_echo).setChecked(mLocalEcho);
        menu.findItem(R.id.action_listen).setChecked(mListen);

        return super.onPrepareOptionsMenu(menu);
    }

}
