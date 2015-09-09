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

                if (Modo.prefsSoundboost > 0)
                    Mixer.boostVolume(samples, Modo.prefsSoundboost);

                Mixer.fade(samples, playtime, fastForward);

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
