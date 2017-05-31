package com.guagua.mp3recorder;

import android.media.AudioRecord;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import com.guagua.mp3recorder.util.LameUtil;
import com.guagua.mp3recorder.util.LogUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class DataEncodeThread extends HandlerThread implements AudioRecord.OnRecordPositionUpdateListener {
	private TimerTask mTask;
	private Timer mTimer;
	private File mDestFile;
	private StopHandler mHandler;
	private static final int PROCESS_STOP = 1;
	private byte[] mMp3Buffer;
	private FileOutputStream mFileOutputStream;

	private static class StopHandler extends Handler {
		
		private DataEncodeThread encodeThread;
		
		public StopHandler(Looper looper, DataEncodeThread encodeThread) {
			super(looper);
			this.encodeThread = encodeThread;
		}

		@Override
		public void handleMessage(Message msg) {
			if (msg.what == PROCESS_STOP) {
				//处理缓冲区中的数据
				while (encodeThread.processData() > 0);
				// Cancel any event left in the queue
				removeCallbacksAndMessages(null);
				encodeThread.flushAndRelease();
				getLooper().quit();
			}
		}
	}

	public DataEncodeThread(Timer timer, TimerTask timerTask, File file, int bufferSize) throws FileNotFoundException {
		super("DataEncodeThread");
		this.mTimer=timer;
		this.mTask=timerTask;
		this.mDestFile=file;
		this.mFileOutputStream = new FileOutputStream(file);
		mMp3Buffer = new byte[(int) (7200 + (bufferSize * 2 * 1.25))];
	}

	@Override
	public synchronized void start() {
		super.start();
		mTimer.scheduleAtFixedRate(mTask, 0, 1000);
		mHandler = new StopHandler(getLooper(), this);
	}

	private void check() {
		if (mHandler == null) {
			throw new IllegalStateException();
		}
	}

	public void sendStopMessage() {
		if (null!=mTask){
			mTask.cancel();
		}
		check();
		mHandler.sendEmptyMessage(PROCESS_STOP);
	}
	public Handler getHandler() {
		check();
		return mHandler;
	}

	@Override
	public void onMarkerReached(AudioRecord recorder) {
		// Do nothing		
	}

	@Override
	public void onPeriodicNotification(AudioRecord recorder) {
		processData();
	}

	private int processData() {	
		if (mTasks.size() > 0) {
			Task task = mTasks.remove(0);
			short[] buffer = task.getData();
			int readSize = task.getReadSize();
			int encodedSize = LameUtil.encode(buffer, buffer, readSize, mMp3Buffer);
			LogUtil.LOG_D("recorder","encodedSize:"+encodedSize);
			if (encodedSize > 0){
				try {
					mFileOutputStream.write(mMp3Buffer, 0, encodedSize);
				} catch (IOException e) {
                    e.printStackTrace();
				}
			}
			return readSize;
		}
		return 0;
	}
	

	private void flushAndRelease() {

		final int flushResult = LameUtil.flush(mMp3Buffer);
		if (flushResult > 0) {
			try {
				mFileOutputStream.write(mMp3Buffer, 0, flushResult);
			} catch (IOException e) {
				e.printStackTrace();
			}finally{
				if (mFileOutputStream != null) {
					try {
						mFileOutputStream.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				LameUtil.closeWithFile(mDestFile.getName());
//				LameUtil.close();
			}
		}
	}
	private List<Task> mTasks = Collections.synchronizedList(new ArrayList<Task>());
	public void addTask(short[] rawData, int readSize){
		mTasks.add(new Task(rawData, readSize));
	}
	private class Task{
		private short[] rawData;
		private int readSize;
		public Task(short[] rawData, int readSize){
			this.rawData = rawData.clone();
			this.readSize = readSize;
		}
		public short[] getData(){
			return rawData;
		}
		public int getReadSize(){
			return readSize;
		}
	}
}
