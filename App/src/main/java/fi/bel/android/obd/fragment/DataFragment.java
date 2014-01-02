package fi.bel.android.obd.fragment;

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.bel.android.obd.ContainerActivity;
import fi.bel.android.obd.R;
import fi.bel.android.obd.service.DataService;
import fi.bel.android.obd.thread.BluetoothRunnable;
import fi.bel.android.obd.util.OBD;

public class DataFragment extends Fragment {
    protected static final String TAG = DataFragment.class.getSimpleName();

    protected SQLiteDatabase db;

    protected List<String> data = new ArrayList<>();

    protected Map<String, Float> dataMap = new HashMap<>();

    protected ListView dataList;

    protected ArrayAdapter dataListAdapter;

    protected final BroadcastReceiver newData = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            data.clear();
            for (String pid : ContainerActivity.BLUETOOTH_RUNNABLE.pid()) {
                if (OBD.unit(pid) == null) {
                    continue;
                }
                if (pid.compareTo("14") >= 0 && pid.compareTo("1b") <= 0) {
                    handle(pid + "_1");
                    handle(pid + "_2");
                } else {
                    handle(pid);
                }
            }
            dataListAdapter.notifyDataSetChanged();
        }

        private void handle(String pid) {
            Cursor cursor = db.rawQuery("SELECT value FROM data WHERE rowid = (SELECT max(rowid) FROM data WHERE pid = ?)", new String[] { pid });
            if (cursor.moveToFirst()) {
                float dbValue = cursor.getFloat(0);
                data.add(pid);
                dataMap.put(pid, dbValue);
            }
            cursor.close();
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_data, null);
        dataListAdapter = new ArrayAdapter<String>(getActivity(), 0, 0, data) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getActivity().getLayoutInflater().inflate(R.layout.fragment_data_item, null);
                }
                String pid = data.get(position);
                float pidValue = dataMap.get(pid);

                TextView key = (TextView) convertView.findViewById(R.id.data_item_key);
                try {
                    key.setText((int) R.string.class.getField("PID" + pid).get(null));
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }

                TextView value = (TextView) convertView.findViewById(R.id.data_item_value);
                value.setText(String.format("%.2f %s", pidValue, OBD.unit(pid)));

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
