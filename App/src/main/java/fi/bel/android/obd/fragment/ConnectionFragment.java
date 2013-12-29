package fi.bel.android.obd.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
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

    protected static final String TAG = ConnectionFragment.class.getSimpleName();

    protected List<BluetoothDevice> devices = new ArrayList<>();

    protected ProgressBar scanIndicator;

    protected ListView deviceList;

    protected Button disconnectButton;

    protected TextView phaseText;

    protected ArrayAdapter<BluetoothDevice> deviceListAdapter;

    protected BluetoothRunnable bluetoothRunnable;

    protected Thread bluetoothThread;

    protected Phase phase;

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            scanIndicator.setVisibility(
                    intent.getAction().equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                            ? View.VISIBLE : View.INVISIBLE
            );
        }
    };

    private final BroadcastReceiver deviceFoundReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothDevice device = (BluetoothDevice) intent.getExtras().get(BluetoothDevice.EXTRA_DEVICE);
            Log.i(TAG, "Found device: " + device.getName() + ", " + device.getAddress());
            devices.add(device);
            deviceListAdapter.notifyDataSetChanged();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        phase = Phase.DISCONNECTED;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        IntentFilter scanFilter = new IntentFilter();
        scanFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        scanFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        getActivity().registerReceiver(scanReceiver, scanFilter);
        getActivity().registerReceiver(deviceFoundReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));

        deviceListAdapter = new ArrayAdapter<BluetoothDevice>(getActivity(), 0, 0, devices) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getActivity().getLayoutInflater().inflate(android.R.layout.simple_list_item_activated_2, null);
                }
                BluetoothDevice device = devices.get(position);
                TextView tv1 = (TextView) convertView.findViewById(android.R.id.text1);
                tv1.setText(device.getName());
                TextView tv2 = (TextView) convertView.findViewById(android.R.id.text2);
                tv2.setText(device.getAddress());
                return convertView;
            }
        };
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter.isEnabled()) {
            View view = inflater.inflate(R.layout.fragment_connection, null);
            scanIndicator = (ProgressBar) view.findViewById(R.id.connection_scan_indicator);

            deviceList = (ListView) view.findViewById(R.id.connection_device_list);
            deviceList.setEnabled(bluetoothThread == null);
            deviceList.setAdapter(deviceListAdapter);
            deviceList.setOnItemClickListener(this);

            disconnectButton = (Button) view.findViewById(R.id.connection_disconnect);
            disconnectButton.setOnClickListener(this);

            phaseText = (TextView) view.findViewById(R.id.connection_phase);
            return view;
        } else {
            return inflater.inflate(R.layout.fragment_connection_no_bt, null);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothThread == null && !adapter.isDiscovering()) {
            devices.clear();
            deviceListAdapter.notifyDataSetChanged();
            adapter.startDiscovery();
        }

        updateUiState();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        getActivity().unregisterReceiver(deviceFoundReceiver);
        getActivity().unregisterReceiver(scanReceiver);
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
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter.isDiscovering()) {
            adapter.cancelDiscovery();
        }

        phase = Phase.CONNECTING;

        BluetoothDevice device = devices.get(position);
        bluetoothRunnable = new BluetoothRunnable(device, new Handler());
        bluetoothThread = new Thread(bluetoothRunnable);
        bluetoothThread.start();
        updateUiState();

        for (String command : new String[] { "ATSP0", "ATZ", "ATE0" }) {
            bluetoothRunnable.addTransaction(new BluetoothRunnable.Transaction(command) {
                @Override
                 protected void success(String response) {
                    if (getCommand().equals("ATSP0")) {
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

    /**
     * Is application ready to interact with reader?
     *
     * @return
     */
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
