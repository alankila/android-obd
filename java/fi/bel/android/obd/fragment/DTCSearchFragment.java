package fi.bel.android.obd.fragment;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import fi.bel.android.obd.R;
import fi.bel.android.obd.util.OBD;

public class DTCSearchFragment extends Fragment {
    protected SearchView search;

    protected List<String[]> fault = new ArrayList<>();

    protected ArrayAdapter<String[]> faultListAdapter;

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
        faultListAdapter = new ArrayAdapter<String[]>(getActivity(), 0, 0, fault) {
            @Override
            public int getViewTypeCount() {
                return 2;
            }

            @Override
            public int getItemViewType(int position) {
                return getItem(position)[0] == null ? 0 : 1;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                boolean isHeader = getItemViewType(position) == 0;
                if (convertView == null) {
                    convertView = getActivity().getLayoutInflater().inflate(
                            isHeader ? R.layout.fragment_dtc_header : R.layout.fragment_dtc_item, null
                    );
                }
                String[] text = getItem(position);

                if (! isHeader) {
                    TextView shortView = (TextView) convertView.findViewById(R.id.dtc_short);
                    shortView.setText(text[0]);
                }

                TextView longView = (TextView) convertView.findViewById(R.id.dtc_long);
                longView.setText(text[1]);
                return convertView;
            }
        };

        faultList = (ListView) view.findViewById(R.id.dtc_list);
        faultList.setAdapter(faultListAdapter);
        faultList.setFastScrollEnabled(true);
        refresh();
        return view;
    }

    private void refresh() {
        String keywords = search.getQuery().toString().toLowerCase();
        Map<String, List<String>> faultHeaderMap = OBD.getFaultHeaderMap(getActivity());
        Map<String, String> faultMap = OBD.getFaultMap(getActivity());

        fault.clear();
        for (String header : faultHeaderMap.keySet()) {
            boolean s1Added = false;
            for (String code : faultHeaderMap.get(header)) {
                String description = faultMap.get(code);
                if (code.toLowerCase().contains(keywords) || description.toLowerCase().contains(keywords)) {
                    if (! s1Added) {
                        fault.add(new String[] { null, header });
                        s1Added = true;
                    }
                    fault.add(new String[] { code, description });
                }
            }
        }
        faultListAdapter.notifyDataSetChanged();
    }
}
