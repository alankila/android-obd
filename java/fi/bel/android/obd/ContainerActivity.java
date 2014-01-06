package fi.bel.android.obd;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;

import java.util.ArrayList;
import java.util.List;

import fi.bel.android.obd.fragment.ConnectionFragment;
import fi.bel.android.obd.fragment.DTCSearchFragment;
import fi.bel.android.obd.fragment.GraphFragment;
import fi.bel.android.obd.fragment.SelfCheckFragment;
import fi.bel.android.obd.fragment.DataFragment;
import fi.bel.android.obd.thread.BluetoothRunnable;

public class ContainerActivity extends Activity implements ScreenListFragment.Callbacks {
    protected static final List<Fragment> FRAGMENTS = new ArrayList<>();
    static {
        FRAGMENTS.add(new ConnectionFragment());
        FRAGMENTS.add(new SelfCheckFragment());
        FRAGMENTS.add(new DTCSearchFragment());
        FRAGMENTS.add(new DataFragment());
        FRAGMENTS.add(new GraphFragment());
        for (Fragment f : FRAGMENTS) {
            f.setRetainInstance(true);
        }
    }

    /** Globally accessible Bluetooth connection */
    public static BluetoothRunnable BLUETOOTH_RUNNABLE;

    private boolean mTwoPane;

    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_list);

        BLUETOOTH_RUNNABLE = new BluetoothRunnable(new Handler(), getApplicationContext());

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

    /*@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        for (int i = 0; i < FRAGMENTS.size(); i += 1) {
            String className = FRAGMENTS.get(i).getClass().getSimpleName();
            menu.add(0, i, i, getResources().getIdentifier(className, "string", getPackageName()));
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        onItemSelected(item.getItemId());
        return true;
    }*/

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
