package com.gabiq.youbid.utils;


/* Borrowed from JazzyListView

Copyright 2013 Two Toasters

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AbsListView;

import com.gabiq.youbid.R;
import com.nineoldandroids.view.ViewHelper;
import com.nineoldandroids.view.ViewPropertyAnimator;

import java.util.HashSet;

public class GridScrollingHelper implements AbsListView.OnScrollListener {

    public interface JazzyEffect {
        /**
         * Initializes the view's attributes so that the view is in position to begin the animation.
         *
         * @param item            The view to be animated.
         * @param position        The index of the view in the list.
         * @param scrollDirection Positive number indicating scrolling down, or negative number indicating scrolling up.
         */
        void initView(View item, int position, int scrollDirection);

        /**
         * Configures the animator object with the relative changes or destination point for any attributes that will be animated.
         *
         * @param item            The view to be animated.
         * @param position        The index of the view in the list.
         * @param scrollDirection Positive number indicating scrolling down, or negative number indicating scrolling up.
         * @param animator        The ViewPropertyAnimator object responsible for animating the view.
         */
        void setupAnimation(View item, int position, int scrollDirection, ViewPropertyAnimator animator);
    }

    public class CustomEffect implements JazzyEffect {
        private static final int DAMPING_FACTOR = 12;
        private static final float INITIAL_SCALE_FACTOR = 0.97f;

        @Override
        public void initView(View item, int position, int scrollDirection) {
            if (scrollDirection <= 0) return;
            ViewHelper.setScaleX(item, INITIAL_SCALE_FACTOR);
            ViewHelper.setScaleY(item, INITIAL_SCALE_FACTOR);
            ViewHelper.setTranslationY(item, item.getHeight() / 2 * scrollDirection / DAMPING_FACTOR);
            ViewHelper.setAlpha(item, 0);
        }

        @Override
        public void setupAnimation(View item, int position, int scrollDirection, ViewPropertyAnimator animator) {
            if (scrollDirection <= 0) return;
            animator.translationYBy(-item.getHeight() / 2 * scrollDirection / DAMPING_FACTOR)
                    .scaleX(1)
                    .scaleY(1)
                    .alpha(OPAQUE);
        }
    }

    public static final int STANDARD = 0;
    public static final int CUSTOM = 15;

    public static final int DURATION = 600;
    public static final int OPAQUE = 255, TRANSPARENT = 0;

    private JazzyEffect mTransitionEffect = null;
    private boolean mIsScrolling = false;
    private int mFirstVisibleItem = -1;
    private int mLastVisibleItem = -1;
    private int mPreviousFirstVisibleItem = 0;
    private long mPreviousEventTime = 0;
    private double mSpeed = 0;
    private int mMaxVelocity = 0;
    public static final int MAX_VELOCITY_OFF = 0;

    private AbsListView.OnScrollListener mAdditionalOnScrollListener;

    private boolean mOnlyAnimateNewItems;
    private boolean mOnlyAnimateOnFling;
    private boolean mIsFlingEvent;
    private boolean mSimulateGridWithList;
    private final HashSet<Integer> mAlreadyAnimatedItems;

    public GridScrollingHelper(Context context, AttributeSet attrs) {

        mAlreadyAnimatedItems = new HashSet<Integer>();

        int transitionEffect = CUSTOM;
        int maxVelocity = MAX_VELOCITY_OFF;
        mOnlyAnimateNewItems = false;
        mOnlyAnimateOnFling = false;
        mSimulateGridWithList = false;
//        a.recycle();

        setTransitionEffect(transitionEffect);
        setMaxAnimationVelocity(maxVelocity);
    }

    public void setOnScrollListener(AbsListView.OnScrollListener l) {
        // hijack the scroll listener setter and have this list also notify the additional listener
        mAdditionalOnScrollListener = l;
    }

    /**
     * @see AbsListView.OnScrollListener#onScroll
     */
    @Override
    public final void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        boolean shouldAnimateItems = (mFirstVisibleItem != -1 && mLastVisibleItem != -1);

        int lastVisibleItem = firstVisibleItem + visibleItemCount - 1;
        if (mIsScrolling && shouldAnimateItems) {
            setVelocity(firstVisibleItem, totalItemCount);
            int indexAfterFirst = 0;
            while (firstVisibleItem + indexAfterFirst < mFirstVisibleItem) {
                View item = view.getChildAt(indexAfterFirst);
                doJazziness(item, firstVisibleItem + indexAfterFirst, -1);
                indexAfterFirst++;
            }

            int indexBeforeLast = 0;
            while (lastVisibleItem - indexBeforeLast > mLastVisibleItem) {
                View item = view.getChildAt(lastVisibleItem - firstVisibleItem - indexBeforeLast);
                doJazziness(item, lastVisibleItem - indexBeforeLast, 1);
                indexBeforeLast++;
            }
        } else if (!shouldAnimateItems) {
            for (int i = firstVisibleItem; i < visibleItemCount; i++) {
                mAlreadyAnimatedItems.add(i);
            }
        }

        mFirstVisibleItem = firstVisibleItem;
        mLastVisibleItem = lastVisibleItem;

        notifyAdditionalOnScrollListener(view, firstVisibleItem, visibleItemCount, totalItemCount);
    }

    /**
     * Should be called in onScroll to keep take of current Velocity.
     *
     * @param firstVisibleItem
     *            The index of the first visible item in the ListView.
     */
    private void setVelocity(int firstVisibleItem, int totalItemCount) {
        if (mMaxVelocity > MAX_VELOCITY_OFF && mPreviousFirstVisibleItem != firstVisibleItem) {
            long currTime = System.currentTimeMillis();
            long timeToScrollOneItem = currTime - mPreviousEventTime;
            if (timeToScrollOneItem < 1) {
                double newSpeed = ((1.0d / timeToScrollOneItem) * 1000);
                // We need to normalize velocity so different size item don't
                // give largely different velocities.
                if (newSpeed < (0.9f * mSpeed)) {
                    mSpeed *= 0.9f;
                } else if (newSpeed > (1.1f * mSpeed)) {
                    mSpeed *= 1.1f;
                } else {
                    mSpeed = newSpeed;
                }
            } else {
                mSpeed = ((1.0d / timeToScrollOneItem) * 1000);
            }

            mPreviousFirstVisibleItem = firstVisibleItem;
            mPreviousEventTime = currTime;
        }
    }

    /**
     *
     * @return Returns the current Velocity of the ListView's scrolling in items
     *         per second.
     */
    private double getVelocity() {
        return mSpeed;
    }

    /**
     * Initializes the item view and triggers the animation.
     *
     * @param item The view to be animated.
     * @param position The index of the view in the list.
     * @param scrollDirection Positive number indicating scrolling down, or negative number indicating scrolling up.
     */
    private void doJazziness(View item, int position, int scrollDirection) {
        if (mIsScrolling) {
            if (mOnlyAnimateNewItems && mAlreadyAnimatedItems.contains(position))
                return;

            if (mOnlyAnimateOnFling && !mIsFlingEvent)
                return;

            if (mMaxVelocity > MAX_VELOCITY_OFF && mMaxVelocity < getVelocity())
                return;

            if (mSimulateGridWithList) {
                ViewGroup itemRow = (ViewGroup) item;
                for (int i = 0; i < itemRow.getChildCount(); i++)
                    doJazzinessImpl(itemRow.getChildAt(i), position, scrollDirection);
            } else {
                doJazzinessImpl(item, position, scrollDirection);
            }

            mAlreadyAnimatedItems.add(position);
        }
    }

    private void doJazzinessImpl(View item, int position, int scrollDirection) {
        ViewPropertyAnimator animator = com.nineoldandroids.view.ViewPropertyAnimator
                .animate(item)
                .setDuration(DURATION)
                .setInterpolator(new AccelerateDecelerateInterpolator());

        scrollDirection = scrollDirection > 0 ? 1 : -1;
        mTransitionEffect.initView(item, position, scrollDirection);
        mTransitionEffect.setupAnimation(item, position, scrollDirection, animator);
        animator.start();
    }

    /**
     * @see AbsListView.OnScrollListener#onScrollStateChanged
     */
    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        switch(scrollState) {
            case AbsListView.OnScrollListener.SCROLL_STATE_IDLE:
                mIsScrolling = false;
                mIsFlingEvent = false;
                break;
            case AbsListView.OnScrollListener.SCROLL_STATE_FLING:
                mIsFlingEvent = true;
                break;
            case AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
                mIsScrolling = true;
                mIsFlingEvent = false;
                break;
            default: break;
        }
        notifyAdditionalOnScrollStateChangedListener(view, scrollState);
    }

    public void setTransitionEffect(int transitionEffect) {

        switch (transitionEffect) {
            case CUSTOM: setTransitionEffect(new CustomEffect()); break;
            default: break;
        }
    }

    public void setTransitionEffect(JazzyEffect transitionEffect) {
        mTransitionEffect = transitionEffect;
    }

    public void setShouldOnlyAnimateNewItems(boolean onlyAnimateNew) {
        mOnlyAnimateNewItems = onlyAnimateNew;
    }

    public void setShouldOnlyAnimateFling(boolean onlyFling) {
        mOnlyAnimateOnFling = onlyFling;
    }

    public void setMaxAnimationVelocity(int itemsPerSecond) {
        mMaxVelocity = itemsPerSecond;
    }

    public void setSimulateGridWithList(boolean simulateGridWithList) {
        mSimulateGridWithList = simulateGridWithList;
    }

    /**
     * Notifies the OnScrollListener of an onScroll event, since JazzyListView is the primary listener for onScroll events.
     */
    private void notifyAdditionalOnScrollListener(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        if (mAdditionalOnScrollListener != null) {
            mAdditionalOnScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
        }
    }

    /**
     * Notifies the OnScrollListener of an onScrollStateChanged event, since JazzyListView is the primary listener for onScrollStateChanged events.
     */
    private void notifyAdditionalOnScrollStateChangedListener(AbsListView view, int scrollState) {
        if (mAdditionalOnScrollListener != null) {
            mAdditionalOnScrollListener.onScrollStateChanged(view, scrollState);
        }
    }
}
