package com.banditsoft.gpu_gameoflife;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

/**
 * Created by ipilter on 22/03/2017.
 */

public class GLSurface extends GLSurfaceView {
    private PointF mPreviousScreenPoint = new PointF();
    private GOLEngine mRenderer;
    private ScaleGestureDetector mScaleGestureDetector;

    public GLSurface(Context context) {
        super(context);
        init(context);
    }

    public GLSurface(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context)
    {
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        mRenderer = new GOLEngine(context);
        mScaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        mScaleGestureDetector.setQuickScaleEnabled(false);
        setRenderer(mRenderer);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mScaleGestureDetector.onTouchEvent(event);

        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_MOVE: {
                PointF currentScreenPoint = new PointF();
                if(event.getPointerCount() == 1)
                {
                    currentScreenPoint.set(event.getX(), event.getY());
                }
                else
                {
                    currentScreenPoint.set((event.getX(0) + event.getX(1)) / 2.0f, (event.getY(0) + event.getY(1)) / 2.0f);
                }
                PointF currentModelPoint = mRenderer.map(currentScreenPoint);
                PointF previousModelPoint = mRenderer.map(mPreviousScreenPoint);
                mRenderer.pan(new PointF(currentModelPoint.x - previousModelPoint.x, currentModelPoint.y - previousModelPoint.y));

                mPreviousScreenPoint = currentScreenPoint;
                requestRender();
                break;
            }
            case MotionEvent.ACTION_DOWN: {
                mPreviousScreenPoint.set(event.getX(), event.getY());
                break;
            }
            case MotionEvent.ACTION_UP: {
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN: {
                mPreviousScreenPoint.set((event.getX(0) + event.getX(1)) / 2.0f, (event.getY(0) + event.getY(1)) / 2.0f);
                break;
            }
            case MotionEvent.ACTION_POINTER_UP: {
                int remainingFinger = 1 - event.getActionIndex();
                mPreviousScreenPoint.set(event.getX(remainingFinger), event.getY(remainingFinger));
                break;
            }
        }

        super.onTouchEvent(event);
        return true;
    }

    public void requestSimulation() {
        queueEvent(new Runnable() {
            public void run() {
            mRenderer.simulate();
            }
        });
        requestRender();
    }

    public void requestNoise() {
        queueEvent(new Runnable() {
            public void run() {
                mRenderer.addNoise();
            }
        });
        requestRender();
    }

    public void setRules(int deadRules, int liveRules)
    {
        mRenderer.setRules(deadRules, liveRules);
    }

    public void reset() {
        queueEvent(new Runnable() {
            public void run() {
                mRenderer.reset();
            }
        });
        requestRender();
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mRenderer.scale(detector.getScaleFactor(), mRenderer.map(new PointF(detector.getFocusX(), detector.getFocusY())));
            requestRender();
            return true;
        }
    }
}
