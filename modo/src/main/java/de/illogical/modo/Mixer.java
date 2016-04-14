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
    private static int songlength = 0;
    private static int fadeInTime = 0;
    private static int fadeOutTime = 0;
    private static float vol = 1.0f;

    private static boolean isFadeIn = false;
    private static boolean isFadeOut = false;
    private static float fadeInStep = 0.05f;
    private static float fadeOutStep = 0.05f;

    static void disableFadeInOut() {
        isFadeIn = false;
        isFadeOut = false;
        fadeOutTime = 0;
        fadeInTime = 0;
        fadeInStep = 1f;
        fadeOutStep = 1f;
        vol = 1.0f;
    }

    static void setFadeInOut(int fadeInSeconds, int fadeOutSeconds, int songlength) {
        Mixer.disableFadeInOut();

        // Dont fade:
        // * song is really short
        // * fadein and fadeout duration is longer then song duration
        if (songlength <= 3000)
            return;
        if (((fadeInSeconds + fadeOutSeconds) * 1100) >= songlength)
            return;

        Mixer.songlength = songlength;

        if (fadeOutSeconds > 0) {
            isFadeOut = true;
            fadeOutStep = 1.0f / (fadeOutSeconds * 5);
            fadeOutTime = fadeOutSeconds * 1000;
            vol = 1.0f;
        }

        if (fadeInSeconds > 0) {
            isFadeIn = true;
            fadeInStep = 1.0f / (fadeInSeconds * 5);
            fadeInTime = fadeInSeconds * 1000;
            vol = 0 - fadeInStep;
        }

    }

    static void fade(short[] samples, int playtime, boolean fastForward) {
        if (isFadeOut && (songlength - playtime) <= fadeOutTime) {  // handle fade out
            vol = vol - (fadeOutStep * (fastForward ? 2 : 1));
            Mixer.adjustVolume(samples, vol < 0.0f ? 0 : (int)(vol * 100));
            //Log.d("MIXER", "Playtime: " + playtime + " FadeOut Vol: " + vol);
        } else if (isFadeIn && playtime <= fadeInTime) { // handle fade in
            vol = vol + (fadeInStep * (fastForward ? 2 : 1));
            Mixer.adjustVolume(samples, vol > 1.0f ? 100 : (int) (vol * 100));
            //Log.d("MIXER", "Playtime: " + playtime + " FadeIn Vol: " + vol);
        } else {
            vol = 1.0f; // restore vol for fade out
        }
    }

    static void adjustVolume(short[] samples, int percent) {
        for (int i = 0, len = samples.length; i < len; ++i)
            samples[i] = (short) (samples[i] * percent / 100);
    }
}
