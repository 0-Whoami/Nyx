package com.termux.view.textselection;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewManager;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.PopupWindow;

import com.termux.view.R;
import com.termux.view.TerminalView;

public class TextSelectionHandleView extends View {

    private final int[] mTempCoords = new int[2];
    private final TerminalView terminalView;
    private final TextSelectionCursorController mCursorController;
    private final Drawable mHandleDrawable;
    //private final int mInitialOrientation;
    private Rect mTempRect;
    private PopupWindow mHandle;
    private boolean mIsDragging;
    private int mPointX;
    private int mPointY;
    private float mTouchToWindowOffsetX;
    private float mTouchToWindowOffsetY;
    private float mHotspotX;
    private float mHotspotY;
    private float mTouchOffsetY;
    private int mLastParentX;
    private int mLastParentY;
    private int mHandleHeight;
    private long mLastTime;

    public TextSelectionHandleView(final TerminalView terminalView, final TextSelectionCursorController cursorController) {
        super(terminalView.getContext());
        this.terminalView = terminalView;
        this.mCursorController = cursorController;
        this.mHandleDrawable = this.getContext().getDrawable(R.drawable.text_select_handle_material);
        this.setOrientation();
    }

    private void initHandle() {
        this.mHandle = new PopupWindow(this.terminalView.getContext(), null, android.R.attr.textSelectHandleWindowStyle);
        this.mHandle.setSplitTouchEnabled(true);
        this.mHandle.setClippingEnabled(false);
        this.mHandle.setWidth(ViewGroup.LayoutParams.WRAP_CONTENT);
        this.mHandle.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        this.mHandle.setBackgroundDrawable(null);
        this.mHandle.setAnimationStyle(0);
        this.mHandle.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);
        this.mHandle.setEnterTransition(null);
        this.mHandle.setExitTransition(null);
        this.mHandle.setContentView(this);
    }

    private void setOrientation() {
        final int handleWidth = this.mHandleDrawable.getIntrinsicWidth();
        this.mHotspotX = handleWidth / 4.0f;
        this.mHandleHeight = this.mHandleDrawable.getIntrinsicHeight();
        this.mTouchOffsetY = -this.mHandleHeight * 0.3f;
        this.mHotspotY = 0;
        this.invalidate();
    }

    private void show() {
        if (!this.isPositionVisible()) {
            this.hide();
            return;
        }
        // We remove handle from its parent first otherwise the following exception may be thrown
        // java.lang.IllegalStateException: The specified child already has a parent. You must call removeView() on the child's parent first.
        this.removeFromParent();
        // init the handle
        this.initHandle();
        // invalidate to make sure onDraw is called
        this.invalidate();
        int[] coords = this.mTempCoords;
        this.terminalView.getLocationInWindow(coords);
        coords[0] += this.mPointX;
        coords[1] += this.mPointY;
        if (null != mHandle)
            this.mHandle.showAtLocation(this.terminalView, 0, coords[0], coords[1]);
    }

    public final void hide() {
        this.mIsDragging = false;
        if (null != mHandle) {
            this.mHandle.dismiss();
            // We remove handle from its parent, otherwise it may still be shown in some cases even after the dismiss call
            this.removeFromParent();
            // garbage collect the handle
            this.mHandle = null;
        }
        this.invalidate();
    }

    private void removeFromParent() {
        if (!this.isParentNull()) {
            ((ViewManager) getParent()).removeView(this);
        }
    }

    public final void positionAtCursor(int cx, int cy, final boolean forceOrientationCheck) {
        final int x = this.terminalView.getPointX(cx);
        final int y = this.terminalView.getPointY(cy + 1);
        this.moveTo(x, y, forceOrientationCheck);
    }

    private void moveTo(final int x, final int y, final boolean forceOrientationCheck) {
        this.mPointX = (int) (x - this.mHotspotX);
        this.mPointY = y;
        this.checkChangedOrientation(forceOrientationCheck);
        if (this.isPositionVisible()) {
            int[] coords = null;
            if (this.isShowing()) {
                coords = this.mTempCoords;
                this.terminalView.getLocationInWindow(coords);
                final int x1 = coords[0] + this.mPointX;
                final int y1 = coords[1] + this.mPointY;
                if (null != mHandle)
                    this.mHandle.update(x1, y1, this.getWidth(), this.getHeight());
            } else {
                this.show();
            }
            if (this.mIsDragging) {
                if (null == coords) {
                    coords = this.mTempCoords;
                    this.terminalView.getLocationInWindow(coords);
                }
                if (coords[0] != this.mLastParentX || coords[1] != this.mLastParentY) {
                    this.mTouchToWindowOffsetX += coords[0] - this.mLastParentX;
                    this.mTouchToWindowOffsetY += coords[1] - this.mLastParentY;
                    this.mLastParentX = coords[0];
                    this.mLastParentY = coords[1];
                }
            }
        } else {
            this.hide();
        }
    }

    private void checkChangedOrientation(final boolean force) {
        if (!this.mIsDragging && !force) {
            return;
        }
        final long millis = SystemClock.currentThreadTimeMillis();
        if (50 > millis - mLastTime && !force) {
            return;
        }
        this.mLastTime = millis;
        TerminalView hostView = this.terminalView;
        int left = hostView.getLeft();
        int right = hostView.getWidth();
        int top = hostView.getTop();
        int bottom = hostView.getHeight();
        if (null == mTempRect) {
            this.mTempRect = new Rect();
        }
        Rect clip = this.mTempRect;
        clip.left = left + this.terminalView.getPaddingLeft();
        clip.top = top + this.terminalView.getPaddingTop();
        clip.right = right - this.terminalView.getPaddingRight();
        clip.bottom = bottom - this.terminalView.getPaddingBottom();
        ViewParent parent = hostView.getParent();
        if (null != parent) {
            parent.getChildVisibleRect(hostView, clip, null);
        }
    }

    private boolean isPositionVisible() {
        // Always show a dragging handle.
        if (this.mIsDragging) {
            return true;
        }
        TerminalView hostView = this.terminalView;
        final int left = 0;
        int right = hostView.getWidth();
        final int top = 0;
        int bottom = hostView.getHeight();
        if (null == mTempRect) {
            this.mTempRect = new Rect();
        }
        Rect clip = this.mTempRect;
        clip.left = left + this.terminalView.getPaddingLeft();
        clip.top = top + this.terminalView.getPaddingTop();
        clip.right = right - this.terminalView.getPaddingRight();
        clip.bottom = bottom - this.terminalView.getPaddingBottom();
        ViewParent parent = hostView.getParent();
        if (null == parent || !parent.getChildVisibleRect(hostView, clip, null)) {
            return false;
        }
        int[] coords = this.mTempCoords;
        hostView.getLocationInWindow(coords);
        int posX = coords[0] + this.mPointX + (int) this.mHotspotX;
        int posY = coords[1] + this.mPointY + (int) this.mHotspotY;
        return posX >= clip.left && posX <= clip.right && posY >= clip.top && posY <= clip.bottom;
    }

    @Override
    public final void onDraw(final Canvas c) {
        int width = this.mHandleDrawable.getIntrinsicWidth();
        final int height = this.mHandleDrawable.getIntrinsicHeight();
        this.mHandleDrawable.setBounds(0, 0, width, height);
        this.mHandleDrawable.draw(c);
    }


    @Override
    public final boolean onTouchEvent(final MotionEvent event) {
        this.terminalView.updateFloatingToolbarVisibility(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float rawX = event.getRawX();
                float rawY = event.getRawY();
                this.mTouchToWindowOffsetX = rawX - this.mPointX;
                this.mTouchToWindowOffsetY = rawY - this.mPointY;
                int[] coords = this.mTempCoords;
                this.terminalView.getLocationInWindow(coords);
                this.mLastParentX = coords[0];
                this.mLastParentY = coords[1];
                this.mIsDragging = true;
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                float rawX = event.getRawX();
                float rawY = event.getRawY();
                float newPosX = rawX - this.mTouchToWindowOffsetX + this.mHotspotX;
                float newPosY = rawY - this.mTouchToWindowOffsetY + this.mHotspotY + this.mTouchOffsetY;
                this.mCursorController.updatePosition(this, Math.round(newPosX), Math.round(newPosY));
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                this.mIsDragging = false;
        }
        return true;
    }

    @Override
    public final void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        this.setMeasuredDimension(this.mHandleDrawable.getIntrinsicWidth(), this.mHandleDrawable.getIntrinsicHeight());
    }

    public final int getHandleHeight() {
        return this.mHandleHeight;
    }

    private boolean isShowing() {
        if (null != mHandle)
            return this.mHandle.isShowing();
        else
            return false;
    }

    private boolean isParentNull() {
        return null == this.getParent();
    }

}
