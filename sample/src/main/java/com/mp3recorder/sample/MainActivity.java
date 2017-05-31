
package com.mp3recorder.sample;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.guagua.mp3recorder.MP3Recorder;
import com.guagua.mp3recorder.util.LameUtil;

import java.io.File;
import java.io.IOException;


public class MainActivity extends Activity implements LameUtil.LameWriteFinishCall, MP3Recorder.RecordDecibelListener, MP3Recorder.RecordTimeListener {


    private MP3Recorder mRecorder = new MP3Recorder();
    private TextView tvDbValue;
    private TextView tvTimeValue;
    private TextView tvWrite;
    private String mTime;
    private double mDecibelValue;
    public static Handler mHandler;
    private InnerHandlerThread innerHandlerThread;
    private boolean mStatus;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.mp3recorder.sample.R.layout.main);

        innerHandlerThread = new InnerHandlerThread("thread-handler");
        Button startButton = (Button) findViewById(com.mp3recorder.sample.R.id.StartButton);
        tvDbValue = ((TextView) findViewById(com.mp3recorder.sample.R.id.tvDbValue));
        tvTimeValue = ((TextView) findViewById(com.mp3recorder.sample.R.id.tvTimeValue));
        tvWrite = ((TextView) findViewById(com.mp3recorder.sample.R.id.tvWrite));
        LameUtil.setLameCallback(this);
        startButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    tvWrite.setVisibility(View.GONE);
                    mRecorder.setRecordFile(new File(Environment.getExternalStorageDirectory(), System.currentTimeMillis() + "---audio.mp3"));
                    mRecorder.start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        Button stopButton = (Button) findViewById(com.mp3recorder.sample.R.id.StopButton);
        stopButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mRecorder.stop();
                mHandler.removeCallbacksAndMessages(null);
            }
        });

        LameUtil.setDebug(true);
        mRecorder.setmRecordTimeListener(this);//录音时间
        mRecorder.setRecordDecibelListener(this);//分贝值
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRecorder.stop();
        mHandler.getLooper().quit();
        mHandler = null;
    }

    @Override
    public void lameWriteCallBack(String fileName) {
        Log.d("activity", "lame write callback:" + ",fileName:" + fileName);
    }

    @Override
    public void lameWriteCallBack(boolean status) {
        Log.d("activity", "lame write callback:" + status);
        mStatus = status;

    }

    @Override
    public void decibelValueCallback(double decibelValueCallback) {
        Log.d("activity", "decibel value:" + decibelValueCallback);
        mHandler.sendEmptyMessageDelayed(0, 100);
        mDecibelValue = decibelValueCallback;

    }

    @Override
    public void timeCallback(long startTime) {
        mTime = secToTime(startTime / 1000);

    }

    public String secToTime(long time) {
        String timeStr = null;
        long hour = 0;
        long minute = 0;
        long second = 0;
        if (time <= 0)
            return "00:00";
        else {
            minute = time / 60;
            if (minute < 60) {
                second = time % 60;
                timeStr = unitFormat(minute) + ":" + unitFormat(second);
            } else {
                hour = minute / 60;
                if (hour > 99)
                    return "99:59:59";
                minute = minute % 60;
                second = time - hour * 3600 - minute * 60;
                timeStr = unitFormat(hour) + ":" + unitFormat(minute) + ":" + unitFormat(second);
            }
        }
        return timeStr;
    }

    public String unitFormat(long i) {
        String retStr = null;
        if (i >= 0 && i < 10)
            retStr = "0" + Long.toString(i);
        else
            retStr = "" + i;
        return retStr;
    }

    private class InnerHandlerThread extends HandlerThread implements Handler.Callback {

        private final Runnable mRefreshTask;

        public InnerHandlerThread(String name) {
            super(name);
            start();
            mHandler = new Handler(this.getLooper(), this);
            mRefreshTask=new Runnable(){
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvDbValue.setText(String.valueOf(mDecibelValue));
                            tvWrite.setVisibility(mStatus ? View.VISIBLE : View.GONE);
                            tvTimeValue.setText(mTime + "seconds");
                        }
                    });

                }
            };
        }


        @Override
        public boolean handleMessage(Message message) {
            mHandler.post(mRefreshTask);
            return false;
        }
    }
}
