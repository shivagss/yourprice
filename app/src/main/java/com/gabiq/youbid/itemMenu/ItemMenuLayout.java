package com.gabiq.youbid.itemMenu;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.Animation.AnimationListener;

import com.gabiq.youbid.R;


public class ItemMenuLayout extends ViewGroup {
    /**
     * children will be set the same size.
     */
    private int mChildSize;

    private int mChildPadding = 5;

    private int mLayoutPadding = 10;

    public static final float DEFAULT_FROM_DEGREES = 270.0f;

    public static final float DEFAULT_TO_DEGREES = 360.0f;

    private float mFromDegrees = DEFAULT_FROM_DEGREES;

    private float mToDegrees = DEFAULT_TO_DEGREES;

    private static final int MIN_RADIUS = 100;

    /* the distance between the layout's center and any child's center */
    private int mRadius;

    private boolean mExpanded = false;

    public ItemMenuLayout(Context context) {
        super(context);
    }

    public ItemMenuLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ItemMenuLayout, 0, 0);
            mFromDegrees = a.getFloat(R.styleable.ArcLayout_fromDegrees, DEFAULT_FROM_DEGREES);
            mToDegrees = a.getFloat(R.styleable.ArcLayout_toDegrees, DEFAULT_TO_DEGREES);
            mChildSize = Math.max(a.getDimensionPixelSize(R.styleable.ArcLayout_childSize, 0), 0);

            a.recycle();
        }
    }

    private static int computeRadius(final float arcDegrees, final int childCount, final int childSize,
                                     final int childPadding, final int minRadius) {
        if (childCount < 2) {
            return minRadius;
        }

        final float perDegrees = arcDegrees / (childCount - 1);
        final float perHalfDegrees = perDegrees / 2;
        final int perSize = childSize + childPadding;

        final int radius = (int) ((perSize / 2) / Math.sin(Math.toRadians(perHalfDegrees)));

        return Math.max(radius, minRadius);
    }

    private static Rect computeChildFrame(final int centerX, final int centerY, final int radius, final float degrees,
                                          final int size) {

        final double childCenterX = centerX + radius * Math.cos(Math.toRadians(degrees));
        final double childCenterY = centerY + radius * Math.sin(Math.toRadians(degrees));

        return new Rect((int) (childCenterX - size / 2), (int) (childCenterY - size / 2),
                (int) (childCenterX + size / 2), (int) (childCenterY + size / 2));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int radius = mRadius = computeRadius(Math.abs(mToDegrees - mFromDegrees), getChildCount(), mChildSize,
                mChildPadding, MIN_RADIUS);
        final int size = radius * 2 + mChildSize + mChildPadding + mLayoutPadding * 2;

        setMeasuredDimension(size, size);

        final int count = getChildCount();
        for (int i = 0; i < count; i++) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(mChildSize, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(mChildSize, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int centerX = getWidth() / 2;
        final int centerY = getHeight() / 2;
        final int radius = mExpanded ? mRadius : 0;

        final int childCount = getChildCount();
        final float perDegrees = (mToDegrees - mFromDegrees) / (childCount - 1);

        float degrees = mFromDegrees;
        for (int i = 0; i < childCount; i++) {
            Rect frame = computeChildFrame(centerX, centerY, radius, degrees, mChildSize);
            degrees += perDegrees;
            getChildAt(i).layout(frame.left, frame.top, frame.right, frame.bottom);
        }
    }

    private static long computeStartOffset(final int childCount, final boolean expanded, final int index,
                                           final float delayPercent, final long duration, Interpolator interpolator) {
        final float delay = delayPercent * duration;
        final long viewDelay = (long) (getTransformedIndex(expanded, childCount, index) * delay);
        final float totalDelay = delay * childCount;

        float normalizedDelay = viewDelay / totalDelay;
        normalizedDelay = interpolator.getInterpolation(normalizedDelay);

        return (long) (normalizedDelay * totalDelay);
    }

    private static int getTransformedIndex(final boolean expanded, final int count, final int index) {
        if (expanded) {
            return count - 1 - index;
        }

        return index;
    }

    private static Animation createExpandAnimation(float fromXDelta, float toXDelta, float fromYDelta, float toYDelta,
                                                   long startOffset, long duration, Interpolator interpolator) {

        AnimationSet animationSet = new AnimationSet(false);

        Animation animation = new RotateAndTranslateAnimation(0, toXDelta, 0, toYDelta, 0, 720);
        animation.setStartOffset(startOffset);
        animation.setDuration(duration);
        animation.setInterpolator(interpolator);
        animationSet.addAnimation(animation);

        Animation animationAlpha = new AlphaAnimation(0.0f, 1.0f);
        animationAlpha.setDuration(200);
        animationSet.addAnimation(animationAlpha);

        animationSet.setFillAfter(true);


        return animationSet;
    }

    private static Animation createShrinkAnimation(float fromXDelta, float toXDelta, float fromYDelta, float toYDelta,
                                                   long startOffset, long duration, Interpolator interpolator) {
        AnimationSet animationSet = new AnimationSet(false);
        animationSet.setFillAfter(true);

        final long preDuration = duration / 2;
        Animation rotateAnimation = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        rotateAnimation.setStartOffset(startOffset);
        rotateAnimation.setDuration(preDuration);
        rotateAnimation.setInterpolator(new LinearInterpolator());
        rotateAnimation.setFillAfter(true);

        animationSet.addAnimation(rotateAnimation);

        Animation translateAnimation = new RotateAndTranslateAnimation(0, toXDelta, 0, toYDelta, 360, 720);
        translateAnimation.setStartOffset(startOffset + preDuration);
        translateAnimation.setDuration(duration - preDuration);
        translateAnimation.setInterpolator(interpolator);
        translateAnimation.setFillAfter(true);

        animationSet.addAnimation(translateAnimation);

        Animation animationAlpha = new AlphaAnimation(1.0f, 0.0f);
        animationAlpha.setDuration(600);
        animationSet.addAnimation(animationAlpha);

        return animationSet;
    }

    private void bindChildAnimation(final View child, final int index, final long duration) {
        final boolean expanded = mExpanded;
        final int centerX = getWidth() / 2;
        final int centerY = getHeight() / 2;
        final int radius = expanded ? 0 : mRadius;

        final int childCount = getChildCount();
        final float perDegrees = (mToDegrees - mFromDegrees) / (childCount - 1);
        Rect frame = computeChildFrame(centerX, centerY, radius, mFromDegrees + index * perDegrees, mChildSize);

        final int toXDelta = frame.left - child.getLeft();
        final int toYDelta = frame.top - child.getTop();

        Interpolator interpolator = mExpanded ? new AccelerateInterpolator() : new OvershootInterpolator(1.5f);
        final long startOffset = computeStartOffset(childCount, mExpanded, index, 0.1f, duration, interpolator);

        Animation animation = mExpanded ? createShrinkAnimation(0, toXDelta, 0, toYDelta, startOffset, duration,
                interpolator) : createExpandAnimation(0, toXDelta, 0, toYDelta, startOffset, duration, interpolator);

        final boolean isLast = getTransformedIndex(expanded, childCount, index) == childCount - 1;
        animation.setAnimationListener(new AnimationListener() {

            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (isLast) {
                    postDelayed(new Runnable() {

                        @Override
                        public void run() {
                            onAllAnimationsEnd();
                        }
                    }, 0);
                }
            }
        });

        child.setAnimation(animation);
    }

    public boolean isExpanded() {
        return mExpanded;
    }

    public void setArc(float fromDegrees, float toDegrees) {
        if (mFromDegrees == fromDegrees && mToDegrees == toDegrees) {
            return;
        }

        mFromDegrees = fromDegrees;
        mToDegrees = toDegrees;

        requestLayout();
    }

    public void setChildSize(int size) {
        if (mChildSize == size || size < 0) {
            return;
        }

        mChildSize = size;

        requestLayout();
    }

    public int getChildSize() {
        return mChildSize;
    }

    /**
     * switch between expansion and shrinkage
     *
     * @param showAnimation
     */
    public void switchState(final boolean showAnimation) {
//        if (mExpanded == true) {
//            setAlpha((float) 0.0);
//        }

        if (showAnimation) {
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                bindChildAnimation(getChildAt(i), i, 300);
            }
        }

        mExpanded = !mExpanded;

        if (!showAnimation) {
            requestLayout();
        }

        invalidate();
    }

    private void onAllAnimationsEnd() {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            getChildAt(i).clearAnimation();
        }

        if (mExpanded == false) {
            setVisibility(View.INVISIBLE);
//            setAlpha((float) 0.0);
        }

        requestLayout();
    }

    public void toState(boolean onState, final boolean showAnimation) {
        if (onState == true) {
            setVisibility(View.VISIBLE);
//            setAlpha((float) 1.0);
        }

        if (onState == false && showAnimation == false) {
            setVisibility(View.INVISIBLE);
//            setAlpha((float) 0.0);
        }

        if (mExpanded == onState) return;

        if (showAnimation) {
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                bindChildAnimation(getChildAt(i), i, 300);
            }
        }

        mExpanded = onState;

        if (!showAnimation) {
            requestLayout();
        }

        invalidate();
    }
}
