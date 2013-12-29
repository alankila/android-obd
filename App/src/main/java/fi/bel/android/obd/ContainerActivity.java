package fi.bel.android.obd;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

import fi.bel.android.obd.fragment.ConnectionFragment;
import fi.bel.android.obd.fragment.FaultFragment;

public class ContainerActivity extends Activity implements ScreenListFragment.Callbacks {
    public static final List<Fragment> FRAGMENTS = new ArrayList<>();
    static {
        FRAGMENTS.add(new ConnectionFragment());
        FRAGMENTS.add(new FaultFragment());
        for (Fragment f : FRAGMENTS) {
            f.setRetainInstance(true);
        }
    }

    private boolean mTwoPane;

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
    }

    @Override
    public void onItemSelected(int id) {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment fragment = FRAGMENTS.get(id);
        if (mTwoPane) {
            ft.replace(R.id.screen_detail_container, fragment);
        } else {
            ft.replace(R.id.screen_list, fragment);
            ft.addToBackStack(null);
        }
        ft.commit();
    }
}
