package fi.bel.android.obd.fragment;

import android.app.Fragment;
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

public class DataFragment extends Fragment {
    protected static final String TAG = DataFragment.class.getSimpleName();

    protected SQLiteDatabase db;

    protected ConnectionFragment connectionFragment;

    protected Handler handler;

    protected final Runnable refresh = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 5000);
        }
    };

    protected List<String> data = new ArrayList<>();

    protected Map<String, Float> dataMap = new HashMap<>();

    protected ListView dataList;

    protected ArrayAdapter dataListAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        connectionFragment = (ConnectionFragment) ContainerActivity.FRAGMENTS.get(0);
        handler = new Handler();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_data, null);
        dataListAdapter = new ArrayAdapter<String>(getActivity(), 0, 0, data) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getActivity().getLayoutInflater().inflate(R.layout.fragment_data_item, null);
                }
                String pidKey = data.get(position);
                float pidValue = dataMap.get(pidKey);

                TextView key = (TextView) convertView.findViewById(R.id.data_item_key);
                key.setText(pidKey);

                TextView value = (TextView) convertView.findViewById(R.id.data_item_value);
                value.setText(String.format("%.2f", pidValue));

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
        handler.post(refresh);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(refresh);
        db.close();
    }

    protected void refresh() {
        data.clear();
        for (int i = 0; i < 256; i ++) {
            String pid = String.format("%02x", i);
            if (! connectionFragment.pidSupported(pid)) {
                continue;
            }

            Cursor cursor = db.rawQuery("SELECT value FROM data WHERE rowid = (SELECT max(rowid) FROM data WHERE pid = ?)", new String[] { pid });
            if (cursor.moveToFirst()) {
                float dbValue = cursor.getFloat(0);
                data.add(pid);
                dataMap.put(pid, dbValue);
            }
            cursor.close();
        }

        dataListAdapter.notifyDataSetChanged();
    }
}
