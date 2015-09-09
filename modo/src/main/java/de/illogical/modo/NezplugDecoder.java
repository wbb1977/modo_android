package de.illogical.modo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

final class NezplugDecoder implements Decoder {

    static {
        System.loadLibrary("nezplug");
    }

    private static native void nezReset();
    private static native int nezTracks();
    private static native int nezLoadFile(String path);
    private static native int nezLoadFromZip(String zipFile, String zipEntry, byte[] str);
    private static native int nezGetSamples(short[] samples);
    private static native int nezSetTrack(int track);
    private static native int nezForward(long pos);

    private int fastForward;
    private int silence;
    private int silencePeriod = 20;
    private int isLoadingOkay;
    private int currentTrack;
    private long samplesPlayed = 0;
    private short[] samplesStereo = new short[17640];
    private int playlistTrack = -1;

    static private String author;
    static private String title;
    static private String copyright;

    private boolean isConvertStereoToMono = false;

    void setMonoOutput(boolean isConvertStereoToMono) {
        this.isConvertStereoToMono = isConvertStereoToMono;
    }

    void setSilenceDetection(int seconds) {
        silencePeriod = seconds * 5;
    }

    public void reset() {
        nezReset();
        isLoadingOkay = 0;
        currentTrack = 0;
        playlistTrack = -1;
        samplesPlayed = 0;
        silence = 0;
        fastForward = 1;
    }

    public int getStatus() {
        if (isLoadingOkay == 0)
            return Decoder.STATUS_FILE_ERROR;

        if (silence >= silencePeriod)
            return Decoder.STATUS_SILENCE;

        return Decoder.STATUS_OK;
    }

    public int tracks() {
        return playlistTrack >= 0 ? 1 : nezTracks();
    }

    public int trackLength() {
        return -1;
    }

    public void setPlaylistTrack(int track) {
        playlistTrack = track;
    }

    public void setTrack(int track) {
        if (track < 0)
            track = 0;
        if (track >= nezTracks())
            track = nezTracks();
        currentTrack = playlistTrack >= 0 ? playlistTrack : track;
        samplesPlayed = 0;
        silence = 0;
        fastForward = 1;
        // Nezplug starts at 1, if track is 0 it just do the next track
        nezSetTrack(currentTrack + 1);
    }

    public int getTrack() {
        return currentTrack;
    }

    public void forward() {
        nezForward((samplesPlayed / 2) + (17640 / 2));
        fastForward = 2;
    }

    public int playtime() {
        return (int)(1000 * samplesPlayed / 88200);
    }

    public int loadFile(String path) {
        reset();

        author = "<?>";
        copyright = "<?>";
        title = "<?>";

        try {
            File f = new File(path);
            if (f.isFile() && f.length() > 0x80) {

                byte[] str = new byte[0x80];

                FileInputStream fis = new FileInputStream(f);
                fis.read(str);
                fis.close();

                for (int i = 0x10; i < str.length; i++)
                    if (str[i] == 0) str[i] = ' ';

                if (str[0] == 'G' && str[1] == 'B' && str[2] == 'S') {
                    title = new String(str, 0x10, 32, "US-ASCII");
                    author = new String(str,0x30, 32, "US-ASCII");
                    copyright = new String(str, 0x50, 32, "US-ASCII");
                }
                if (str[0] == 'N' && str[1] == 'E' && str[2] == 'S' && str[3] == 'M') {
                    title = new String(str, 0x0E, 32, "US-ASCII");
                    author = new String(str,0x2E, 32, "US-ASCII");
                    copyright = new String(str, 0x4E, 32, "US-ASCII");
                }
            }
        } catch (IOException e) {}

        isLoadingOkay = nezLoadFile(path);

        return isLoadingOkay;
    }

    public int loadFromZip(String zipFile, String zipEntry) {
        reset();

        author = "<?>";
        copyright = "<?>";
        title = "<?>";

        byte[] str = new byte[0x80];
        isLoadingOkay = nezLoadFromZip(zipFile, zipEntry, str);

        // extract file details from first 80 uncompressed bytes
        for (int i = 0x10; i < str.length; i++)
            if (str[i] == 0) str[i] = ' ';

        try {
            if (str[0] == 'G' && str[1] == 'B' && str[2] == 'S') {
                title = new String(str, 0x10, 32, "US-ASCII");
                author = new String(str,0x30, 32, "US-ASCII");
                copyright = new String(str, 0x50, 32, "US-ASCII");
            }
        } catch (UnsupportedEncodingException e) {}

        return isLoadingOkay;
    }

    public short[] getSamples() {
        nezGetSamples(samplesStereo);

        samplesPlayed += samplesStereo.length * fastForward;
        fastForward = 1;

        if (samplesStereo[0] == 0 || samplesStereo[0] == 1 || samplesStereo[0] == -1)
            ++silence;
        else
            silence = 0;

        if (isConvertStereoToMono)
            Mixer.convertToMono(samplesStereo);

        return samplesStereo;
    }

    public boolean supportTrackLength() {
        return true;
    }

    public boolean isTrackerFormat() {
        return false;
    }

    public String getTrackInfo() {
        return title + "\n" + author + "\n" + copyright;
    }
}