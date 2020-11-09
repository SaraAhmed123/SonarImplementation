package com.example.sonarimplementation;

import android.app.ActivityManager;
//import android.support.annotation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.AudioRecord;
import android.os.Build;
import androidx.annotation.NonNull;
import android.app.Fragment;
import android.os.Handler;
import android.media.AudioTrack;
import android.media.AudioFormat;
//import android.support.annotation.Nullable;
//import android.R;

import com.example.sonarimplementation.R;
//import com.example.machineproblem2.R;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.text.DecimalFormat;
//package javax.sound.sampled;

//import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;
public class Sonar extends AppCompatActivity {
    private static final int PERMISSION_CODE = 200;
    protected final int MIC_AMPL_HIGH = 32767;
    protected int VOLUME_LOW = 0;
    protected int VOLUME_HIGH = 15;
    private boolean permissionToRecordAccepted = false;
    private boolean permissionToWriteAccepted = false;
    //DoubleFFT_1D fft2 = new DoubleFFT_1D(2048);
    AudioRecord recorder;
    //  private final int sampleRate = 8000;
    private String[] permissions = {"android.permission.RECORD_AUDIO", "android.permission.WRITE_EXTERNAL_STORAGE"};
    private static Context context;
    Switch trigger;
    SeekBar volSeekBar;
    ProgressBar currentVolume;
    ProgressBar currentMic;
    AudioManager audioManager;
    ActivityManager activityManager;
    TextView micReading;
    static final int duration = 2; // seconds
    static final float amplitude = 1;
    static final int sampleRate = 44100; // Hz
    static final int numSamples = duration * sampleRate;
    static final double sample[] = new double[numSamples];
    static final float freqOfTone = 20000; // Hz
    static final byte generatedSnd[] = new byte[2 * numSamples];
    short[] re2 = new short[2048];
   static final float f1 = 6 * freqOfTone;
   static final float amplitude1 = 1;
   static final float twoPiF1 = 2 *(float) Math.PI * f1;
   static final  float twoPiF = 2 * (float)Math.PI * freqOfTone;
    static final float[] buffer = new float[(int) (duration * sampleRate)];
    static final byte[] byteBuffer = new byte[buffer.length * 2];
 //   static final double time = sample / sampleRate;
    //DoubleFFT_1D fft2 = new DoubleFFT_1D(2048);
    Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sonar);
// ConstraintLayout bgElement = /*(ConstraintLayout)*/findViewById(R.id.activity_main);
        trigger = (Switch) findViewById(R.id.enable_switch);

        volSeekBar = (SeekBar) findViewById(R.id.volume_seek);

        currentVolume = (ProgressBar) findViewById(R.id.current_volume_progress);

        currentMic = (ProgressBar) findViewById(R.id.current_mic_progress);

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);

        context = getApplicationContext();

        micReading = (TextView) findViewById(R.id.mic_disp);

        // tv=(TextView)findViewById(R.id.textView2);

        int requestCode = PERMISSION_CODE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permissions, requestCode);
        }

        trigger.setChecked(true);
        currentMic.setMax(MIC_AMPL_HIGH);
        currentVolume.setMax(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        currentVolume.setProgress(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
        volSeekBar.setMax(currentVolume.getMax());
        volSeekBar.setProgress(currentVolume.getProgress());
        micReading.setText(Double.toString(0.0));
        VOLUME_HIGH = currentVolume.getMax();
        VOLUME_LOW = currentVolume.getProgress();

        trigger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (trigger.isChecked() == false) {
                    SonarService.setIsServiceRunning(false);
                    micReading.setText(Double.toString(0.0));
                    currentMic.setProgress(0);
                } else {
                    if (SonarService.getIsServiceRunning() == false) {
                        SonarService.setIsServiceRunning(true);
                        SonarService.setIsBackgroundUI(false);
                        onStart();
                    }
                }
            }
        });

        volSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser == true) {
                    SonarService.setVolumeLow(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            System.out.println("Volume Down Pressed");
            int volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            volume -= 1;

            if (volume < 0) {
                volume = 0;
            }

            SonarService.setVolumeLow(volume);

            currentVolume.setProgress(volume);
            volSeekBar.setProgress(volume);
            return true;
        } else if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            System.out.println("Volume Up Pressed");
            int volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            volume += 1;

            if (volume > VOLUME_HIGH) {
                volume = VOLUME_HIGH;
            }

            SonarService.setVolumeLow(volume);

            currentVolume.setProgress(volume);
            volSeekBar.setProgress(volume);
            return true;
        }
        return false;
    }

    private BroadcastReceiver MICReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null) {
                int reading = intent.getIntExtra(SonarService.CURRENT_MIC_PROGRESS, 0);
                currentMic.setProgress(reading);
                double dbReading = intent.getDoubleExtra(SonarService.CURRENT_MIC_DB_PROGRESS, 0.0);
                //dbReading = 20 * Math.log10(dbReading / MIC_AMPL_HIGH);
               // dbReading = Math.round(dbReading * 100.0) / 100.0;
                micReading.setText(Double.toString(dbReading));
                int volume = intent.getIntExtra(SonarService.CURRENT_VOLUME_PROGRESS, 0);
                currentVolume.setProgress(volume);
            }
        }
    };

    public static Context getAppContext() {
        return context;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_CODE:
                permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                permissionToWriteAccepted = grantResults[1] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted) {
            onStop();
        }
        if (!permissionToWriteAccepted) {
            onStop();
        }
    }

    @Override
    protected void onStart() {
        System.out.println("OnStart");
        super.onStart();
        if (context != null) {
            IntentFilter intentFilter = new IntentFilter(SonarService.INTENT_TO_UPDATE);
            context.registerReceiver(MICReceiver, intentFilter);
            startService(new Intent(this, SonarService.class));
            genTone();
            playSound();

        } else {
            throw new RuntimeException("Unable to start Sonar Demutator");
        }
    }

    @Override
    public void onStop() {
        System.out.println("OnStop");
        super.onStop();
        SonarService.setIsBackgroundUI(true);
    }

    @Override
    public void onDestroy() {
        System.out.println("OnDestroy");
        super.onDestroy();
        if (trigger.isChecked() == false) {
            if (context != null) {
                SonarService.setIsServiceRunning(false);
                stopService(new Intent(this, SonarService.class));
                if (MICReceiver != null) {
                    context.unregisterReceiver(MICReceiver);
                }
            }
        }
    }

    @Override
    public void onResume() {
        System.out.println("OnResume");
        super.onResume();
        final Thread thread = new Thread(new Runnable() {
            public void run() {
                genTone();
              playSound();

            }
        });
        thread.start();
        SonarService.setIsBackgroundUI(false);
        trigger.setChecked(true);
    }

    public static void genTone() {

        for (int i = 0; i < numSamples; ++i) {
            //amplitude = SonarService.getAmplitude();
            sample[i] = Math.sin(2 * Math.PI * i / (sampleRate / freqOfTone));
            double time = sample[i] / sampleRate;
            buffer[i] = (float) amplitude * (float) Math.sin(twoPiF * time);
        }

        // convert to 16 bit pcm sound array

        for (int sample = 0; sample < buffer.length; sample++) {
            double time = sample / sampleRate;
            double f0Component = amplitude * Math.sin(twoPiF * time);
            double f1Component = amplitude1 * Math.sin(twoPiF1 * time);
            buffer[sample] = (float) (f0Component + f1Component);
        }

         int bufferIndex = 0;
        for (int i = 0; i < byteBuffer.length; i++) {
            final int x = (int) (buffer[bufferIndex++] * 32767.0);
            byteBuffer[i] = (byte) x;
            i++;
             byteBuffer[i] = (byte) (x >>> 8);
        }
    }

    public static void playSound() {
        final AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                (int) sampleRate, AudioFormat.CHANNEL_CONFIGURATION_MONO,
                AudioFormat.ENCODING_PCM_16BIT, byteBuffer.length,
                AudioTrack.MODE_STATIC);
        audioTrack.write(byteBuffer, 0, byteBuffer.length);
        audioTrack.play();

    }


}