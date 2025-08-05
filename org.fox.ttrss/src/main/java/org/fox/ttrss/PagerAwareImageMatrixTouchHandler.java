package org.fox.ttrss;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.bogdwellers.pinchtozoom.ImageMatrixTouchHandler;

// Based on https://github.com/martinwithaar/PinchToZoom/blob/master/pinchtozoom/src/main/java/com/bogdwellers/pinchtozoom/view/ImageViewPager.java
// mLastMotionX is a simplified version of ViewPager's MotionEvent logic
public class PagerAwareImageMatrixTouchHandler extends ImageMatrixTouchHandler {
    /**
     * NOT Thread safe! (But it all happens on the UI thread anyway)
     */
    private static final float[] VALUES = new float[9];

    private static final float SCALE_THRESHOLD = 1.2f;

    private float mLastMotionX;

    public PagerAwareImageMatrixTouchHandler(Context context) {
        super(context);
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        super.onTouch(view, event);

        if (event.getPointerCount() > 1) {
            view.getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }

        if (event.getAction() != MotionEvent.ACTION_MOVE)
        {
            return true;
        }

        float x = event.getX(0);
        float dx = x - mLastMotionX;
        mLastMotionX = x;

        ImageView iv = (ImageView) view;
        Drawable drawable = iv.getDrawable();
        if (drawable != null) {
            float vw = iv.getWidth();
            float vh = iv.getHeight();
            float dw = drawable.getIntrinsicWidth();
            float dh = drawable.getIntrinsicHeight();

            Matrix matrix = iv.getImageMatrix();
            matrix.getValues(VALUES);
            float tx = VALUES[Matrix.MTRANS_X] + dx;
            float sdw = dw * VALUES[Matrix.MSCALE_X];

            boolean blockScroll = VALUES[Matrix.MSCALE_X] / centerInsideScale(vw, vh, dw, dh) > SCALE_THRESHOLD && !translationExceedsBoundary(tx, vw, sdw) && sdw > vw; // Assumes x-y scales are equal
            view.getParent().requestDisallowInterceptTouchEvent(blockScroll);
        }

        return true;
    }

    /**
     * <p>Returns the scale ratio between view and drawable for the longest side.</p>
     *
     * @param vw
     * @param vh
     * @param dw
     * @param dh
     * @return
     */
    public static final float centerInsideScale(float vw, float vh, float dw, float dh) {
        return vw / vh <= dw / dh ? vw / dw : vh / dh;
    }

    /**
     * <p>Determines whether a translation makes the view exceed the boundary of a drawable.</p>
     *
     * @param tx
     * @param vw
     * @param dw
     * @return
     */
    public static final boolean translationExceedsBoundary(float tx, float vw, float dw) {
        return dw >= vw && (tx > 0 || tx < vw - dw);
    }
}
