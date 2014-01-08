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
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.bel.android.obd.ContainerActivity;
import fi.bel.android.obd.R;
import fi.bel.android.obd.service.DataService;
import fi.bel.android.obd.util.OBD;
import fi.bel.android.obd.util.PID;

public class DataFragment extends Fragment {
    protected static final String TAG = DataFragment.class.getSimpleName();

    protected SQLiteDatabase db;

    protected List<PID.Sensor> data = new ArrayList<>();

    protected Map<PID, String> dataMap = new HashMap<>();

    protected ListView dataList;

    protected ArrayAdapter dataListAdapter;

    protected final BroadcastReceiver newData = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            data.clear();
            for (PID pid : ContainerActivity.BLUETOOTH_RUNNABLE.pid()) {
                try (Cursor cursor = db.rawQuery(
                        "SELECT value FROM data WHERE rowid = (SELECT max(rowid) FROM data WHERE code = ?)",
                        new String[] { String.valueOf(pid.getCode()) }
                )) {
                    if (!cursor.moveToFirst()) {
                        continue;
                    }
                    String dbValue = cursor.getString(0);
                    dataMap.put(pid, dbValue);
                    for (int i = 0; i < pid.values(); i += 1) {
                        data.add(new PID.Sensor(pid, i));
                    }
                };
            }
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
                String response = dataMap.get(sensor.getPid());

                TextView key = (TextView) convertView.findViewById(R.id.data_item_key);
                key.setText(sensor.getPid().key(getActivity())[sensor.getIndex()]);

                TextView value = (TextView) convertView.findViewById(R.id.data_item_value);
                value.setText(sensor.getPid().stringValue(getActivity(), response)[sensor.getIndex()]);

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
        getActivity().registerReceiver(newData, new IntentFilter(DataService.NEW_DATA));
        newData.onReceive(null, null);
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(newData);
        db.close();
    }
}
