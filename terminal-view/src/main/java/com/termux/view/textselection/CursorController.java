package com.termux.view.textselection;

import android.view.MotionEvent;
import android.view.ViewTreeObserver;

import com.termux.view.TerminalView;

/**
 * A CursorController instance can be used to control cursors in the text.
 * It is not used outside of {@link TerminalView}.
 */
public interface CursorController extends ViewTreeObserver.OnTouchModeChangeListener {

    /**
     * Show the cursors on screen. Will be drawn by {@link #render()} by a call during onDraw.
     * See also {@link #hide()}.
     */
    void show(MotionEvent event);

    /**
     * Hide the cursors from screen.
     * See also {@link #show(MotionEvent event)}.
     */
    boolean hide();

    /**
     * Render the cursors.
     */
    void render();

    /**
     * Update the cursor positions.
     */
    void updatePosition(TextSelectionHandleView handle, int x, int y);


    /**
     * @return true if the cursors are currently active.
     */
    boolean isActive();
}
