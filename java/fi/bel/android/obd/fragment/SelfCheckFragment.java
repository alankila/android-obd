package fi.bel.android.obd.fragment;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import fi.bel.android.obd.ContainerActivity;
import fi.bel.android.obd.R;
import fi.bel.android.obd.thread.BluetoothRunnable;
import fi.bel.android.obd.util.DTC;

public class SelfCheckFragment extends Fragment {
    protected static final String TAG = SelfCheckFragment.class.getSimpleName();

    protected enum SelfCheckType {
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

    protected List<SelfCheckType> selfCheck = new ArrayList<>();

    protected ArrayAdapter<SelfCheckType> selfCheckListAdapter;

    protected ListView selfCheckList;

    protected ArrayAdapter<String> detectedListAdapter;

    protected List<String> detected = new ArrayList<>();

    protected ListView detectedList;

    protected Button clearButton;

    protected int selfCheckStatus;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_fault, null);
        selfCheckListAdapter = new ArrayAdapter<SelfCheckType>(getActivity(), 0, 0, selfCheck) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getActivity().getLayoutInflater().inflate(R.layout.fragment_fault_item, null);
                }
                SelfCheckType selfCheckType = selfCheck.get(position);

                TextView name = (TextView) convertView.findViewById(R.id.fault_item_name);
                name.setText(getResources().getIdentifier(selfCheckType.toString(), "string", getActivity().getPackageName()));

                TextView state = (TextView) convertView.findViewById(R.id.fault_item_state);
                switch (selfCheckType) {
                    case MIL:
                        state.setText((selfCheckStatus & 0x80000000) != 0 ? "On" : "Off");
                        break;

                    case FAULT_CODES:
                        state.setText(String.valueOf((selfCheckStatus >> 24) & 0x7f));
                        break;

                    case MISFIRE:
                        state.setText((selfCheckStatus & 0x10000) != 0 ? "Not ready" : "Ready");
                        break;

                    case FUEL_SYSTEM:
                        state.setText((selfCheckStatus & 0x20000) != 0 ? "Not ready" : "Ready");
                        break;

                    case COMPONENTS:
                        state.setText((selfCheckStatus & 0x40000) != 0 ? "Not ready" : "Ready");
                        break;

                    /* Diesel */
                    case CATALYST:
                        state.setText((selfCheckStatus & 0x01) != 0 ? "Not ready" : "Ready");
                        break;

                    case HEATED_CATALYST:
                        state.setText((selfCheckStatus & 0x02) != 0 ? "Not ready" : "Ready");
                        break;

                    case EVAPORATIVE_SYSTEM:
                        state.setText((selfCheckStatus & 0x04) != 0 ? "Not ready" : "Ready");
                        break;

                    case SECONDARY_AIR_SYSTEM:
                        state.setText((selfCheckStatus & 0x08) != 0 ? "Not ready" : "Ready");
                        break;

                    case AC_REFRIDGERANT:
                        state.setText((selfCheckStatus & 0x10) != 0 ? "Not ready" : "Ready");
                        break;

                    case OXYGEN_SENSOR:
                        state.setText((selfCheckStatus & 0x20) != 0 ? "Not ready" : "Ready");
                        break;

                    case OXYGEN_SENSOR_HEATER:
                        state.setText((selfCheckStatus & 0x40) != 0 ? "Not ready" : "Ready");
                        break;

                    case EGR_SYSTEM:
                        state.setText((selfCheckStatus & 0x80) != 0 ? "Not ready" : "Ready");
                        break;

                    /* Spark */
                    case NMHC_CATALYST:
                        state.setText((selfCheckStatus & 0x01) != 0 ? "Not ready" : "Ready");
                        break;

                    case NOx_SCR_MONITOR:
                        state.setText((selfCheckStatus & 0x02) != 0 ? "Not ready" : "Ready");
                        break;

                    case BOOST_PRESSURE:
                        state.setText((selfCheckStatus & 0x08) != 0 ? "Not ready" : "Ready");
                        break;

                    case EXHAUST_GAS_SENSOR:
                        state.setText((selfCheckStatus & 0x20) != 0 ? "Not ready" : "Ready");
                        break;

                    case PM_FILTER_MONITORING:
                        state.setText((selfCheckStatus & 0x40) != 0 ? "Not ready" : "Ready");
                        break;

                    case EGR_VVT_SYSTEM:
                        state.setText((selfCheckStatus & 0x80) != 0 ? "Not ready" : "Ready");
                        break;
                }

                return convertView;
            }
        };
        selfCheckList = (ListView) view.findViewById(R.id.fault_selfcheck);
        selfCheckList.setAdapter(selfCheckListAdapter);

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
                Map<String, String> faultMap = DTC.getFaultMap(getContext());
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
                ContainerActivity.BLUETOOTH_RUNNABLE.addTransaction(new BluetoothRunnable.Transaction("04") {
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
        enabled &= ContainerActivity.BLUETOOTH_RUNNABLE.getPhase() == BluetoothRunnable.Phase.READY;
        clearButton.setEnabled(enabled);

        selfCheckStatus = 0;
        selfCheck.clear();
        selfCheckListAdapter.clear();
        if (enabled && ContainerActivity.BLUETOOTH_RUNNABLE.pid().contains("01")) {
            ContainerActivity.BLUETOOTH_RUNNABLE.addTransaction(new BluetoothRunnable.Transaction("01 01 1") {
                @Override
                protected void success(String response) {
                    selfCheckStatus = (int) Long.parseLong(response.substring(4), 16);

                    selfCheck.add(SelfCheckType.MIL);
                    selfCheck.add(SelfCheckType.FAULT_CODES);
                    if ((selfCheckStatus & 0x100000) != 0) {
                        selfCheck.add(SelfCheckType.MISFIRE);
                    }
                    if ((selfCheckStatus & 0x200000) != 0) {
                        selfCheck.add(SelfCheckType.FUEL_SYSTEM);
                    }
                    if ((selfCheckStatus & 0x400000) != 0) {
                        selfCheck.add(SelfCheckType.COMPONENTS);
                    }
                    boolean compression = (selfCheckStatus & 0x800000) != 0;
                    if (compression) {
                        if ((selfCheckStatus & 0x0100) != 0) {
                            selfCheck.add(SelfCheckType.CATALYST);
                        }
                        if ((selfCheckStatus & 0x0200) != 0) {
                            selfCheck.add(SelfCheckType.HEATED_CATALYST);
                        }
                        if ((selfCheckStatus & 0x0400) != 0) {
                            selfCheck.add(SelfCheckType.EVAPORATIVE_SYSTEM);
                        }
                        if ((selfCheckStatus & 0x0800) != 0) {
                            selfCheck.add(SelfCheckType.SECONDARY_AIR_SYSTEM);
                        }
                        if ((selfCheckStatus & 0x1000) != 0) {
                            selfCheck.add(SelfCheckType.AC_REFRIDGERANT);
                        }
                        if ((selfCheckStatus & 0x2000) != 0) {
                            selfCheck.add(SelfCheckType.OXYGEN_SENSOR);
                        }
                        if ((selfCheckStatus & 0x4000) != 0) {
                            selfCheck.add(SelfCheckType.OXYGEN_SENSOR_HEATER);
                        }
                        if ((selfCheckStatus & 0x8000) != 0) {
                            selfCheck.add(SelfCheckType.EGR_SYSTEM);
                        }
                    } else {
                        if ((selfCheckStatus & 0x0100) != 0) {
                            selfCheck.add(SelfCheckType.NMHC_CATALYST);
                        }
                        if ((selfCheckStatus & 0x0200) != 0) {
                            selfCheck.add(SelfCheckType.NOx_SCR_MONITOR);
                        }
                        if ((selfCheckStatus & 0x0800) != 0) {
                            selfCheck.add(SelfCheckType.BOOST_PRESSURE);
                        }
                        if ((selfCheckStatus & 0x2000) != 0) {
                            selfCheck.add(SelfCheckType.EXHAUST_GAS_SENSOR);
                        }
                        if ((selfCheckStatus & 0x4000) != 0) {
                            selfCheck.add(SelfCheckType.PM_FILTER_MONITORING);
                        }
                        if ((selfCheckStatus & 0x8000) != 0) {
                            selfCheck.add(SelfCheckType.EGR_VVT_SYSTEM);
                        }
                    }

                    selfCheckListAdapter.notifyDataSetChanged();
                }
            });
        }

        detected.clear();
        detectedListAdapter.notifyDataSetChanged();
        if (enabled) {
            ContainerActivity.BLUETOOTH_RUNNABLE.addTransaction(new BluetoothRunnable.Transaction("03") {
                @Override
                protected void success(String response) {
                    detected.clear();
                    for (int i = 0; i < (selfCheckStatus & 0x7f000000) >> 24; i += 1) {
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
