package com.gabiq.youbid.activity;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;

import com.gabiq.youbid.R;
import com.gabiq.youbid.fragment.DetailsFragment;

public class DetailsActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_details);

    /* if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, new DetailsFragment())
                    .commit();
        }
     */
        String itemId = getIntent().getStringExtra("item_id");
        if (savedInstanceState == null) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            DetailsFragment detailsFragment = DetailsFragment.newInstance(itemId);
            ft.replace(R.id.container, detailsFragment);
            ft.commit();
        }

        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

}
