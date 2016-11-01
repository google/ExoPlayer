package com.google.android.exoplayer2.demo.recyclerview;

import android.graphics.Rect;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/* PACKAGE */ class PercentVisibilityOnScrollListener extends RecyclerView.OnScrollListener {

    private static final int PERCENTAGE_MULTIPLIER = 100;
    private static final int SHOWING_TRIGGER_PERCENTAGE = 50;
    private static final long PLAY_DELAY = 250L;
    private static final Rect visibilityRect = new Rect();

    private final VideoAdapter adapter;
    private final Handler handler;
    private Runnable runnable;

    /* PACKAGE */ PercentVisibilityOnScrollListener(VideoAdapter adapter) {
        this.adapter = adapter;
        this.handler = new Handler();
    }

    @Override
    public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
        switch (newState) {
            case RecyclerView.SCROLL_STATE_DRAGGING:
                break;
            case RecyclerView.SCROLL_STATE_IDLE:
                if (runnable != null) {
                    handler.removeCallbacks(runnable);
                    runnable = null;
                }
                scheduleChangePlayerState(recyclerView);
                break;
            case RecyclerView.SCROLL_STATE_SETTLING:
                break;
        }
    }

    private void scheduleChangePlayerState(final RecyclerView recyclerView) {
        handler.postDelayed(runnable = new Runnable() {
            @Override
            public void run() {
                changePlayerState(recyclerView);
            }
        }, PLAY_DELAY);
    }

    /* PACKAGE */ void changePlayerState(RecyclerView recyclerView) {

        final LinearLayoutManager linearLayoutManager =
                (LinearLayoutManager) recyclerView.getLayoutManager();
        final int firstVisibleItemPosition =
                linearLayoutManager.findFirstVisibleItemPosition();
        final int lastVisibleItemPosition =
                linearLayoutManager.findLastVisibleItemPosition();

        int currentlyPlaying = -1;
        int currentHighestPercent = 0;
        int currentViewPercent;

        final List<VideoAdapter.VideoViewHolder> viewHolders = new ArrayList<>();

        for (int i = firstVisibleItemPosition; i <= lastVisibleItemPosition; i++) {
            final RecyclerView.ViewHolder viewHolder =
                    recyclerView.findViewHolderForLayoutPosition(i);
            if (viewHolder != null) {
                viewHolders.add((VideoAdapter.VideoViewHolder) viewHolder);
            }
        }

        for (int i = 0; i < viewHolders.size(); i++) {
            final VideoAdapter.VideoViewHolder viewHolder = viewHolders.get(i);

            currentViewPercent = getPercentShowing(viewHolder.getVideoView());
            if (currentViewPercent > currentHighestPercent && currentViewPercent >= SHOWING_TRIGGER_PERCENTAGE) {
                currentlyPlaying = i;
                currentHighestPercent = currentViewPercent;
            }
        }

        if (currentlyPlaying == -1) {
            // nothing is playing, pause the player
            adapter.pauseVideo();
        } else {
            for (int i = 0; i < viewHolders.size(); i++) {
                final VideoAdapter.VideoViewHolder viewHolder = viewHolders.get(i);
                if (currentlyPlaying == i) {
                    viewHolder.playVideo();
                } else {
                    viewHolder.setupViewState(false, false);
                }
            }
        }
    }

    /* PACKAGE */ static int getPercentShowing(@NonNull View view) {
        if (view.getHeight() <= 0 || view.getWidth() <= 0) {
            return 0;
        }
        view.getGlobalVisibleRect(visibilityRect);
        return (int) (PERCENTAGE_MULTIPLIER * visibilityRect.height() / (float) view.getHeight());
    }

    /* PACKAGE */ static boolean isCompletelyShowing(@NonNull View view) {
        return getPercentShowing(view) >= SHOWING_TRIGGER_PERCENTAGE;
    }
}