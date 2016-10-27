package com.google.android.exoplayer2.demo.recyclerview;

import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;

public class RecyclerViewPlayerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(android.R.id.content, RecyclerViewFragment.newRecyclerViewFragment(getIntent()), RecyclerViewFragment.TAG)
                    .commit();
        }
    }
}
