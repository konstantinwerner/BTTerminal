package com.example.konstantin.btterminal;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Set;


public class BTDeviceList extends Activity {
    // Debug
    private static final String TAG = "DeviceListDialog";
    private static final boolean DBG = true;

    // Return Intent Extra
    public static String EXTRA_DEVICE_ADDRESS = "device_address";

    // Member Variables
    private BluetoothAdapter mBluetoothAdapter;
    private ArrayAdapter<String> mPairedDevicesArrayAdapter;
    private ArrayAdapter<String> mNewDevicesArrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_btdevicelist);

        // Set canceled in case activity is closed
        setResult(Activity.RESULT_CANCELED);

        Button scanButton = (Button) findViewById(R.id.button_scan);
        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanForDevices();
                v.setVisibility(View.GONE);
            }
        });

        // Init ArrayAdapters & ListViews
        mPairedDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.arrayadapter_devicename);
        mNewDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.arrayadapter_devicename);

        ListView listPairedDevices = (ListView) findViewById(R.id.list_paired_devices);
        listPairedDevices.setAdapter(mPairedDevicesArrayAdapter);
        listPairedDevices.setOnItemClickListener(mDeviceClickListener);

        ListView listNewDevices = (ListView) findViewById(R.id.list_new_devices);
        listNewDevices.setAdapter(mNewDevicesArrayAdapter);
        listNewDevices.setOnItemClickListener(mDeviceClickListener);

        // Register IntentFilter for when a Device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);

        // Register IntentFilter for when Discovery is finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // get a set of currently paired Devices and list them
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
            for (BluetoothDevice device : pairedDevices) {
                mPairedDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            }
        } else {
            String noDevices = getResources().getText(R.string.text_no_paired_devices).toString();
            mPairedDevicesArrayAdapter.add(noDevices);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.cancelDiscovery();
        }

        this.unregisterReceiver(mReceiver);
    }

    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            mBluetoothAdapter.cancelDiscovery();

            // Get MAC-Address
            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);

            Intent intent = new Intent();
            intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    mNewDevicesArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setProgressBarIndeterminateVisibility(false);
                setTitle(R.string.text_select_device);

                if (mNewDevicesArrayAdapter.getCount() == 0) {
                    String noDevices = getResources().getText(R.string.text_no_new_devices).toString();
                    mNewDevicesArrayAdapter.add(noDevices);
                }
            }
        }
    };

    private void scanForDevices() {
        if (DBG) Log.d(TAG, "scanForDevices()");

        setProgressBarIndeterminateVisibility(true);
        setTitle(R.string.text_scanning);

        findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);

        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }

        mBluetoothAdapter.startDiscovery();
    }
}
