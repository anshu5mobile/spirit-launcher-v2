package com.kdt;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.ArrayDeque;

import git.artdeell.mojo.LogDispatcher;
import git.artdeell.mojo.Logger;
import git.artdeell.mojo.R;

/**
 * A class able to display logs to the user.
 * It has support for the Logger class
 */
public class LoggerView extends ConstraintLayout {
    private Logger.eventLogListener mLogListener;
    private ToggleButton mLogToggle;
    private DefocusableScrollView mScrollView;
    private TextView mLogTextView;

    // Log lines arrive far faster than a TextView can lay them out: they are queued and shown a few times a second,
    // and only the newest lines are kept, so the text never grows into a huge block that stalls the game start.
    private static final int MAX_LINES = 300;
    private static final long FLUSH_MS = 250L;
    private final ArrayDeque<String> mPending = new ArrayDeque<>();
    private boolean mFlushScheduled;
    private final Runnable mFlush = new Runnable() {
        @Override
        public void run() {
            String joined;
            synchronized (mPending) {
                mFlushScheduled = false;
                if(mPending.isEmpty()) return;
                StringBuilder sb = new StringBuilder(mPending.size() * 80);
                for(String line : mPending) sb.append(line).append('\n');
                joined = sb.toString();
            }
            if(mLogTextView.getVisibility() != VISIBLE) return;
            mLogTextView.setText(joined);
            if(mScrollView.isKeepFocusing()) mScrollView.fullScroll(View.FOCUS_DOWN);
        }
    };


    public LoggerView(@NonNull Context context) {
        this(context, null);
    }

    public LoggerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        // Triggers the log view shown state by default when viewing it
        mLogToggle.setChecked(visibility == VISIBLE);
    }

    /**
     * Inflate the layout, and add component behaviors
     */
    private void init(){
        inflate(getContext(), R.layout.view_logger, this);
        mLogTextView = findViewById(R.id.content_log_view);
        mLogTextView.setTypeface(Typeface.MONOSPACE);
        //TODO clamp the max text so it doesn't go oob
        mLogTextView.setMaxLines(Integer.MAX_VALUE);
        mLogTextView.setEllipsize(null);
        mLogTextView.setVisibility(GONE);

        // Toggle log visibility
        mLogToggle = findViewById(R.id.content_log_toggle_log);
        mLogToggle.setOnCheckedChangeListener(
                (compoundButton, isChecked) -> {
                    mLogTextView.setVisibility(isChecked ? VISIBLE : GONE);
                    if(isChecked) {
                        LogDispatcher.setViewListener(mLogListener);
                    }else{
                        mLogTextView.setText("");
                        synchronized (mPending) { mPending.clear(); }
                        LogDispatcher.setViewListener(null); // Makes the JNI code be able to skip expensive logger callbacks
                        // NOTE: was tested by rapidly smashing the log on/off button, no sync issues found :)
                    }
                });
        mLogToggle.setChecked(false);

        // Remove the loggerView from the user View
        ImageButton cancelButton = findViewById(R.id.log_view_cancel);
        cancelButton.setOnClickListener(view -> LoggerView.this.setVisibility(GONE));

        // Set the scroll view
        mScrollView = findViewById(R.id.content_log_scroll);
        mScrollView.setKeepFocusing(true);

        //Set up the autoscroll switch
        ToggleButton autoscrollToggle = findViewById(R.id.content_log_toggle_autoscroll);
        autoscrollToggle.setOnCheckedChangeListener(
                (compoundButton, isChecked) -> {
                    if(isChecked) mScrollView.fullScroll(View.FOCUS_DOWN);
                    mScrollView.setKeepFocusing(isChecked);
                }
        );
        autoscrollToggle.setChecked(true);

        // Listen to logs
        mLogListener = text -> {
            if(mLogTextView.getVisibility() != VISIBLE) return;
            synchronized (mPending) {
                mPending.addLast(text);
                while(mPending.size() > MAX_LINES) mPending.pollFirst();
                if(mFlushScheduled) return;
                mFlushScheduled = true;
            }
            postDelayed(mFlush, FLUSH_MS);
        };
    }

}
