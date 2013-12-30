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

    protected ListAdapter faultListAdapter;

    protected ListView faultList;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Map<String, List<String>> faultHeaderMap = OBD.getFaultHeaderMap(getActivity());
        Map<String, String> faultMap = OBD.getFaultMap(getActivity());

        for (String s1 : faultHeaderMap.keySet()) {
            fault.add(s1);
            for (String s2 : faultHeaderMap.get(s1)) {
                fault.add(s2 + " " + faultMap.get(s2));
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dtc, null);
        search = (SearchView) view.findViewById((R.id.dtc_search);
        faultList = (ListView) view.findViewById(R.id.dtc_list);
        faultList.setAdapter(new ArrayAdapter(getActivity(), 0, 0, fault) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getActivity().getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);
                }
                TextView tv1 = (TextView) convertView.findViewById(android.R.id.text1);
                tv1.setText(fault.get(position));
            }
        });
        return view;
    }
}
