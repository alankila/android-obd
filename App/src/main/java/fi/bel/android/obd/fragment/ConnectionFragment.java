package fi.bel.android.obd.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import fi.bel.android.obd.R;

public class ConnectionFragment extends Fragment {
    public static BluetoothSocket SOCKET;

    /** Well-known serial SPP */
    protected static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    protected BluetoothManager btm;

    protected List<BluetoothDevice> devices = new ArrayList<>();

    protected ArrayAdapter<BluetoothDevice> deviceListAdapter;

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
            if (! btm.getAdapter().isDiscovering()) {
                btm.getAdapter().startDiscovery();
            }
            View view = inflater.inflate(R.layout.connectionview, null);
            ListView deviceList = (ListView) view.findViewById(R.id.connection_device_list);
            deviceList.setAdapter(deviceListAdapter);
            deviceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (btm.getAdapter().isDiscovering()) {
                        btm.getAdapter().cancelDiscovery();
                    }

                    BluetoothDevice device = devices.get(position);
                    try {
                        BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SPP);
                        socket.connect();
                        if (socket.isConnected()) {
                            SOCKET = socket;
                        }
                    }
                    catch (IOException ioe) {
                        Toast.makeText(getActivity(), "Failed to connect: " + ioe, Toast.LENGTH_LONG);
                    }
                }
            });
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
}
