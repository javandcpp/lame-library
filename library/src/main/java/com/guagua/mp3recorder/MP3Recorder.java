package com.guagua.mp3recorder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import com.guagua.mp3recorder.util.LameUtil;
import com.guagua.mp3recorder.util.LogUtil;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class MP3Recorder {
    private static final int DEFAULT_AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
    private static final int DEFAULT_SAMPLING_RATE = 48000;//模拟器仅支持从麦克风输入8kHz采样率
    private static final int DEFAULT_CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final PCMFormat DEFAULT_AUDIO_FORMAT = PCMFormat.PCM_16BIT;

    private static final int DEFAULT_LAME_MP3_QUALITY = 7;
    private static final int DEFAULT_LAME_IN_CHANNEL = 1;
    private static final int DEFAULT_LAME_MP3_BIT_RATE = 32;

    private static final int FRAME_COUNT = 160;
    private RecordDecibelListener mRecordDecibelListener;
    private RecordTimeListener mRecordTimeListener;
    private AudioRecord mAudioRecord = null;
    private int mBufferSize;
    private short[] mPCMBuffer;
    private DataEncodeThread mEncodeThread;
    private boolean mIsRecording = false;
    private File mRecordFile;
    private TimerTask task;
    private Timer timer = new Timer();

    /**
     * 录音开始时间值
     *
     * @param mRecordTimeListener
     */
    public void setmRecordTimeListener(RecordTimeListener mRecordTimeListener) {
        this.mRecordTimeListener = mRecordTimeListener;
    }

    /**
     *
     */
    public MP3Recorder() {

    }

    /**
     * @param recordFile target file
     */
    public MP3Recorder(File recordFile) {
        mRecordFile = recordFile;
    }

    /**
     * 目标文件,与分贝回调
     *
     * @param recordFile
     * @param recordDecibelListener
     */
    public MP3Recorder(File recordFile, RecordDecibelListener recordDecibelListener) {
        this.mRecordFile = recordFile;
        this.mRecordDecibelListener = recordDecibelListener;
    }

    /**
     * 设置写入目标文件
     *
     * @param recordFile
     */
    public void setRecordFile(File recordFile) {
        this.mRecordFile = recordFile;
    }

    /**
     * 录音分贝值回调
     *
     * @param recordDecibelListener
     */
    public void setRecordDecibelListener(RecordDecibelListener recordDecibelListener) {
        this.mRecordDecibelListener = recordDecibelListener;
    }

    public void start() throws IOException {
        if (mIsRecording) {
            return;
        }
        mIsRecording = true; // 提早，防止init或startRecording被多次调用
        initAudioRecorder();
        mAudioRecord.startRecording();
        new Thread() {
            @Override
            public void run() {
                //设置线程权限
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                while (mIsRecording) {
                    int readSize = mAudioRecord.read(mPCMBuffer, 0, mBufferSize);
                    LogUtil.LOG_D("recorder", "readSize:" + readSize);
                    if (readSize > 0) {
                        mEncodeThread.addTask(mPCMBuffer, readSize);
                        calculateRealVolume(mPCMBuffer, readSize);
                    }
                }
                // release and finalize audioRecord
                mAudioRecord.stop();
                mAudioRecord.release();
                mAudioRecord = null;
                // stop the encoding thread and try to wait
                // until the thread finishes its job
                mEncodeThread.sendStopMessage();
            }

            /**
             * 此计算方法来自samsung开发范例
             *
             * @param buffer buffer
             * @param readSize readSize
             */
            private void calculateRealVolume(short[] buffer, int readSize) {
                double sum = 0;
                for (int i = 0; i < readSize; i++) {
                    // 这里没有做运算的优化，为了更加清晰的展示代码
                    sum += buffer[i] * buffer[i];
                }
                if (readSize > 0) {
                    double amplitude = sum / readSize;
                    mVolume = (int) Math.sqrt(amplitude);
                    double decibelValue = 10 * Math.log10(mVolume);
                    if (null != mRecordDecibelListener) {
                        mRecordDecibelListener.decibelValueCallback(decibelValue);//分贝回调
                    }
                }
            }
        }.start();
    }

    private int mVolume;

    /**
     * 获取真实的音量。 [算法来自三星]
     *
     * @return 真实音量
     */
    public int getRealVolume() {
        return mVolume;
    }

    /**
     * 获取相对音量。 超过最大值时取最大值。
     *
     * @return 音量
     */
    public int getVolume() {
        if (mVolume >= MAX_VOLUME) {
            return MAX_VOLUME;
        }
        return mVolume;
    }

    private static final int MAX_VOLUME = 2000;


    /**
     * 根据资料假定的最大值。 实测时有时超过此值。
     *
     * @return 最大音量值。
     */
    public int getMaxVolume() {
        return MAX_VOLUME;
    }

    public void stop() {
        mIsRecording = false;
    }

    public boolean isRecording() {
        return mIsRecording;
    }

    private void initAudioRecorder() throws IOException {
        mBufferSize = AudioRecord.getMinBufferSize(DEFAULT_SAMPLING_RATE,
                DEFAULT_CHANNEL_CONFIG, DEFAULT_AUDIO_FORMAT.getAudioFormat());

        int bytesPerFrame = DEFAULT_AUDIO_FORMAT.getBytesPerFrame();
        int frameSize = mBufferSize / bytesPerFrame;
        if (frameSize % FRAME_COUNT != 0) {
            frameSize += (FRAME_COUNT - frameSize % FRAME_COUNT);
            mBufferSize = frameSize * bytesPerFrame;
        }

        mAudioRecord = new AudioRecord(DEFAULT_AUDIO_SOURCE,
                DEFAULT_SAMPLING_RATE, DEFAULT_CHANNEL_CONFIG, DEFAULT_AUDIO_FORMAT.getAudioFormat(),
                mBufferSize);

        mPCMBuffer = new short[mBufferSize];
        /*
		 *
		 */
        LameUtil.init(DEFAULT_SAMPLING_RATE, DEFAULT_LAME_IN_CHANNEL, DEFAULT_SAMPLING_RATE, DEFAULT_LAME_MP3_BIT_RATE, DEFAULT_LAME_MP3_QUALITY);
        final long[] i = {0};
        task = new TimerTask() {
            @Override
            public void run() {
                if (null != mRecordTimeListener) {
                    ++i[0];
                    mRecordTimeListener.timeCallback(i[0] * 1000);
                }
            }
        };
        mEncodeThread = new DataEncodeThread(timer, task, mRecordFile, mBufferSize);
        mEncodeThread.start();
        mAudioRecord.setRecordPositionUpdateListener(mEncodeThread, mEncodeThread.getHandler());
        mAudioRecord.setPositionNotificationPeriod(FRAME_COUNT);

    }

    public interface RecordDecibelListener {
        void decibelValueCallback(double decibelValueCallback);
    }

    public interface RecordTimeListener {
        void timeCallback(long startTime);
    }
}