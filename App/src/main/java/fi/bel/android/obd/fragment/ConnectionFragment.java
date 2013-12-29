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
import android.provider.Settings;
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
import java.util.ListIterator;

import fi.bel.android.obd.R;
import fi.bel.android.obd.thread.BluetoothRunnable;

public class ConnectionFragment extends Fragment
        implements AdapterView.OnItemClickListener, View.OnClickListener, BluetoothRunnable.Callback {
    public static final String ACTION_PHASE = "fi.bel.android.obd.PHASE";

    public static final String EXTRA_PHASE = "fi.bel.android.obd.PHASE";

    protected static final String TAG = ConnectionFragment.class.getSimpleName();

    protected List<BluetoothDevice> devices = new ArrayList<>();

    protected ListView deviceList;

    protected Button disconnectButton;

    protected TextView phaseText;

    protected ArrayAdapter<BluetoothDevice> deviceListAdapter;

    protected BluetoothRunnable bluetoothRunnable;

    protected Thread bluetoothThread;

    protected BluetoothDevice currentDevice;

    protected BluetoothRunnable.Phase phase;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        phase = BluetoothRunnable.Phase.DISCONNECTED;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
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
                convertView.setActivated(device.equals(currentDevice));
                return convertView;
            }
        };
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter.isEnabled()) {
            View view = inflater.inflate(R.layout.fragment_connection, null);

            Button bondButton = (Button) view.findViewById(R.id.connection_bond);
            bondButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                }
            });

            deviceList = (ListView) view.findViewById(R.id.connection_device_list);
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

        devices.clear();
        devices.addAll(BluetoothAdapter.getDefaultAdapter().getBondedDevices());

        updateUiState();
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
        currentDevice = devices.get(position);
        deviceListAdapter.notifyDataSetChanged();

        bluetoothRunnable = new BluetoothRunnable(currentDevice, new Handler(), this);
        bluetoothThread = new Thread(bluetoothRunnable);
        bluetoothThread.start();
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
        updateUiState();
    }

    /**
     * Update the UI state to reflect current state of the BT system.
     */
    private void updateUiState() {
        deviceList.setEnabled(phase == BluetoothRunnable.Phase.DISCONNECTED);
        if (deviceList.isEnabled()) {
            currentDevice = null;
            deviceListAdapter.notifyDataSetChanged();
        }
        disconnectButton.setEnabled(phase != BluetoothRunnable.Phase.DISCONNECTED);
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
        return phase == BluetoothRunnable.Phase.READY;
    }

    /**
     * Add transaction into the queue.
     *
     * @param transaction
     */
    public void sendCommand(BluetoothRunnable.Transaction transaction) {
        bluetoothRunnable.addTransaction(transaction);
    }

    @Override
    public void setPhase(BluetoothRunnable.Phase phase) {
        this.phase = phase;
        updateUiState();
    }
}
