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
import java.util.List;

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
            long time = intent.getLongExtra("time", 0);
            String code = intent.getStringExtra("code");
            float value = intent.getFloatExtra("value", 0);

            if (! data.contains(code)) {
                data.add(code);
            }
            for (View view : dataList.getTouchables()) {
                GraphView gv = (GraphView) view.findViewById(R.id.graph_item_graph);
                if (code.equals(gv.getCode())) {
                    gv.addPoint(time, value);
                }
            }
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
                String code = data.get(position);

                TextView title = (TextView) convertView.findViewById(R.id.graph_item_title);
                String titleText = getResources().getString(getResources().getIdentifier("PID" + code, "string", getActivity().getPackageName()));
                titleText += " / " + OBD.unit(code);
                title.setText(titleText);

                GraphView graph = (GraphView) convertView.findViewById(R.id.graph_item_graph);
                graph.setCode(code);
                graph.clearPoints();
                Cursor cursor = db.rawQuery("SELECT timestamp, value FROM data WHERE code = ? ORDER BY rowid", new String[] { code });
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
        data.clear();
        getActivity().registerReceiver(newData, new IntentFilter(DataService.NEW_DATA));
    }

    @Override
    public void onPause() {
        super.onPause();
        getActivity().unregisterReceiver(newData);
        db.close();
    }
}
