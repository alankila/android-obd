package fi.bel.android.obd.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import fi.bel.android.obd.R;
import fi.bel.android.obd.thread.BluetoothRunnable;

public class ConnectionFragment extends Fragment implements AdapterView.OnItemClickListener, View.OnClickListener {
    protected enum Phase {
        DISCONNECTED, CONNECTING, INITIALIZING, READY
    }

    public static final String ACTION_PHASE = "fi.bel.android.obd.PHASE";

    public static final String EXTRA_PHASE = "fi.bel.android.obd.PHASE";

    protected BluetoothManager btm;

    protected List<BluetoothDevice> devices = new ArrayList<>();

    protected ListView deviceList;

    protected Button disconnectButton;

    protected TextView phaseText;

    protected ArrayAdapter<BluetoothDevice> deviceListAdapter;

    protected BluetoothRunnable bluetoothRunnable;

    protected Thread bluetoothThread;

    protected Phase phase;

    private final BroadcastReceiver deviceFoundReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            devices.add((BluetoothDevice) intent.getExtras().get(BluetoothDevice.EXTRA_DEVICE));
            deviceListAdapter.notifyDataSetChanged();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        btm = (BluetoothManager) getActivity().getSystemService(Context.BLUETOOTH_SERVICE);
        phase = Phase.DISCONNECTED;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        getActivity().registerReceiver(deviceFoundReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));

        deviceListAdapter = new ArrayAdapter<>(
                getActivity(),
                android.R.layout.simple_list_item_activated_1,
                android.R.id.text1,
                devices
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (btm.getAdapter().isEnabled()) {
            if (bluetoothThread == null && !btm.getAdapter().isDiscovering()) {
                btm.getAdapter().startDiscovery();
            }
            View view = inflater.inflate(R.layout.connectionview, null);
            deviceList = (ListView) view.findViewById(R.id.connection_device_list);
            deviceList.setEnabled(bluetoothThread == null);
            deviceList.setAdapter(deviceListAdapter);
            deviceList.setOnItemClickListener(this);

            disconnectButton = (Button) view.findViewById(R.id.connection_disconnect);
            disconnectButton.setOnClickListener(this);

            phaseText = (TextView) view.findViewById(R.id.connection_phase);
            return view;
        } else {
            return inflater.inflate(R.layout.connectionview_no_bt, null);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        getActivity().unregisterReceiver(deviceFoundReceiver);
    }

    /**
     * Connect to bluetooth device
     *
     * @param parent ignored
     * @param view ignored
     * @param position Which device from deviceList was clicked on
     * @param id ignored
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (btm.getAdapter().isDiscovering()) {
            btm.getAdapter().cancelDiscovery();
        }

        phase = Phase.CONNECTING;

        BluetoothDevice device = devices.get(position);
        bluetoothRunnable = new BluetoothRunnable(device, new Handler());
        bluetoothThread = new Thread(bluetoothRunnable);
        bluetoothThread.start();
        updateUiState();

        for (String command : new String[] { "ATZ", "ATE0" }) {
            bluetoothRunnable.addTransaction(new BluetoothRunnable.Transaction(command) {
                @Override
                 protected void success(String response) {
                    if (getCommand().equals("ATZ")) {
                        phase = Phase.INITIALIZING;
                    }

                    if (getCommand().equals("ATE0")) {
                        phase = Phase.READY;
                    }

                    updateUiState();
                }

                protected void failed() {
                    onClick(null);
                }
            });
        }
    }

    /**
     * Disconnect from bluetooth device
     *
     * @param v ignored
     */
    @Override
    public void onClick(View v) {
        /* This should be fast, so we just wait on the main thread. */
        while (bluetoothThread.isAlive()) {
            bluetoothThread.interrupt();
            try {
                bluetoothThread.join(10);
            } catch (InterruptedException e) {
                break;
            }
        }
        bluetoothThread = null;
        bluetoothRunnable = null;
        phase = Phase.DISCONNECTED;
        updateUiState();
    }

    /**
     * Update the UI state to reflect current state of the BT system.
     */
    private void updateUiState() {
        deviceList.setEnabled(bluetoothThread == null);
        disconnectButton.setEnabled(bluetoothThread != null);
        phaseText.setText(phase.toString());
        Activity activity = getActivity();
        if (activity != null) {
            activity.sendBroadcast(new Intent(ACTION_PHASE).putExtra(EXTRA_PHASE, phase));
        }
    }

    public boolean canSendCommand() {
        return phase == Phase.READY;
    }

    /**
     * Add transaction into the queue.
     *
     * @param transaction
     */
    public void sendCommand(BluetoothRunnable.Transaction transaction) {
        bluetoothRunnable.addTransaction(transaction);
    }
}
