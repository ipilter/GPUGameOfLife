package com.banditsoft.gpu_gameoflife;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;

public class MainActivity extends Activity {
    private GLSurface mGLSurface;
    private EditText mDeadRuleEditText;
    private EditText mLiveRuleEditText;
    private Button mStartStopButton;

    private long mPeriod = 100;
    private int mPeriodMinimum = 50;
    private int mPeriodMaximum = 1000;
    private boolean mIsTimerRunning = false;

    public Handler mSimulationHandler = new Handler();
    private Runnable mRequestSimulationRunnable = new Runnable() {
        @Override
        public void run() {
            mGLSurface.requestSimulation();
            mSimulationHandler.postDelayed(mRequestSimulationRunnable, mPeriod);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mGLSurface = findViewById(R.id.gl_surface);
        mDeadRuleEditText = findViewById(R.id.deadRuleEditText);
        mLiveRuleEditText = findViewById(R.id.liveRuleEditText);
        mStartStopButton = findViewById(R.id.simulationButton);

        Button resetButton = findViewById(R.id.resetButton);
        Button noiseButton = findViewById(R.id.noiseButton);
        SeekBar speedSeekBar = findViewById(R.id.speedseekBar);
        speedSeekBar.setProgress((int)mPeriod);
        speedSeekBar.setMax(mPeriodMaximum);
        speedSeekBar.setOnSeekBarChangeListener(mOnSeekBarChangeListener);

        mStartStopButton.setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(View v) {
                        if(mIsTimerRunning) {
                            mSimulationHandler.removeCallbacks(mRequestSimulationRunnable);
                            mStartStopButton.setText("Start");
                            mIsTimerRunning = false;
                        } else {
                            mGLSurface.setRules(Integer.parseInt(mDeadRuleEditText.getText().toString()), Integer.parseInt(mLiveRuleEditText.getText().toString()));
                            mSimulationHandler.postDelayed(mRequestSimulationRunnable, mPeriod);
                            mStartStopButton.setText("Stop");
                            mIsTimerRunning = true;
                        }
                    }
                });
        resetButton.setOnClickListener(
            new View.OnClickListener() {
                public void onClick(View v) {
                    mGLSurface.setRules(Integer.parseInt(mDeadRuleEditText.getText().toString()), Integer.parseInt(mLiveRuleEditText.getText().toString()));
                    mGLSurface.reset();
                }
            });
        noiseButton.setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(View v) {
                        mGLSurface.requestNoise();
                    }
                });
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        if(mGLSurface == null) {
            return;
        }
        mGLSurface.onPause();
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        if(mGLSurface == null) {
            return;
        }
        mGLSurface.onResume();
    }

    private SeekBar.OnSeekBarChangeListener mOnSeekBarChangeListener =
            new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean b) {
                    if (progress < mPeriodMinimum) {
                        mPeriod = mPeriodMinimum;
                        return;
                    }
                    mPeriod = seekBar.getProgress();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) { }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) { }
            };

}
