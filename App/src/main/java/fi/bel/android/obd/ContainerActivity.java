package fi.bel.android.obd;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

import fi.bel.android.obd.fragment.ConnectionFragment;

public class ContainerActivity extends Activity implements ScreenListFragment.Callbacks {
    protected static final List<Fragment> FRAGMENTS = new ArrayList<>();
    static {
        FRAGMENTS.add(new ConnectionFragment());
        for (Fragment f : FRAGMENTS) {
            f.setRetainInstance(true);
        }
    }

    private boolean mTwoPane;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_list);

        if (findViewById(R.id.screen_detail_container) != null) {
            mTwoPane = true;
            ((ScreenListFragment) getFragmentManager()
                    .findFragmentById(R.id.screen_list))
                    .setActivateOnItemClick(true);
        }
    }

    @Override
    public void onItemSelected(int id) {
        Fragment fragment = FRAGMENTS.get(id);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        if (mTwoPane) {
            ft.replace(R.id.screen_detail_container, fragment);
        } else {
            ft.replace(R.id.screen_list, fragment);
            ft.addToBackStack(null);
        }
        ft.commit();
    }
}
