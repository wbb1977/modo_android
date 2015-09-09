package de.illogical.modo;

/**
 * Created by wb on 9/7/15.
 */
public final class Mixer {

    static void convertToStereo(short[] monoSamples, short[] stereoSamples) {
        for (int i = 0, st = 0, len = monoSamples.length; i < len; ++i) {
            stereoSamples[st++] = monoSamples[i];
            stereoSamples[st++] = monoSamples[i];
        }
    }

    static void convertToMono(short[] stereoSamples) {
        for (int i = 0; i < stereoSamples.length; i = i + 2) {
            int monoSample = ((int)stereoSamples[i] + (int)stereoSamples[i + 1]) >> 1;
            stereoSamples[i] = (short)monoSample;
            stereoSamples[i + 1] = (short)monoSample;
        }
    }

    static void boostVolume(short[] samples, int boost) {
        for (int i = 0, len = samples.length; i < len; ++i) {
            int sample = samples[i] * boost;
            if (sample > Short.MAX_VALUE)
                samples[i] = Short.MAX_VALUE;
            else if (sample < Short.MIN_VALUE)
                samples[i] = Short.MIN_VALUE;
            else
                samples[i] = (short)sample;
        }
    }

    // Fade in out control
    private static float fadeStep = 0.05f;
    private static int songlength = 0;
    private static int fadeInTime = 0;
    private static int fadeOutTime = 0;
    private static float vol = 1.0f;
    private static boolean isFade = false;

    static void disableFadeInOut() {
        isFade = false;
    }

    static void setFadeInOut(int seconds, int songlength) {
        // check songlength does not even allow to fade in / out one second just disable
        if (seconds <= 0 || songlength <= 3000) {
            disableFadeInOut();
            return;
        }
        isFade = true;
        Mixer.songlength = songlength;
        // check songlength is not big enough to fade in and out => use one second to fade in / out.
        if ((seconds * 1000 * 2 + 1000) > songlength)
            seconds = 1;
        fadeStep = 1.0f / (seconds * 5);
        vol = 0 - fadeStep; // so fade in starts from silence
        fadeInTime = seconds * 1000;
        fadeOutTime = seconds * 1000;
    }

    static void fade(short[] samples, int playtime, boolean fastForward) {
        if (isFade && (songlength - playtime) <= fadeOutTime) {  // handle fade out
            vol = vol - (fadeStep * (fastForward ? 2 : 1));
            Mixer.adjustVolume(samples, vol < 0.0f ? 0 : (int)(vol * 100));
        } else if (isFade && playtime <= fadeInTime) { // handle fade in
            vol = vol + (fadeStep * (fastForward ? 2 : 1));
            Mixer.adjustVolume(samples, vol > 1.0f ? 100 : (int) (vol * 100));
        } else if (isFade) {
            vol = 1.0f; // restore vol for fade out
        }
    }

    static void adjustVolume(short[] samples, int percent) {
        for (int i = 0, len = samples.length; i < len; ++i)
            samples[i] = (short) (samples[i] * percent / 100);
    }
}
