package fi.bel.android.obd.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import fi.bel.android.obd.ContainerActivity;
import fi.bel.android.obd.R;
import fi.bel.android.obd.thread.BluetoothRunnable;

public class ConnectionFragment extends Fragment
        implements AdapterView.OnItemClickListener, View.OnClickListener {
    protected static final String TAG = ConnectionFragment.class.getSimpleName();

    protected final BroadcastReceiver phaseReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateUiState();
        }
    };

    protected List<BluetoothDevice> devices = new ArrayList<>();

    protected ListView deviceList;

    protected Button disconnectButton;

    protected TextView phaseText;

    protected ArrayAdapter<BluetoothDevice> deviceListAdapter;

    protected Thread bluetoothThread;

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
                convertView.setActivated(device.equals(ContainerActivity.BLUETOOTH_RUNNABLE.getDevice()));
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
        Collections.sort(devices, new Comparator<BluetoothDevice>() {
            @Override
            public int compare(BluetoothDevice lhs, BluetoothDevice rhs) {
                return lhs.getName().compareTo(rhs.getName());
            }
        });
        deviceListAdapter.notifyDataSetChanged();
        updateUiState();
        getActivity().getApplicationContext().registerReceiver(phaseReceiver, new IntentFilter(BluetoothRunnable.ACTION_PHASE));
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().getApplicationContext().unregisterReceiver(phaseReceiver);
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
        ContainerActivity.BLUETOOTH_RUNNABLE.setDevice(devices.get(position));
        deviceListAdapter.notifyDataSetChanged();

        bluetoothThread = new Thread(ContainerActivity.BLUETOOTH_RUNNABLE);
        bluetoothThread.start();
    }

    /**
     * Disconnect from bluetooth device
     *
     * @param v ignored
     */
    @Override
    public void onClick(View v) {
        /* This should be fast to do, so we just wait on the main thread. */
        ContainerActivity.BLUETOOTH_RUNNABLE.terminate();
        while (bluetoothThread.isAlive()) {
            bluetoothThread.interrupt();
            try {
                bluetoothThread.join(10);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /**
     * Update the UI state to reflect current state of the BT system.
     */
    private void updateUiState() {
        deviceList.setEnabled(ContainerActivity.BLUETOOTH_RUNNABLE.getPhase() == BluetoothRunnable.Phase.DISCONNECTED);
        if (deviceList.isEnabled()) {
            ContainerActivity.BLUETOOTH_RUNNABLE.setDevice(null);
            bluetoothThread = null;
            deviceListAdapter.notifyDataSetChanged();
        }
        disconnectButton.setEnabled(ContainerActivity.BLUETOOTH_RUNNABLE.getPhase() != BluetoothRunnable.Phase.DISCONNECTED);
        try {
            String phase = String.valueOf(ContainerActivity.BLUETOOTH_RUNNABLE.getPhase());
            phaseText.setText((int) R.string.class.getField(phase).get(null));
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
