package com.xanadudevelopers.app.bluetooth;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class MainActivity extends ActionBarActivity {

    // tells handler that is a message
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    /**
     * String buffer for outgoing messages
     */
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothService mService = null;

    private String mConnectedDeviceName = null;
    private StringBuffer mOutStringBuffer;
    private ArrayAdapter<String> mConversationArrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // set up the incoming message field
        ListView conversationView = (ListView) findViewById(R.id.in);
        mConversationArrayAdapter = new ArrayAdapter<>(this, R.layout.message);
        conversationView.setAdapter(mConversationArrayAdapter);
        mOutStringBuffer = new StringBuffer("");

        // Initialize the BluetoothChatService to perform bluetooth connections
        mService = new BluetoothService(this, mHandler);

        // Ensure bluetooth is setup before continuing setup
        // TODO: CHECK IF ALREADY CONNECTED
        setUpBluetooth();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.

        if (getState() == State.DISCONNECTED) {
            Log.v("debug", "On resume, find devices");
            findDevices();
            if (mService != null) {
                // Only if the state is STATE_NONE, do we know that we haven't started already
                if (mService.getState() == BluetoothService.STATE_NONE) {
                    // Start the Bluetooth chat services
                    mService.start();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bluetoothBroadcastReceiver);
        disconnect();
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

    // ----------------------------------------------------------------------------------
    // DISCONNECT
    //

    private void disconnect() {
        if (mService != null) {
            mService.stop();
        }
        changeState(State.DISCONNECTED);
    }


    // ----------------------------------------------------------------------------------
    // ENABLING
    //

    // flag to ensure that two listeners don't get the bluetooth connection sign
    private boolean enabling_bluetooth = false;
    private void setUpBluetooth() {
        // assume bluetooth isn't set up
        changeState(State.INACTIVE);

        // listen to changes in bluetooth connection_status
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(bluetoothBroadcastReceiver, filter);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            makeShortToast("Sorry! This device doesn't support bluetooth!");
        }

        enableBluetooth();
    }

    private void enableBluetooth() {

        if (!mBluetoothAdapter.isEnabled()) {
            enabling_bluetooth = true;
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            // bluetooth is enabled, continue the connection process
            changeState(State.DISCONNECTED);

            // Initialize the BluetoothChatService to perform bluetooth connections
            // starts listening for connections here
            mService.start();

            findDevices();
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // return from the enable bluetooth thing
        if (requestCode == REQUEST_ENABLE_BT) {
            enabling_bluetooth = false;
            Log.v("debug", "Request Enable Bt");
            if (resultCode == RESULT_OK) {
                Log.v("bluetooth", "Bluetooth successfully enabled.");
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
                        changeState(State.INACTIVE);
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        makeShortToast("Bluetooth turning off - disconnecting PairIt");
                        disconnect();
                        break;
                    case BluetoothAdapter.STATE_ON:
                        makeShortToast("Bluetooth enabled");
                        if (!enabling_bluetooth) {
                            // don't step on the other listener's toes
                            findDevices();
                        }
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        break;
                }
            }
        }
    };

    // ----------------------------------------------------------------------------------
    // PAIRING
    //

    // Bluetooth is now enabled at this point
    private int mStackLevel = 0; // the number of fragments open
    private DialogFragment pairDialog = null;
    private void findDevices() {
        Log.v("Debug", "Find Devices");
        changeState(State.PAIRING);

        // show the dialog
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
        pairDialog = BluetoothDialogFragment.newInstance(mStackLevel);
        pairDialog.show(ft, "dialog");
    }

    // dismiss the dialog without pairing
    private void dismissDialog() {

    }

    // ----------------------------------------------------------------------------------
    // CONNECTED
    //

    public void connect (BluetoothDevice device) {
        mService.connect(device, true);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");

        changeState(State.CONNECTED);
        Log.v("manage", "Managing socket");
    }

    // ----------------------------------------------------------------------------------
    // PRIVATE METHODS
    //

    // display a quick toast to the user
    public void makeShortToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ----------------------------------------------------------------------------------
    // STATE STUFF
    //

    private State connection_status; // DO NOT SET THIS VARIABLE MANUALLY
    enum State {
        INACTIVE,
        DISCONNECTED,
        PAIRING,
        CONNECTING,
        CONNECTED
    }

    private State getState() {
        return connection_status;
    }
    // change the connection_status of the app
    private void changeState(State newState) {
        connection_status = newState;

        String text;
        switch (newState){
            case INACTIVE:
                text = "Please turn on Bluetooth";
                break;
            case DISCONNECTED:
                text = "Not Connected";
                break;
            case PAIRING:
                text = "Pairing";
                break;
            case CONNECTING:
                text = "Connecting";
                break;
            case CONNECTED:
                text = "Connected to " + mConnectedDeviceName;
                break;
            default:
                text = "BAD STATE";
        }
        ((TextView) findViewById(R.id.debug_text)).setText(text);
    }

    // ----------------------------------------------------------------------------------
    // Message Handler
    //

    /**
     * Sends a message.
     *
     * @param message A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mService.getState() != BluetoothService.STATE_CONNECTED) {
            makeShortToast("Connect before sending a message");
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            // mOutEditText.setText(mOutStringBuffer);
        }
    }

    public void send(View view) {
        sendMessage("Blah");
    }

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:
                            changeState(State.CONNECTED);
                            mConversationArrayAdapter.clear();
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            changeState(State.CONNECTING);
                            break;
                        case BluetoothService.STATE_LISTEN:
                        case BluetoothService.STATE_NONE:
                            changeState(State.DISCONNECTED);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    mConversationArrayAdapter.add("Me:  " + writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    mConversationArrayAdapter.add(mConnectedDeviceName + ":  " + readMessage);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    makeShortToast("Connected to " + mConnectedDeviceName);
                    break;
                case Constants.MESSAGE_TOAST:
                    makeShortToast(msg.getData().getString(Constants.TOAST));
                    break;
            }
        }
    };
}
