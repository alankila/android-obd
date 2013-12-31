package fi.bel.android.obd.fragment;

import android.app.Fragment;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.bel.android.obd.ContainerActivity;
import fi.bel.android.obd.R;
import fi.bel.android.obd.thread.BluetoothRunnable;
import fi.bel.android.obd.util.OBD;

public class FaultFragment extends Fragment {
    protected static final String TAG = FaultFragment.class.getSimpleName();

    protected enum SelfcheckTypes {
        MIL,                    /* A7 */
        FAULT_CODES,            /* A6-A0 */

        MISFIRE,                /* B0/B4 */
        FUEL_SYSTEM,            /* B1/B5 */
        COMPONENTS,             /* B2/B6 */
        SPARK_COMPRESSION,      /* B3 */

        /* Compression engines */
        CATALYST,               /* C0/D0 */
        HEATED_CATALYST,        /* C1/D1 */
        EVAPORATIVE_SYSTEM,     /* C2/D2 */
        SECONDARY_AIR_SYSTEM,   /* C3/D3 */
        AC_REFRIDGERANT,        /* C4/D4 */
        OXYGEN_SENSOR,          /* C5/D5 */
        OXYGEN_SENSOR_HEATER,   /* C6/D6 */
        EGR_SYSTEM,             /* C7/D7 */

        /* Spark engines */
        NMHC_CATALYST,          /* C0/D0 */
        NOx_SCR_MONITOR,        /* C1/D1 */
        BOOST_PRESSURE,         /* C3/D3 */
        EXHAUST_GAS_SENSOR,     /* C5/D5 */
        PM_FILTER_MONITORING,   /* C6/D6 */
        EGR_VVT_SYSTEM          /* C7/D7 */
    }

    protected ConnectionFragment connectionFragment;

    protected List<SelfcheckTypes> selfcheck = new ArrayList<>();

    protected ArrayAdapter<SelfcheckTypes> selfcheckListAdapter;

    protected ListView selfcheckList;

    protected ArrayAdapter<String> detectedListAdapter;

    protected List<String> detected = new ArrayList<>();

    protected ListView detectedList;

    protected Button clearButton;

    protected int selfcheckStatus;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        connectionFragment = (ConnectionFragment) ContainerActivity.FRAGMENTS.get(0);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_fault, null);
        selfcheckListAdapter = new ArrayAdapter<SelfcheckTypes>(getActivity(), 0, 0, selfcheck) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getActivity().getLayoutInflater().inflate(R.layout.fragment_fault_item, null);
                }
                SelfcheckTypes selfcheckType = selfcheck.get(position);

                TextView name = (TextView) convertView.findViewById(R.id.fault_item_name);
                name.setText(selfcheckType.toString());

                TextView state = (TextView) convertView.findViewById(R.id.fault_item_state);
                switch (selfcheckType) {
                    case MIL:
                        state.setText((selfcheckStatus & 0x80000000) != 0 ? "On" : "Off");
                        break;

                    case FAULT_CODES:
                        state.setText(String.valueOf((selfcheckStatus >> 24) & 0x7f));
                        break;

                    case MISFIRE:
                        state.setText((selfcheckStatus & 0x10000) != 0 ? "Not ready" : "Ready");
                        break;

                    case FUEL_SYSTEM:
                        state.setText((selfcheckStatus & 0x20000) != 0 ? "Not ready" : "Ready");
                        break;

                    case COMPONENTS:
                        state.setText((selfcheckStatus & 0x40000) != 0 ? "Not ready" : "Ready");
                        break;

                    /* Diesel */
                    case CATALYST:
                        state.setText((selfcheckStatus & 0x01) != 0 ? "Not ready" : "Ready");
                        break;

                    case HEATED_CATALYST:
                        state.setText((selfcheckStatus & 0x02) != 0 ? "Not ready" : "Ready");
                        break;

                    case EVAPORATIVE_SYSTEM:
                        state.setText((selfcheckStatus & 0x04) != 0 ? "Not ready" : "Ready");
                        break;

                    case SECONDARY_AIR_SYSTEM:
                        state.setText((selfcheckStatus & 0x08) != 0 ? "Not ready" : "Ready");
                        break;

                    case AC_REFRIDGERANT:
                        state.setText((selfcheckStatus & 0x10) != 0 ? "Not ready" : "Ready");
                        break;

                    case OXYGEN_SENSOR:
                        state.setText((selfcheckStatus & 0x20) != 0 ? "Not ready" : "Ready");
                        break;

                    case OXYGEN_SENSOR_HEATER:
                        state.setText((selfcheckStatus & 0x40) != 0 ? "Not ready" : "Ready");
                        break;

                    case EGR_SYSTEM:
                        state.setText((selfcheckStatus & 0x80) != 0 ? "Not ready" : "Ready");
                        break;

                    /* Spark */
                    case NMHC_CATALYST:
                        state.setText((selfcheckStatus & 0x01) != 0 ? "Not ready" : "Ready");
                        break;

                    case NOx_SCR_MONITOR:
                        state.setText((selfcheckStatus & 0x02) != 0 ? "Not ready" : "Ready");
                        break;

                    case BOOST_PRESSURE:
                        state.setText((selfcheckStatus & 0x08) != 0 ? "Not ready" : "Ready");
                        break;

                    case EXHAUST_GAS_SENSOR:
                        state.setText((selfcheckStatus & 0x20) != 0 ? "Not ready" : "Ready");
                        break;

                    case PM_FILTER_MONITORING:
                        state.setText((selfcheckStatus & 0x40) != 0 ? "Not ready" : "Ready");
                        break;

                    case EGR_VVT_SYSTEM:
                        state.setText((selfcheckStatus & 0x80) != 0 ? "Not ready" : "Ready");
                        break;
                }

                return convertView;
            }
        };
        selfcheckList = (ListView) view.findViewById(R.id.fault_selfcheck);
        selfcheckList.setAdapter(selfcheckListAdapter);

        detectedListAdapter = new ArrayAdapter<String>(getActivity(), 0, 0, detected) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getActivity().getLayoutInflater().inflate(android.R.layout.simple_list_item_2, null);
                }
                String faultCode = detected.get(position);

                TextView tv1 = (TextView) convertView.findViewById(android.R.id.text1);
                tv1.setText(faultCode);

                TextView tv2 = (TextView) convertView.findViewById(android.R.id.text2);
                Map<String, String> faultMap = OBD.getFaultMap(getContext());
                tv2.setText(faultMap.get(faultCode));

                return convertView;
            }
        };
        detectedList = (ListView) view.findViewById(R.id.fault_detected);
        detectedList.setAdapter(detectedListAdapter);

        clearButton = (Button) view.findViewById(R.id.fault_clear);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectionFragment.sendCommand(new BluetoothRunnable.Transaction("04") {
                    @Override
                    protected void success(String response) {
                        refresh();
                    }
                });

            }
        });
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    protected void refresh() {
        boolean enabled = true;
        enabled &= connectionFragment.canSendCommand();
        clearButton.setEnabled(enabled);

        selfcheckStatus = 0;
        selfcheck.clear();
        selfcheckListAdapter.clear();
        if (enabled && connectionFragment.pidSupported("01")) {
            connectionFragment.sendCommand(new BluetoothRunnable.Transaction("01 01 1") {
                @Override
                protected void success(String response) {
                    selfcheckStatus = (int) Long.parseLong(response.substring(4), 16);

                    selfcheck.add(SelfcheckTypes.MIL);
                    selfcheck.add(SelfcheckTypes.FAULT_CODES);
                    if ((selfcheckStatus & 0x100000) != 0) {
                        selfcheck.add(SelfcheckTypes.MISFIRE);
                    }
                    if ((selfcheckStatus & 0x200000) != 0) {
                        selfcheck.add(SelfcheckTypes.FUEL_SYSTEM);
                    }
                    if ((selfcheckStatus & 0x400000) != 0) {
                        selfcheck.add(SelfcheckTypes.COMPONENTS);
                    }
                    boolean compression = (selfcheckStatus & 0x800000) != 0;
                    if (compression) {
                        if ((selfcheckStatus & 0x0100) != 0) {
                            selfcheck.add(SelfcheckTypes.CATALYST);
                        }
                        if ((selfcheckStatus & 0x0200) != 0) {
                            selfcheck.add(SelfcheckTypes.HEATED_CATALYST);
                        }
                        if ((selfcheckStatus & 0x0400) != 0) {
                            selfcheck.add(SelfcheckTypes.EVAPORATIVE_SYSTEM);
                        }
                        if ((selfcheckStatus & 0x0800) != 0) {
                            selfcheck.add(SelfcheckTypes.SECONDARY_AIR_SYSTEM);
                        }
                        if ((selfcheckStatus & 0x1000) != 0) {
                            selfcheck.add(SelfcheckTypes.AC_REFRIDGERANT);
                        }
                        if ((selfcheckStatus & 0x2000) != 0) {
                            selfcheck.add(SelfcheckTypes.OXYGEN_SENSOR);
                        }
                        if ((selfcheckStatus & 0x4000) != 0) {
                            selfcheck.add(SelfcheckTypes.OXYGEN_SENSOR_HEATER);
                        }
                        if ((selfcheckStatus & 0x8000) != 0) {
                            selfcheck.add(SelfcheckTypes.EGR_SYSTEM);
                        }
                    } else {
                        if ((selfcheckStatus & 0x0100) != 0) {
                            selfcheck.add(SelfcheckTypes.NMHC_CATALYST);
                        }
                        if ((selfcheckStatus & 0x0200) != 0) {
                            selfcheck.add(SelfcheckTypes.NOx_SCR_MONITOR);
                        }
                        if ((selfcheckStatus & 0x0800) != 0) {
                            selfcheck.add(SelfcheckTypes.BOOST_PRESSURE);
                        }
                        if ((selfcheckStatus & 0x2000) != 0) {
                            selfcheck.add(SelfcheckTypes.EXHAUST_GAS_SENSOR);
                        }
                        if ((selfcheckStatus & 0x4000) != 0) {
                            selfcheck.add(SelfcheckTypes.PM_FILTER_MONITORING);
                        }
                        if ((selfcheckStatus & 0x8000) != 0) {
                            selfcheck.add(SelfcheckTypes.EGR_VVT_SYSTEM);
                        }
                    }

                    selfcheckListAdapter.notifyDataSetChanged();
                }
            });
        }

        detected.clear();
        detectedListAdapter.notifyDataSetChanged();
        if (enabled) {
            connectionFragment.sendCommand(new BluetoothRunnable.Transaction("03") {
                @Override
                protected void success(String response) {
                    detected.clear();
                    for (int i = 0; i < (selfcheckStatus & 0x7f000000) >> 24; i += 1) {
                        int begin = 2 + i * 4;
                        int code = Integer.valueOf(response.substring(begin, begin + 4), 16);
                        String decoded = String.format("%s%04x",
                                String.valueOf("PCBU".charAt(code >> 14)),
                                code & 0x3fff
                        );
                        detected.add(decoded);
                    }
                    detectedListAdapter.notifyDataSetChanged();
                }
            });
        }
    }

}
