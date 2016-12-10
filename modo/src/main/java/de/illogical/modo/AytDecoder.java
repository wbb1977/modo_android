package de.illogical.modo;

final class AytDecoder implements Decoder {

    static {
        System.loadLibrary("unzip");
        System.loadLibrary("gme_spc");
    }
    // Standard spc
    static native void aytReset();
    static native int aytTracks();
    static native int aytLoadFile(String path);
    static native int aytLoadFromZip(String zipfile, String entry);
    static native int aytGetSamples(short[] samples);
    static native int aytSetTrack(int track);
    static native int aytTrackLength(int track);
    static native int aytPlaytime();
    static native int aytSeek(long milli);
    static native void aytGetTrackInfo(int track, int what, byte[] s);
    static native int aytGetTrackInfoLength(int track, int what);
    static native void aytSetStereoSeparation(int depth);
    static native int aytPlayerVersion();
    static native int aytSysPreset(int track);
    static native int aytFirstsong();

    private short[] samples = new short[17640];
    private int currentTrack = 0;
    private long samplesPlayed = 0;
    private int fastForward = 1;
    private int silence = 0;
    private int isLoadingOkay = 0;
    private int silencePeriod = 20;
    private int playlistTrack = -1;

    void setSilenceDetection(int seconds) {
        silencePeriod = seconds * 5;
    }

    public void setStereoSeparation(int sep) {
        // libkmikmod: 1d
        // 28 max stereo, 0 mono
        // libgme: 1.0 max stereo, 0.0 mono, /100.0 in C
        aytSetStereoSeparation(sep * 100/128);
    }


    public void reset() {
        aytReset();
        samplesPlayed = 0;
        currentTrack = 0;
        playlistTrack = -1;
        fastForward = 1;
        silence = 0;
        isLoadingOkay = 0;
    }

    public int getStatus() {
        if (isLoadingOkay == 0)
            return Decoder.STATUS_FILE_ERROR;

        if (silence >= silencePeriod)
            return Decoder.STATUS_SILENCE;

        return Decoder.STATUS_OK;
    }

    public int tracks() {
        return playlistTrack >= 0 ? 1 : aytTracks() / 2;
    }

    public int trackLength() {
        return aytTrackLength(currentTrack);
    }

    public void setPlaylistTrack(int track) {
        playlistTrack = track;
    }

    public void setTrack(int track) {
        if (track <= 0)
            track = 0;
        if (track >= tracks())
            track = tracks() - 1;
        currentTrack = playlistTrack >= 0 ? playlistTrack : track;
        samplesPlayed = 0;
        silence = 0;
        fastForward = 1;
        aytSetTrack(currentTrack * 2);
    }

    public int getTrack() {
        return currentTrack;
    }

    public void forward() {
        aytSeek(+200);
        fastForward = 2;
    }

    public int playtime() {
        return (int)(1000 * samplesPlayed / 88200);
    }

    public int loadFile(String path) {
        reset();
        if (path == null)
            return 0;
        isLoadingOkay = aytLoadFile(path);
        isLoadingOkay = (isLoadingOkay > 0 && aytPlayerVersion() == 16 && (aytTracks() % 2) == 0) ? 1 : 0;
        return isLoadingOkay;
    }

    public int loadFromZip(String zipFile, String zipEntry) {
        reset();
        if (zipFile == null)
            return 0;
        if (zipEntry == null)
            return 0;
        isLoadingOkay = aytLoadFromZip(zipFile, zipEntry);
        isLoadingOkay = (isLoadingOkay > 0 && aytPlayerVersion() == 16 && (aytTracks() % 2) == 0) ? isLoadingOkay : 0;
        return isLoadingOkay;
    }

    public short[] getSamples() {
        aytGetSamples(samples);
        samplesPlayed += (samples.length * fastForward);
        fastForward = 1;

        // check for silence
        if (samples[2000] == 0 && samples[4001] == 0)
            ++silence;
        else
            silence = 0;

        return samples;
    }

    public boolean supportTrackLength() {
        return true;
    }

    public boolean isTrackerFormat() {
        return false;
    }

    public String getTrackInfo() {
        //01-06 01:16:19.875: W/dalvikvm(496): JNI WARNING: illegal start byte 0x81
        //this happen when using newStringUTF in native code.
        //byte[] system = new byte[spcGetTrackInfoLength(currentTrack, 0)];
        byte[] game= new byte[aytGetTrackInfoLength(currentTrack * 2, 1)];
        byte[] song = new byte[aytGetTrackInfoLength(currentTrack * 2, 2)];
        byte[] author = new byte[aytGetTrackInfoLength(currentTrack * 2, 3)];
        byte[] copyright = new byte[aytGetTrackInfoLength(currentTrack * 2, 4)];
        byte[] comment = new byte[aytGetTrackInfoLength(currentTrack * 2, 5)];
        byte[] dumper = new byte[aytGetTrackInfoLength(currentTrack * 2, 6)];

        //spcGetTrackInfo(currentTrack, 0, system);
        aytGetTrackInfo(currentTrack * 2, 1, game);
        aytGetTrackInfo(currentTrack * 2, 2, song);
        aytGetTrackInfo(currentTrack * 2, 3, author);
        aytGetTrackInfo(currentTrack * 2, 4, copyright);
        aytGetTrackInfo(currentTrack * 2, 5, comment);
        aytGetTrackInfo(currentTrack * 2, 6, dumper);

        int playerversion = aytPlayerVersion();
        int sysPreset = aytSysPreset(currentTrack * 2);
        int lowerByte = 0x000000FF & sysPreset; // computer type
        int higherByte = (0x0000FF00 & sysPreset) >> 8; // soundchip type
        int firstsong = aytFirstsong();

        StringBuffer sb = new StringBuffer(256*9);

        // Debug output
        if (SpcDecoder.DEBUG_INFO && playerversion != -1) {

            final int[] PLAYER_BASE = {
                    0x0000,
                    0x1000 - 0x10,
                    0x2000 - 0x10,
                    0x3000 - 0x10,
                    0x4000 - 0x10,
                    0x5000 - 0x10,
                    0x6000 - 0x10,
                    0x7000 - 0x10,
                    0x8000 - 0x10,
                    0x9000 - 0x10,
                    0xA000 - 0x10,
                    0xB000 - 0x10,
                    0xC000 - 0x10,
                    0xD000 - 0x10,
                    0xE000 - 0x10,
                    0xF000 - 0x10 };

            sb.append("Playerversion: " + playerversion + "\n");
            sb.append("FirstSong: " + firstsong + " ");
            if ((firstsong & 0x80) == 0)
                sb.append(" [ABC]\n");
            if ((firstsong & 0x80) != 0)
                sb.append(" [ACB]\n");
            sb.append("=> main loop @ " + String.format("%04X", PLAYER_BASE[firstsong & 0x0000000F]) + "\n");
            sb.append("=> " + String.format("%8s", Integer.toBinaryString(firstsong)).replace(' ', '0') + "\n");
            sb.append("---<<<<>>>>\n");
            sb.append("SysPreset: " + sysPreset + "\n");
            sb.append("=> lo: " + String.format("%8s", Integer.toBinaryString(lowerByte)).replace(' ', '0') + "\n");
            sb.append("=> hi: " + String.format("%8s", Integer.toBinaryString(higherByte)).replace(' ', '0') + "\n");
            sb.append("-------<<<<>>>>\n\n");
        }
        //

        if (playerversion != -1) {
            switch (lowerByte) {
                case 130:
                case 131:
                    sb.append("ZX Spectrum (SAA-1099)");
                    break;
                case 128:
                case 129:
                    sb.append("Amstrad CPC");
                    break;
                case 0:
                default:
                    sb.append("ZX Spectrum");
                    break;
            }

            switch (higherByte) {
                case 1:
                    sb.append(" (Turbosound)");
                    break;
            }

            if ((firstsong & 0x80) == 0)
                sb.append(" [ABC]");
            if ((firstsong & 0x80) != 0)
                sb.append(" [ACB]");
            sb.append("\n");

        }

        sb.append("\n");
        sb.append(new String(game)).append("\n");
        sb.append(new String(song)).append("\n");
        sb.append(new String(author)).append("\n");
        sb.append(new String(copyright)).append("\n");
        sb.append(new String(comment)).append("\n");
        sb.append(new String(dumper));
        return sb.toString();
    }
}