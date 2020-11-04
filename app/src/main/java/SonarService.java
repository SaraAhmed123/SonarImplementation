package com.example.sonarimplementation;

import android.app.Service;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

public class SonarService extends Service {
    public SonarService() {

    }

    public static final String INTENT_TO_UPDATE = "updateValues";
    public final static String CURRENT_MIC_PROGRESS = "micProgress";
    public final static String CURRENT_MIC_DB_PROGRESS = "micDoubleProgress";
    public final static String CURRENT_VOLUME_PROGRESS = "volumeProgress";
    private static boolean updateCurrentVolume = true;
    private static double predictedVolume = 0.0;
    private static int VOLUME_LOW = 0;
    private static int VOLUME_HIGH = 15;
    private static final int UPDATE_VOLUME_DELAY = 300;
    private static boolean isServiceRunning = false;
    private static boolean isBackgroundUI = false;
    private final double MIC_FILTER = 0.6;
    private final int MIC_AMPL_HIGH = 32767;
    static double[] drawingBufferForPlayer = new double[100];
    private final int MIC_AMPL_LOW = 0;
    int counterPlayer = 0;
    private Intent progressIntent;

    AudioManager audioManager = null;
    MediaRecorder mediaRecorder = null;

    public static void setVolumeLow(int volume) {
        VOLUME_LOW = volume;
    }

    public static void setIsServiceRunning(boolean running) {
        isServiceRunning = running;
        updateCurrentVolume = running;
    }

    public static void setIsBackgroundUI(boolean isBackground) {
        isBackgroundUI = isBackground;
    }

    public static boolean getIsServiceRunning() {
        return isServiceRunning;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {

        isBackgroundUI = false;
        progressIntent = new Intent(INTENT_TO_UPDATE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        VOLUME_LOW = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setOutputFile("/dev/null");

        try {
            mediaRecorder.prepare();
        } catch(IOException e) {
            e.printStackTrace();
        }

        mediaRecorder.start();
    }

    public double getAmplitude() {
        if (mediaRecorder != null)
            return  (mediaRecorder.getMaxAmplitude());
        else
            return 0;

    }
    public double getAmplitudeEMA(byte [] generatedSnd) {
        double amplitude = getAmplitude () ;
        double amplitudeDb;
        for (int i = 0; i < generatedSnd.length/2; i++) {
            //double y = (generatedSnd[i*2] | generatedSnd[i*2+1] << 8) / 32768.0;
            // depending on your endianness:
             double y = (generatedSnd[i*2]<<8 | generatedSnd[i*2+1]) / 32768.0;
            amplitude += Math.abs(y);
        }
      return amplitude = amplitude / generatedSnd.length / 2;
      // amplitudeDb = 20 * Math.log10((double)Math.abs(amplitude));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        isServiceRunning = true;
        updateCurrentVolume = true;

        Thread volUpdate = new Thread(new Runnable() {
            @Override
            public void run() {
                while (updateCurrentVolume) {
                    Double reading = getAmplitudeEMA(Sonar.generatedSnd);
                    int micReading = (int)Math.round(reading);
                    int volume = (int)Math.round(Math.abs((reading/(MIC_AMPL_HIGH - MIC_AMPL_LOW))*(VOLUME_HIGH - VOLUME_LOW)));
                    if (volume < VOLUME_LOW) {
                        volume = VOLUME_LOW;
                    }

                    if (volume > VOLUME_HIGH) {
                        volume = VOLUME_HIGH;
                    }

                    if (progressIntent != null && isBackgroundUI == false) {
                        progressIntent.putExtra(CURRENT_MIC_PROGRESS, micReading);
                        progressIntent.putExtra(CURRENT_MIC_DB_PROGRESS, reading);
                        progressIntent.putExtra(CURRENT_VOLUME_PROGRESS, volume);
                        Sonar.genTone();
                        Sonar.playSound();
                        try {
                            if (Sonar.getAppContext() != null){
                                Sonar.getAppContext().sendBroadcast(progressIntent);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        progressIntent = new Intent(INTENT_TO_UPDATE);
                    }

                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.AUDIOFOCUS_NONE);
                    try {
                        Thread.sleep(UPDATE_VOLUME_DELAY);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                }
            }
        });

        volUpdate.start();
        return START_NOT_STICKY;
    }

    public void onDestroy() {
        System.out.println("OnDestroy Service");
        setIsServiceRunning(false);
        mediaRecorder.stop();
        mediaRecorder = null;
    }
}
