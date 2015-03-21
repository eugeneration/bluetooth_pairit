package com.xanadudevelopers.app.bluetooth;

import android.app.DialogFragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

public class BluetoothDialogFragment extends DialogFragment {
    int mNum;

    private BluetoothAdapter mBluetoothAdapter;

    /**
     * Create a new instance of MyDialogFragment, providing "num"
     * as an argument.
     */
    static BluetoothDialogFragment newInstance(int num) {
        BluetoothDialogFragment f = new BluetoothDialogFragment();

        // Supply num input as an argument.
        Bundle args = new Bundle();
        args.putInt("num", num);
        f.setArguments(args);

        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mNum = getArguments().getInt("num");

        // Pick a style based on the num.
        int style = DialogFragment.STYLE_NORMAL, theme = 0;
        switch ((mNum-1)%6) {
            case 1: style = DialogFragment.STYLE_NO_TITLE; break;
            case 2: style = DialogFragment.STYLE_NO_FRAME; break;
            case 3: style = DialogFragment.STYLE_NO_INPUT; break;
            case 4: style = DialogFragment.STYLE_NORMAL; break;
            case 5: style = DialogFragment.STYLE_NORMAL; break;
            case 6: style = DialogFragment.STYLE_NO_TITLE; break;
            case 7: style = DialogFragment.STYLE_NO_FRAME; break;
            case 8: style = DialogFragment.STYLE_NORMAL; break;
        }
        switch ((mNum-1)%6) {
            case 4: theme = android.R.style.Theme_Holo; break;
            case 5: theme = android.R.style.Theme_Holo_Light_Dialog; break;
            case 6: theme = android.R.style.Theme_Holo_Light; break;
            case 7: theme = android.R.style.Theme_Holo_Light_Panel; break;
            case 8: theme = android.R.style.Theme_Holo_Light; break;
        }
        setStyle(style, theme);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_bluetooth_dialog, container, false);
        View tv = v.findViewById(R.id.dialog_text);
        ((TextView)tv).setText("Dialog #" + mNum);

        // Discover button
        Button button = (Button)v.findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (isDiscovering)
                    stopDiscovery();
                else
                    startDiscovery();
            }
        });

        // Set up Bluetooth Device ListView
        final ArrayAdapter<BluetoothDevice> mArrayAdapter =
                new BluetoothDeviceArrayAdapter(getActivity(), R.layout.list_item_bluetooth_device);
        ListView deviceList = (ListView) v.findViewById(R.id.device_list);
        deviceList.setAdapter(mArrayAdapter);
        deviceList.setOnItemClickListener(mClickListener);

        // Get Bluetooth paired devices
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            // Loop through paired devices
            for (BluetoothDevice device : pairedDevices) {
                // Add the name and address to an array adapter to show in a ListView
                mArrayAdapter.add(device);
            }
        }

        return v;
    }

    // ----------------------------------------------------------------------------------
    //
    // BLUETOOTH DISCOVERY

    private boolean isDiscovering = false;
    private BroadcastReceiver bluetoothDiscoveryReceiver;

    // try to find any available bluetooth devices
    private void startDiscovery() {
        Log.v("discovery", "Start Discovery");

        if (!isDiscovering) {
            isDiscovering = true;

            // change button text
            Button button = (Button) getView().findViewById(R.id.button);
            button.setText("Stop Discovery");

            // Register the BroadcastReceiver
            ((MainActivity) getActivity()).makeShortToast("Finding Bluetooth devices in discovery mode.");

            // Set up Bluetooth Device ListView
            final ArrayAdapter<BluetoothDevice> mNewDevicesArrayAdapter =
                    new BluetoothDeviceArrayAdapter(getActivity(), R.layout.list_item_bluetooth_device);
            ListView deviceList = (ListView) getView().findViewById(R.id.new_device_list);
            deviceList.setAdapter(mNewDevicesArrayAdapter);
            deviceList.setOnItemClickListener(mClickListener);

            // Create a BroadcastReceiver for ACTION_FOUND
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            bluetoothDiscoveryReceiver = new BroadcastReceiver() {
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    // When discovery finds a device
                    if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                        // Get the BluetoothDevice object from the Intent
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        // Add the name and address to an array adapter to show in a ListView
                        // If it's already paired, skip it, because it's been listed already
                        if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                            mNewDevicesArrayAdapter.add(device);
                        }
                    } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                        stopDiscovery();
                        if (mNewDevicesArrayAdapter.getCount() == 0) {
                            ((MainActivity) getActivity()).makeShortToast("No New Devices Found");
                        }
                    }
                }
            };
            getActivity().registerReceiver(bluetoothDiscoveryReceiver, filter); // Don't forget to unregister during onDestroy

            mBluetoothAdapter.startDiscovery();
        }
    }

    // stops discovery if it is currently started
    private void stopDiscovery() {
        Log.v("discovery", "Stop Discovery");

        if (isDiscovering) {
            // change button text
            Button button = (Button) getView().findViewById(R.id.button);
            button.setText("Find Unpaired Devices");

            isDiscovering = false;
            getActivity().unregisterReceiver(bluetoothDiscoveryReceiver);
            mBluetoothAdapter.cancelDiscovery();
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();

        // if the discovery has started, stop it when leaving
        stopDiscovery();
    }

    // ----------------------------------------------------------------------------------
    //
    // BLUETOOTH CONNECTION

    // TODO: Might be a good idea to make both sides both a server and a client
    // TODO: might be a good idea to use BLE when not plugged in, and only full B w/ extra features when plugged in



    // ----------------------------------------------------------------------------------
    //
    // PRIVATE CLASSES/METHODS

    // the 'styling' for the list view
    private class BluetoothDeviceArrayAdapter extends ArrayAdapter<BluetoothDevice> {
        public BluetoothDeviceArrayAdapter(Context context, int resource) {
            super(context, resource);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext())
                        .inflate(R.layout.list_item_bluetooth_device, parent, false);
            }

            // Lookup view for data population
            TextView textView = (TextView) convertView.findViewById(R.id.text1);

            // Get the data item for this position
            BluetoothDevice device = getItem(position);
            // Populate the data into the template view using the data object
            textView.setText(device.getName() + "\n" + device.getAddress());
            // Return the completed view to render on screen
            return convertView;
        }
    }


    private AdapterView.OnItemClickListener mClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            BluetoothDevice device = (BluetoothDevice) parent.getAdapter().getItem(position);
            Log.v("dialog", "Clicked on " + device.getAddress());

            // try to connect to the device
            ((MainActivity) getActivity()).connect(device);
            stopDiscovery();

            dismiss();
        }
    };
}
