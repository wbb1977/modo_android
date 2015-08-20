package de.illogical.modo;

import android.os.SystemClock;

final class MikModDecoder implements Decoder {

    static {
        System.loadLibrary("mikmod");
    }

    // Mikmod mixer modes
    // These ones take effect only after MikMod_Init or MikMod_Reset */
    final int DMODE_16BITS     = 0x0001; /* enable 16 bit output */
    final int DMODE_STEREO     = 0x0002; /* enable stereo output */
    final int DMODE_SOFT_SNDFX = 0x0004; /* Process sound effects via software mixer */
    final int DMODE_SOFT_MUSIC = 0x0008; /* Process music via software mixer */
    final int DMODE_HQMIXER    = 0x0010; /* Use high-quality (slower) software mixer */
    // These take effect immediately. */
    final int DMODE_SURROUND   = 0x0100; /* enable surround sound */
    final int DMODE_INTERP     = 0x0200; /* enable interpolation */
    final int DMODE_REVERSE    = 0x0400; /* reverse stereo */

    private final int 		MIKMOD_DEFAULT_MIXER_MODE	= DMODE_STEREO | DMODE_16BITS | DMODE_SURROUND | DMODE_SOFT_MUSIC | DMODE_SOFT_SNDFX;
    private final int 				NORMAL_MIXER_MODE	= DMODE_STEREO | DMODE_16BITS | DMODE_SOFT_MUSIC | DMODE_SOFT_SNDFX;
    private final int		INTERPOLATION_MIXER_MODE	= DMODE_STEREO | DMODE_16BITS | DMODE_SOFT_MUSIC | DMODE_SOFT_SNDFX | DMODE_INTERP;

    private final static String TAG = "MikModDecoder";
    private final static int MAX_LOOPS = 3;

    private native static void mikmodReset();
    private native static int mikmodLoadFile(String path);
    private native static int mikmodLoadFromZip(String zipFile, String zipEntry);
    private native static int mikmodTracks();
    private native static int mikmodSetTrack(int track);
    private native static int mikmodGetSamples(short[] samples);
    private native static int mikmodGetTrack();
    private native static int mikmodIsPlayerActive();
    private native static void mikmodSetMixerMode(int mode);
    private native static void mikmodSetStereoSeparation(int separation);

    // We can not directly create UTF String in JNI. Instrument and samples names from the
    // Amiga area does not follow any standards and thus are not UTF safe.
    private native static int mikmodGetInstrumentsCount();
    private native static int mikmodGetSamplesCount();

    private native static int mikmodGetSampleLength(int number);
    private native static int mikmodGetSampleBytes(int number, byte[] chars, int length);

    private native static int mikmodGetInstrumentLength(int number);
    private native static int mikmodGetInstrumentBytes(int number, byte[] chars, int length);

    private native static int mikmodGetCommentLength();
    private native static int mikmodGetCommentBytes(byte[] chars, int length);

    private native static int mikmodGetTitleLength();
    private native static int mikmodGetTitleBytes(byte[] chars, int length);

    private native static int mikmodGetTrackerLength();
    private native static int mikmodGetTrackerBytes(byte[] chars, int length);


    private short[] samples = new short[17640];
    private long samplesPlayed = 0;
    private int loops = 0;
    private int isLoadingOkay = 0;
    private long looptime = 0;

    void isInterpolationMixingEnabled(boolean isInterpolation) {
        mikmodSetMixerMode(isInterpolation ? INTERPOLATION_MIXER_MODE : NORMAL_MIXER_MODE);
    }

    void setStereoSeparation(int stereoSeparation) {
        mikmodSetStereoSeparation(stereoSeparation);
    }

    public void reset() {
        mikmodReset();
        samplesPlayed = 0;
        loops = 0;
        looptime = SystemClock.elapsedRealtime();
        isLoadingOkay = 0;
    }

    public int tracks() {
        return mikmodTracks();
    }

    public int trackLength() {
        return -1;
    }

    public void setPlaylistTrack(int track) {} // no effect on tracker files

    public void setTrack(int track) {
        mikmodSetTrack(track);
        loops = 0;
        looptime = SystemClock.elapsedRealtime();
    }

    public void forward() {}

    public int playtime() {
        return  (int)(1000 * samplesPlayed / 88200);
    }

    public int loadFile(String path) {
        reset();
        isLoadingOkay =  mikmodLoadFile(path);
        return isLoadingOkay;
    }

    public int loadFromZip(String zipFile, String zipEntry) {
        reset();
        isLoadingOkay = mikmodLoadFromZip(zipFile, zipEntry);
        return isLoadingOkay;
    }

    public short[] getSamples() {
        int trackBefore = mikmodGetTrack();
        mikmodGetSamples(samples);
        int trackAfter = mikmodGetTrack();

        // Detect a loop / backward jump
        // but only if we played already 4s since last jump, so it can happen that we go on forever in same modules
        if (trackAfter < trackBefore) {
            if ((SystemClock.elapsedRealtime() - looptime) > 4000) {
                ++loops;
            }
            looptime = SystemClock.elapsedRealtime();
        }

        samplesPlayed += samples.length;
        return samples;
    }

    public int getTrack() {
        return mikmodGetTrack();
    }

    public boolean supportTrackLength() {
        return false;
    }

    public int getStatus() {
        if (isLoadingOkay == 0)
            return Decoder.STATUS_FILE_ERROR;

        // check if we looped enough
        if (loops >= MAX_LOOPS || mikmodIsPlayerActive() != 1)
            return Decoder.STATUS_EOF;

        return Decoder.STATUS_OK;
    }

    public boolean isTrackerFormat() {
        return true;
    }

    public String getTrackInfo() {
        StringBuffer b = new StringBuffer(5000);

        byte[] title = new byte[mikmodGetTitleLength()];
        mikmodGetTitleBytes(title, title.length);
        if (title.length > 0) {
            b.append(new String(title));
            b.append("\n");
        }

        byte[] comment = new byte[mikmodGetCommentLength()];
        mikmodGetCommentBytes(comment, comment.length);

        if (comment.length > 0) {
            b.append(new String(comment));
            b.append("\n");
        }

        int numsmp = mikmodGetSamplesCount();
        for (int i = 0; i < numsmp; ++i) {
            byte[] chars = new byte[mikmodGetSampleLength(i)];
            mikmodGetSampleBytes(i, chars, chars.length);
            b.append(String.format("\nS%03d: %s",i , new String(chars)));
        }

        b.append("\n");

        int numins = mikmodGetInstrumentsCount();
        for (int i = 0; i < numins; ++i) {
            byte[] chars = new byte[mikmodGetInstrumentLength(i)];
            mikmodGetInstrumentBytes(i, chars, chars.length);
            b.append(String.format("\nI%03d: %s",i , new String(chars)));
        }

        b.append("\n");

        byte[] tracker = new byte[mikmodGetTrackerLength()];
        mikmodGetTrackerBytes(tracker, tracker.length);
        if (tracker.length > 0) {
            b.append("\n");
            b.append(new String(tracker));
        }

        b.trimToSize();

        return b.toString();
    }
}