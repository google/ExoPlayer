package com.google.android.exoplayer2.demo.recyclerview;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.exoplayer2.demo.R;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;

public class RecyclerViewFragment extends Fragment {

    public static final String TAG = "RecyclerViewFragment";

    @Bind(R.id.recycler_view)
    RecyclerView recyclerView;

    private VideoAdapter adapter;
    private PercentVisibilityOnScrollListener percentVisibilityOnScrollListener;
    private String urlSource;

    public RecyclerViewFragment() {
        // Required empty public constructor
    }

    public static RecyclerViewFragment newRecyclerViewFragment(Intent intent) {
        RecyclerViewFragment fragment = new RecyclerViewFragment();
        fragment.urlSource = intent.getData().toString();
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_recycler_view, container, false);
        ButterKnife.bind(this, view);

        final List<String> urls = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            urls.add(urlSource);
        }
        adapter = new VideoAdapter(getContext(), urls);
        // recyclerview
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        percentVisibilityOnScrollListener = new PercentVisibilityOnScrollListener(adapter);
        recyclerView.addOnScrollListener(percentVisibilityOnScrollListener);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // onResume, if a video view is visible, resume it
        percentVisibilityOnScrollListener.changePlayerState(recyclerView);
    }

    @Override
    public void onPause() {
        adapter.onPause();
        super.onPause();
    }

    @Override
    public void onStop() {
        adapter.onStop();
        super.onStop();
    }
}
