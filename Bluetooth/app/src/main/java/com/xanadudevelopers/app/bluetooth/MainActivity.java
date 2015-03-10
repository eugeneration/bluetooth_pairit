package com.xanadudevelopers.app.bluetooth;

import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends ActionBarActivity {

    private static final int REQUEST_ENABLE_BT = 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        changeState(State.DISCONNECTED);

        // listen to changes in bluetooth connection_status
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothBroadcastReceiver, filter);

        // Ensure bluetooth is setup before continuing setup
        setUpBluetooth();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothBroadcastReceiver);
    }

    /**
     *
     * MAJOR FUNCTIONS
     *
     */

    private void disconnect() {
        changeState(State.DISCONNECTED);
    }


    /**
     *
     * BLUETOOTH ENABLING
     *
     */

    private void setUpBluetooth() {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            makeShortToast("Sorry! This device doesn't support bluetooth!");
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            // bluetooth is enabled, continue the connection process
            findDevices();
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // return from the enable bluetooth thing
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                findDevices();
            } else if (resultCode == RESULT_CANCELED) {
                makeShortToast("You need to enable bluetooth to use PairIt!");
            } else {
                // weird output
                Log.v("debug", "I don't know what happened here: result code = " + resultCode);
            }
        }
    }

    // listens to changes in Bluetooth
    private final BroadcastReceiver bluetoothBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        // makeShortToast("Bluetooth off");
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        makeShortToast("Bluetooth turning off - disconnecting PairIt");
                        disconnect();
                        break;
                    case BluetoothAdapter.STATE_ON:
                        makeShortToast("Bluetooth enabled");
                        findDevices();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        break;
                }
            }
        }
    };

    /**
     *
     * BLUETOOTH PAIRING
     *
     */

    // Bluetooth is now enabled at this point
    private void findDevices() {
        changeState(State.PAIRING);
        showBluetoothPairingDialog();
        // pair then connect
    }

    private int mStackLevel = 0; // the number of fragments open
    void showBluetoothPairingDialog() {
        mStackLevel++;

        // DialogFragment.show() will take care of adding the fragment
        // in a transaction.  We also want to remove any currently showing
        // dialog, so make our own transaction and take care of that here.
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag("dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        // Create and show the dialog.
        DialogFragment newFragment = BluetoothDialogFragment.newInstance(mStackLevel);
        newFragment.show(ft, "dialog");
    }

    /**
     *
     * BLUETOOTH CONNECTION
     *
     */

    public void connect() {
        changeState(State.CONNECTING);
    }

    /**
     *
     * PRIVATE METHODS
     *
     */

    // display a quick toast to the user
    public void makeShortToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }


    /**
     *
     * STATE STUFF
     *
     */

    private State connection_status; // DO NOT SET THIS VARIABLE MANUALLY
    enum State {
        DISCONNECTED,
        PAIRING,
        CONNECTING,
        CONNECTED
    }

    // change the connection_status of the app
    private void changeState(State newState) {
        connection_status = newState;

        String text;
        switch (newState){
            case DISCONNECTED:
                text = "Disconnected";
                break;
            case PAIRING:
                text = "Pairing";
                break;
            case CONNECTING:
                text = "Connecting";
                break;
            case CONNECTED:
                text = "Connected";
                break;
            default:
                text = "BAD STATE";
        }
        ((TextView) findViewById(R.id.debug_text)).setText(text);
    }
}
