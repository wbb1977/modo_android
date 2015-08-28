package de.illogical.modo;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

final public class Player extends Thread implements OnAudioFocusChangeListener {

    private float audioFocusVolume = 1;

    private boolean pause = true;
    private boolean abort = false;
    private boolean fastForward = false;
    private Decoder decoder = null;
    private AudioManager audioManager = null;

    private Handler mHandler = null;
    private Message updateGuiMessage = null;

    private float fadeStep = 0.05f;
    private int songlength = 0;
    private int fadeInTime = 0;
    private int fadeOutTime = 0;
    private float vol = 1.0f;
    private boolean isFade = false;

    // One audiotrack for the process
    static AudioTrack at = null;

    public Player(Context c) {
        audioManager = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);		
        if (at == null)
            at = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    44100,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    17640 * 2 * 5,
                    AudioTrack.MODE_STREAM);

        setPriority(3);
    }

    void fastForward() {
        fastForward = true;
    }

    void noFastForward() {
        fastForward = false;
    }

    boolean isPlaying() {
        return !pause;
    }

    void pausePlayer() {
        pause = true;
    }

    void resumePlayer() {
        fastForward = false;
        pause = false;
    }

    void stopPlayer() {
        abort = true;
    }

    void setDecoder(Decoder decoder) {
        synchronized (Modo.sync) {
            this.decoder = decoder;
        }
    }

    void setHandler(Handler h) {
        mHandler = h;
    }

    void setFadeInOut(int seconds, int songlength) {
        // check songlength does not even allow to fade in / out one second just disable
        if (seconds <= 0 || songlength <= 3000) {
            disableFadeInOut();
            return;
        }
        isFade = true;
        this.songlength = songlength;
        // check songlength is not big enough to fade in and out => use one second to fade in / out.
        if ((seconds * 1000 * 2 + 1000) > songlength)
            seconds = 1;
        fadeStep = 1.0f / (seconds * 5);
        vol = 0 - fadeStep; // so fade in starts from silence
        fadeInTime = seconds * 1000;
        fadeOutTime = seconds * 1000;
    }

    void disableFadeInOut() {
        isFade = false;
    }

    void fade(short[] samples, int percent) {
        for (int i = 0, len = samples.length; i < len; ++i)
            samples[i] = (short) (samples[i] * percent / 100);
    }

    void requestAudioFocus () {
        int focusGranted = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        audioFocusVolume = (focusGranted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? 1.0f : 0.0f);
    }

    public void run() {
        short[] samples = { 0, 0, 0, 0, 0, 0, 0 };
        int playtime = 0;

        int focusGranted = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        audioFocusVolume = (focusGranted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED ? 1.0f : 0.0f);

        while(abort == false) {       	
            if (pause) {
                SystemClock.sleep(50);
            } else if (decoder != null) {

                int track = -1;
                int status = -1;

                synchronized (Modo.sync) {
                    if (fastForward)
                        decoder.forward();

                    samples = decoder.getSamples();
                    playtime = decoder.playtime();
                    track = decoder.getTrack();
                    status = decoder.getStatus();
                }

                if (abort == false && mHandler != null) {
                    updateGuiMessage = Message.obtain(mHandler, playtime, track, status);
                    mHandler.sendMessage(updateGuiMessage);
                }

                if (Modo.prefsSoundboost > 0) {
                    for (int i = 0, len = samples.length; i < len; ++i) {
                        int sample = samples[i] * Modo.prefsSoundboost;
                        if (sample > Short.MAX_VALUE)
                            samples[i] = Short.MAX_VALUE;
                        else if (sample < Short.MIN_VALUE)
                            samples[i] = Short.MIN_VALUE;
                        else
                            samples[i] = (short) sample;
                    }
                }

                if (isFade && (songlength - playtime) <= fadeOutTime) {  // handle fade out
                    vol = vol - (fadeStep * (fastForward ? 2 : 1));
                    fade(samples, vol < 0.0f ? 0 : (int)(vol * 100));
                } else if (isFade && playtime <= fadeInTime) { // handle fade in
                    vol = vol + (fadeStep * (fastForward ? 2 : 1));
                    fade(samples, vol > 1.0f ? 100 : (int)(vol * 100));
                } else if (isFade) {
                    vol = 1.0f; // restore vol for fade out
                }

                if (at.getState() == AudioTrack.STATE_INITIALIZED) {
                    at.setStereoVolume(audioFocusVolume, audioFocusVolume);
                    at.write(samples, 0, samples.length);
                    at.play();
                }
            } else {
                SystemClock.sleep(100);
            }
        }

        if (at.getState() == AudioTrack.STATE_INITIALIZED) {
            at.stop();
        }

        audioManager.abandonAudioFocus(this);
    }

    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                audioFocusVolume = 1.0f;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                audioFocusVolume = 0.3f;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS:
                audioFocusVolume = 0.0f;
                break;
        }

        if (mHandler != null)
            mHandler.sendMessage(Message.obtain(mHandler, Modo.MESSAGE_AUDIOFOCUS_CHANGE, focusChange, 0));
    }
}
