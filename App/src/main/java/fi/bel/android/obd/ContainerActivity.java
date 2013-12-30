package fi.bel.android.obd;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

import fi.bel.android.obd.fragment.ConnectionFragment;
import fi.bel.android.obd.fragment.DTCFragment;
import fi.bel.android.obd.fragment.DataFragment;
import fi.bel.android.obd.fragment.FaultFragment;

public class ContainerActivity extends Activity implements ScreenListFragment.Callbacks {
    public static final List<Fragment> FRAGMENTS = new ArrayList<>();
    static {
        FRAGMENTS.add(new ConnectionFragment());
        FRAGMENTS.add(new FaultFragment());
        FRAGMENTS.add(new DTCFragment());
        FRAGMENTS.add(new DataFragment());
        for (Fragment f : FRAGMENTS) {
            f.setRetainInstance(true);
        }
    }

    private boolean mTwoPane;

    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_list);

        getFragmentManager()
                .beginTransaction()
                .replace(R.id.screen_list, new ScreenListFragment())
                .commit();
        if (findViewById(R.id.screen_detail_container) != null) {
            mTwoPane = true;
            ((ScreenListFragment) getFragmentManager()
                    .findFragmentById(R.id.screen_list))
                    .setActivateOnItemClick(true);
        }

        if (savedInstanceState != null && savedInstanceState.containsKey("activeFragment")) {
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            activeFragment = FRAGMENTS.get(savedInstanceState.getInt("activeFragment"));
            if (mTwoPane) {
                ft.replace(R.id.screen_detail_container, activeFragment);
            } else {
                ft.replace(R.id.screen_list, activeFragment);
            }
            ft.commit();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (activeFragment != null) {
            outState.putInt("activeFragment", FRAGMENTS.indexOf(activeFragment));
        }
    }

    @Override
    public void onItemSelected(int id) {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        activeFragment = FRAGMENTS.get(id);
        if (mTwoPane) {
            ft.replace(R.id.screen_detail_container, activeFragment);
        } else {
            ft.replace(R.id.screen_list, activeFragment);
            ft.addToBackStack(null);
        }
        ft.commit();
    }
}
