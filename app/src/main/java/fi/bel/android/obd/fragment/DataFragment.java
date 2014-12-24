package fi.bel.android.obd.fragment;

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fi.bel.android.obd.ContainerActivity;
import fi.bel.android.obd.R;
import fi.bel.android.obd.service.DataService;
import fi.bel.android.obd.util.PID;
import fi.bel.android.obd.view.GraphView;

public class DataFragment extends Fragment {
    protected static final String TAG = DataFragment.class.getSimpleName();

    protected SQLiteDatabase db;

    protected List<PID.Sensor> data = new ArrayList<>();

    protected Set<Integer> codesAdded = new HashSet<>();

    protected Map<Integer, String> dataMap = new HashMap<>();

    protected ListView dataList;

    protected ArrayAdapter dataListAdapter;

    /* This is a pretty wasteful update because we update every PID in a separate intent,
     * but we can only notify the entire adapter about something being different. So there
     * will be a whole bunch of these notifications. What is worse, it takes a significant
     * time to get a data value across OBD and BT, so the individual value updates can be
     * seen on screen as they occur. */
    protected final BroadcastReceiver newData = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int code = intent.getIntExtra("pid", 0);
            String response = intent.getStringExtra("value");

            /* We received data for this PID. Do we have a sensor in our list for it? */
            if (! codesAdded.contains(code)) {
                for (PID pid : ContainerActivity.BLUETOOTH_RUNNABLE.pid()) {
                    if (pid.getCode() == code) {
                        for (int i = 0; i < pid.values(); i += 1) {
                            data.add(new PID.Sensor(pid, i));
                        }
                        Collections.sort(data);
                    }
                }
                codesAdded.add(code);
            }

            dataMap.put(code, response);
            dataListAdapter.notifyDataSetChanged();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_data, null);
        dataListAdapter = new ArrayAdapter<PID.Sensor>(getActivity(), 0, 0, data) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getActivity().getLayoutInflater().inflate(R.layout.fragment_data_item, null);
                }
                PID.Sensor sensor = data.get(position);
                String response = dataMap.get(sensor.getPid().getCode());

                TextView key = (TextView) convertView.findViewById(R.id.data_item_key);
                key.setText(sensor.getPid().key(getActivity(), sensor.getIndex()));

                TextView value = (TextView) convertView.findViewById(R.id.data_item_value);
                value.setText(sensor.getPid().stringValue(getActivity(), response, sensor.getIndex()));

                return convertView;
            }
        };
        dataList = (ListView) view.findViewById(R.id.data);
        dataList.setAdapter(dataListAdapter);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        db = DataService.openDatabase(getActivity());
        data.clear();
        codesAdded.clear();
        getActivity().registerReceiver(newData, new IntentFilter(DataService.NEW_DATA));
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(newData);
        db.close();
    }
}
