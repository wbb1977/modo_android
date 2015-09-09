package de.illogical.modo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

final class AsapDecoder implements Decoder {

    static {
        System.loadLibrary("asap");
    }

    private static native void asapReset();
    private static native int asapTracks();
    private static native int asapLoadFile(String path, int module_length, byte[] module);
    private static native int asapLoadFromZip(String zipfile, String entry);
    private static native int asapGetSamples(short[] samples);
    private static native int asapSetTrack(int track);
    private static native int asapTrackLength(int track);
    private static native int asapGetChannels();
    private static native void asapGetTitle(byte[] title);
    private static native void asapGetAuthor(byte[] author);
    private static native int asapGetYear();
    private static native int asapGetClock();
    private static native void asapSeek(int milli);

    private final int MAX_MODULE_LENGTH = 65000;
    private int isLoadingOkay;
    private int silence;
    private int currentTrack;
    private int fastForward;
    private long samplesPlayed = 0;
    private byte[] module = new byte[MAX_MODULE_LENGTH];
    private int channels; // 1 = mono, 2 = stereo;
    private short[] samplesMono = new short[17640 / 2];
    private short[] samplesStereo = new short[17640];
    private byte[] title = new byte[256];
    private byte[] author = new byte[256];
    private int silencePeriod = 20;
    private int playlistTrack = -1;
    private boolean isConvertStereoToMono = false;

    void setMonoOutput(boolean isConvertStereoToMono) {
        this.isConvertStereoToMono = isConvertStereoToMono;
    }

    void setSilenceDetection(int seconds) {
        silencePeriod = seconds * 5;
    }

    public void reset() {
        asapReset();
        isLoadingOkay = 0;
        silence = 0;
        currentTrack = 0;
        playlistTrack = -1;
        channels = 2;
        fastForward = 1;
    }

    public int getStatus() {
        if (isLoadingOkay == 0)
            return Decoder.STATUS_FILE_ERROR;

        if (silence >= silencePeriod)
            return Decoder.STATUS_SILENCE;

        return Decoder.STATUS_OK;
    }

    public void setPlaylistTrack(int track) {
        playlistTrack = track;
    }

    public int tracks() {
        return playlistTrack >= 0 ? 1 : asapTracks();
    }

    public int trackLength() {
        return asapTrackLength(currentTrack);
    }

    public void setTrack(int track) {
        if (track < 0)
            track = 0;
        if (track >= asapTracks())
            track = asapTracks() - 1;
        currentTrack = playlistTrack >= 0 ? playlistTrack : track;
        samplesPlayed = 0;
        silence = 0;
        fastForward = 1;
        asapSetTrack(currentTrack);
    }

    public int getTrack() {
        return currentTrack;
    }

    public void forward() {
        // absolute position in milliseconds
        asapSeek(playtime() + 200);
        fastForward = 2;
    }

    public int playtime() {
        return (int)(1000 * samplesPlayed / 88200);
    }

    public int loadFile(String path) {

        reset();
        isLoadingOkay = 0;

        File f = new File(path);

        if (f.exists() == false)
            return 0;
        if (f.isDirectory())
            return 0;
        if (f.length() > MAX_MODULE_LENGTH)
            return 0;

        int module_length = -1;

        try {
            FileInputStream fis = new FileInputStream(f);
            module_length = fis.read(module);
            fis.close();
        } catch (IOException e) {
            return 0;
        }

        if (module_length <= 0)
            return 0;

        isLoadingOkay = asapLoadFile(path, module_length, module);
        if (isLoadingOkay == 1) {
            channels = asapGetChannels();
        }

        return isLoadingOkay;
    }

    public int loadFromZip(String zipFile, String zipEntry) {
        reset();
        if (zipFile == null)
            return 0;
        if (zipEntry == null)
            return 0;
        isLoadingOkay = asapLoadFromZip(zipFile, zipEntry);
        if (isLoadingOkay > 0) {
            channels = asapGetChannels();
        }
        return isLoadingOkay;
    }

    public short[] getSamples() {
        if (channels == 1) { // mono
            asapGetSamples(samplesMono);
            Mixer.convertToStereo(samplesMono, samplesStereo);// Mix the mono data into stereo data
        } else { // stereo
            asapGetSamples(samplesStereo);
            if (isConvertStereoToMono)
                Mixer.convertToMono(samplesStereo);
        }

        if (samplesStereo[1] == 0)
            ++silence;
        else
            silence = 0;

        samplesPlayed += samplesStereo.length * fastForward;
        fastForward = 1;
        return samplesStereo;
    }

    public boolean supportTrackLength() {
        return true;
    }

    public boolean isTrackerFormat() {
        return false;
    }

    public String getTrackInfo() {
        for (int i = 0, len = title.length; i < len; ++i) {
            title[i] = 0;
            author[i] = 0;
        }
        asapGetTitle(title);
        asapGetAuthor(author);

        String t = new String(title);
        String a = new String(author);

        StringBuffer sb = new StringBuffer(1024);
        sb.append(t.substring(0, t.indexOf(0)));
        sb.append("\n");
        sb.append(a.substring(0, a.indexOf(0)));
        sb.append("\n");

        if (asapGetYear() != -1)
            sb.append(asapGetYear());
        sb.append("\n");
        sb.append(asapGetClock() == 1 ? "NTSC - " : "PAL - ");
        sb.append(asapGetChannels() == 1 ? "Mono" : "Stereo");
        sb.trimToSize();

        return sb.toString();
    }
}