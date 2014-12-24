package fi.bel.android.obd.fragment;

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import fi.bel.android.obd.ContainerActivity;
import fi.bel.android.obd.R;
import fi.bel.android.obd.service.DataService;
import fi.bel.android.obd.util.PID;
import fi.bel.android.obd.view.GraphView;

public class GraphFragment extends Fragment {
    protected static final String TAG = GraphFragment.class.getSimpleName();

    protected SQLiteDatabase db;

    protected List<PID.Sensor> data = new ArrayList<>();

    protected Set<Integer> codesAdded = new HashSet<>();

    protected Set<GraphView> visibleViews = new HashSet<>();

    protected ListView dataList;

    protected ArrayAdapter dataListAdapter;

    protected final BroadcastReceiver newData = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long timestamp = intent.getLongExtra("timestamp", 0);
            int code = intent.getIntExtra("pid", 0);
            String response = intent.getStringExtra("value");

            /* We received data for this PID. Do we have a sensor in our list for it? */
            if (! codesAdded.contains(code)) {
                for (PID pid : ContainerActivity.BLUETOOTH_RUNNABLE.pid()) {
                    if (pid.getCode() == code) {
                        for (int i = 0; i < pid.values(); i += 1) {
                            if (pid.unit(i) != null) {
                                data.add(new PID.Sensor(pid, i));
                            }
                        }
                        Collections.sort(data);
                        dataListAdapter.notifyDataSetChanged();
                    }
                }
                codesAdded.add(code);
            }

            for (GraphView gv : visibleViews) {
                if (code == gv.getSensor().getPid().getCode()) {
                    float value = gv.getSensor().getPid().floatValue(response, gv.getSensor().getIndex());
                    gv.addPoint(timestamp, value);
                }
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_graph, null);
        dataListAdapter = new ArrayAdapter<PID.Sensor>(getActivity(), 0, 0, data) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getActivity().getLayoutInflater().inflate(R.layout.fragment_graph_item, null);
                }
                PID.Sensor sensor = data.get(position);

                TextView title = (TextView) convertView.findViewById(R.id.graph_item_title);
                String titleText = String.format(
                        "%s (%s)",
                        sensor.getPid().key(getActivity(), sensor.getIndex()),
                        sensor.getPid().unit(sensor.getIndex())
                );
                title.setText(titleText);

                GraphView graph = (GraphView) convertView.findViewById(R.id.graph_item_graph);
                graph.setSensor(sensor);
                graph.clearPoints();
                try (Cursor cursor = db.rawQuery(
                        "SELECT timestamp, value FROM data WHERE pid = ? ORDER BY rowid",
                        new String[] { String.valueOf(sensor.getPid().getCode()) }
                )) {
                    while (cursor.moveToNext()) {
                        long timestamp = cursor.getLong(0);
                        String response = cursor.getString(1);
                        float value = sensor.getPid().floatValue(response, sensor.getIndex());
                        graph.addPoint(timestamp, value);
                    }
                }
                visibleViews.add(graph);

                return convertView;
            }
        };
        dataList = (ListView) view.findViewById(R.id.data);
        dataList.setAdapter(dataListAdapter);
        dataList.setRecyclerListener(new AbsListView.RecyclerListener() {
            @Override
            public void onMovedToScrapHeap(View view) {
                GraphView graph = (GraphView) view.findViewById(R.id.graph_item_graph);
                visibleViews.remove(graph);
            }
        });
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        db = DataService.openDatabase(getActivity());
        data.clear();
        codesAdded.clear();
        visibleViews.clear();
        getActivity().registerReceiver(newData, new IntentFilter(DataService.NEW_DATA));
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(newData);
        db.close();
    }
}
