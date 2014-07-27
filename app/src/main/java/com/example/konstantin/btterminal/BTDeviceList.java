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
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;


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
        listPairedDevices.setOnItemLongClickListener(mDeviceLongClickListener);

        ListView listNewDevices = (ListView) findViewById(R.id.list_new_devices);
        listNewDevices.setAdapter(mNewDevicesArrayAdapter);
        listNewDevices.setOnItemClickListener(mDeviceClickListener);
        listNewDevices.setOnItemLongClickListener(mDeviceLongClickListener);

        // Register IntentFilters
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);

        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);

        filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        this.registerReceiver(mReceiver, filter);

        filter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
        this.registerReceiver(mReceiver, filter);

        filter = new IntentFilter(BluetoothDevice.ACTION_UUID);
        this.registerReceiver(mReceiver, filter);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        getPairedDevices();
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
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            mBluetoothAdapter.cancelDiscovery();

            // Get MAC-Address
            String info = ((TextView) view).getText().toString();
            String address = info.substring(info.length() - 17);

            Intent intent = new Intent();
            intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    };

    private AdapterView.OnItemLongClickListener mDeviceLongClickListener = new AdapterView.OnItemLongClickListener() {
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            mBluetoothAdapter.cancelDiscovery();

            // Get MAC-Address
            String info = ((TextView) view).getText().toString();
            String address = info.substring(info.length() - 17);

            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);

            if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                removeBond(device);
            } else if (device.getBondState() == BluetoothDevice.BOND_NONE) {
                createBond(device);
            }

            return true;
        }
    };

    private boolean removeBond(BluetoothDevice device){
        try {
            Class btClass = Class.forName("android.bluetooth.BluetoothDevice");
            Method removeBondMethod = btClass.getMethod("removeBond");
            return (Boolean) removeBondMethod.invoke(device);
        } catch (Exception e) {
            if (DBG) Log.d(TAG, "removeBond() failed", e);
        }
        return false;
    }

    private boolean createBond(BluetoothDevice device) {
        try {
            Class btClass = Class.forName("android.bluetooth.BluetoothDevice");
            Method createBondMethod = btClass.getMethod("createBond");
            return (Boolean) createBondMethod.invoke(device);
        } catch (Exception e) {
            if (DBG) Log.d(TAG, "createBond() failed", e);
        }
        return false;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    String entry = device.getName() + "\n" + device.getAddress();

                    if (mNewDevicesArrayAdapter.getPosition(entry) == -1)
                        mNewDevicesArrayAdapter.add(entry);
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                setProgressBarIndeterminateVisibility(false);
                setTitle(R.string.text_select_device);
                findViewById(R.id.button_scan).setVisibility(View.VISIBLE);

                if (mNewDevicesArrayAdapter.getCount() == 0) {
                    String noDevices = getResources().getText(R.string.text_no_new_devices).toString();
                    mNewDevicesArrayAdapter.add(noDevices);
                }
            } else if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(action)) {
                int pairingVariant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1);

                if (pairingVariant == BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION) {
                    String pairingPasskey = intent.getStringExtra(BluetoothDevice.EXTRA_PAIRING_KEY);

                    if (DBG) Log.d(TAG, "Passkey :" + pairingPasskey);

                } else if (pairingVariant == BluetoothDevice.PAIRING_VARIANT_PIN) {

                    if (DBG) Log.d(TAG, "Pairing via PIN");
                }
            } else if (BluetoothDevice.ACTION_UUID.equals(action))     {


            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                int prevBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);
                int currBondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);

                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                // Unpaired
                if (prevBondState == BluetoothDevice.BOND_BONDED &&
                    currBondState == BluetoothDevice.BOND_NONE) {

                    Toast.makeText(getApplicationContext(), "Unpaired " + device.getName(), Toast.LENGTH_SHORT).show();

                } else if (prevBondState == BluetoothDevice.BOND_BONDING &&
                           currBondState == BluetoothDevice.BOND_BONDED) {

                    Toast.makeText(getApplicationContext(), "Paired with " + device.getName(), Toast.LENGTH_SHORT).show();

                    String entry = device.getName() + "\n" + device.getAddress();

                    mNewDevicesArrayAdapter.remove(entry);
                }

                getPairedDevices();
            }
        }
    };

    private void getPairedDevices() {
        if (DBG) Log.d(TAG, "getPairedDevices()");

        mPairedDevicesArrayAdapter.clear();

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

    private void scanForDevices() {
        if (DBG) Log.d(TAG, "scanForDevices()");

        setProgressBarIndeterminateVisibility(true);
        setTitle(R.string.text_scanning);
        mNewDevicesArrayAdapter.clear();

        findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);

        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }

        mBluetoothAdapter.startDiscovery();
    }
}
