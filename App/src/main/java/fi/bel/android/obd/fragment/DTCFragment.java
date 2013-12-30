package fi.bel.android.obd.fragment;

import android.app.Fragment;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import fi.bel.android.obd.ContainerActivity;
import fi.bel.android.obd.R;
import fi.bel.android.obd.thread.BluetoothRunnable;
import fi.bel.android.obd.util.OBD;

public class DTCFragment extends Fragment {
    protected SearchView search;

    protected List<String> fault = new ArrayList<>();

    protected ArrayAdapter<String> faultListAdapter;

    protected ListView faultList;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dtc, null);
        search = (SearchView) view.findViewById(R.id.dtc_search);
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return onQueryTextChange(query);
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                refresh();
                return true;
            }

        });
        faultListAdapter = new ArrayAdapter<String>(getActivity(), 0, 0, fault) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getActivity().getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);
                }
                TextView tv1 = (TextView) convertView.findViewById(android.R.id.text1);
                tv1.setText(fault.get(position));
                return convertView;
            }
        };

        faultList = (ListView) view.findViewById(R.id.dtc_list);
        faultList.setAdapter(faultListAdapter);
        refresh();
        return view;
    }

    private void refresh() {
        String keywords = search.getQuery().toString().toLowerCase();
        Map<String, List<String>> faultHeaderMap = OBD.getFaultHeaderMap(getActivity());
        Map<String, String> faultMap = OBD.getFaultMap(getActivity());

        fault.clear();
        for (String s1 : faultHeaderMap.keySet()) {
            boolean s1Added = false;
            for (String s2 : faultHeaderMap.get(s1)) {
                String v2 = faultMap.get(s2);
                if (s2.toLowerCase().contains(keywords) || v2.toLowerCase().contains(keywords)) {
                    if (! s1Added) {
                        fault.add(s1);
                        s1Added = true;
                    }
                    fault.add(s2 + " " + v2);
                }
            }
        }
        faultListAdapter.notifyDataSetChanged();
    }
}
