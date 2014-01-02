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
import fi.bel.android.obd.view.GraphView;

public class GraphFragment extends Fragment {
    protected static final String TAG = GraphFragment.class.getSimpleName();

    protected SQLiteDatabase db;

    protected List<String> data = new ArrayList<>();

    protected ListView dataList;

    protected ArrayAdapter dataListAdapter;

    protected final BroadcastReceiver newData = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            /* FIXME: can't afford to do updates like this. */
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
            data.add(pid);
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_graph, null);
        dataListAdapter = new ArrayAdapter<String>(getActivity(), 0, 0, data) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getActivity().getLayoutInflater().inflate(R.layout.fragment_graph_item, null);
                }
                String pid = data.get(position);

                try {
                    TextView title = (TextView) convertView.findViewById(R.id.graph_item_title);
                    String titleText = getResources().getString((int) R.string.class.getField("PID" + pid).get(null));
                    titleText += " / " + OBD.unit(pid);
                    title.setText(titleText);
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }

                GraphView graph = (GraphView) convertView.findViewById(R.id.graph_item_graph);
                graph.clear();
                Cursor cursor = db.rawQuery("SELECT timestamp, value FROM data WHERE pid = ? ORDER BY rowid", new String[] { pid });
                while (cursor.moveToNext()) {
                    graph.addPoint(cursor.getLong(0), cursor.getFloat(1));
                }

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
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(newData);
        db.close();
    }
}
