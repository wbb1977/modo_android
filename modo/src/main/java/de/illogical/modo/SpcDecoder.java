//GME 0.6.0 for fast SPC & AY playback
 
package de.illogical.modo;

final class SpcDecoder implements Decoder {

    static {
        System.loadLibrary("unzip");
        System.loadLibrary("unrar");
        System.loadLibrary("gme_spc");
        spcInit();
    }

    static final boolean DEBUG_INFO = true;

    // RSN related
    static native void spcInit(); // Init RSN structure
    static native void spcResetRsnEntries();
    static native int spcLoadRSN(String path);
    static native int spcPlayRSN(int track);
    static native int spcRSNInfoLength();
    static native void spcGetRSNInfo(byte[] info);

    // Standard spc
    static native void spcReset();
    static native int spcTracks();
    static native int spcLoadFile(String path);
    static native int spcLoadFromZip(String zipfile, String entry);
    static native int spcGetSamples(short[] samples);
    static native int spcSetTrack(int track);
    static native int spcTrackLength(int track);
    static native int spcPlaytime();
    static native int spcSeek(long milli);
    static native void spcGetTrackInfo(int track, int what, byte[] s);
    static native int spcGetTrackInfoLength(int track, int what);
    static native int spcAYplayerVersion();
    static native int spcAYsysPreset(int track);
    static native int spcAYfirstsong();

    private short[] samples = new short[17640];
    private int currentTrack = 0;
    private long samplesPlayed = 0;
    private int fastForward = 1;
    private int silence = 0;
    private int isLoadingOkay = 0;
    private int silencePeriod = 20;
    private int playlistTrack = -1;
    private boolean isAyStereo = false;
    private boolean isConvertStereoToMono = false;

    void setMonoOutput(boolean isConvertStereoToMono) {
        this.isConvertStereoToMono = isConvertStereoToMono;
    }

    void setSilenceDetection(int seconds) {
        silencePeriod = seconds * 5;
    }

    public void reset() {
        spcReset();
        samplesPlayed = 0;
        currentTrack = 0;
        fastForward = 1;
        silence = 0;
        isLoadingOkay = 0;
        playlistTrack = -1;
    }

    public int tracks() {
        return playlistTrack >= 0 ? 1 : spcTracks();
    }

    public int trackLength() {
        return spcTrackLength(currentTrack);
    }

    public void setPlaylistTrack(int track) {
        playlistTrack = track;
    }

    public void setTrack(int track) {
        if (track < 0)
            track = 0;
        if (track >= spcTracks())
            track = spcTracks() - 1;
        currentTrack = playlistTrack >= 0 ? playlistTrack : track;
        samplesPlayed = 0;
        silence = 0;
        fastForward = 1;
        spcSetTrack(currentTrack);
    }

    public void forward() {
        spcSeek(+200);
        fastForward = 2;
    }

    public int playtime() {
        return (int)(1000 * samplesPlayed / 88200);
    }

    public int loadFile(String path) {
        reset();
        if (path == null)
            return 0;
        isLoadingOkay = spcLoadFile(path);
        if (isLoadingOkay > 0 && spcAYplayerVersion() == 16)  // Player version 16 used for emulation of two ZX cores
            isLoadingOkay = 0;
        return isLoadingOkay;
    }

    public int loadFromZip(String zipFile, String zipEntry) {
        reset();
        if (zipFile == null)
            return 0;
        if (zipEntry == null)
            return 0;
        isLoadingOkay = spcLoadFromZip(zipFile, zipEntry);
        if (isLoadingOkay > 0 && spcAYplayerVersion() == 16) // Player version 16 used for emulation of two ZX cores
            isLoadingOkay = 0;
        return isLoadingOkay;
    }

    public short[] getSamples() {
        spcGetSamples(samples);
        samplesPlayed += (samples.length * fastForward);
        fastForward = 1;
        /// check for silence
        if (samples[2000] == 0 && samples[4001] == 0)
            ++silence;
        else
            silence = 0;

        if (isConvertStereoToMono)
            Mixer.convertToMono(samples);

        return samples;
    }

    public int getTrack() {
        return currentTrack;
    }

    public boolean supportTrackLength() {
        return true;
    }

    public int getStatus() {
        if (isLoadingOkay == 0)
            return Decoder.STATUS_FILE_ERROR;

        if (silence >= silencePeriod)
            return Decoder.STATUS_SILENCE;

        return Decoder.STATUS_OK;
    }

    public boolean isTrackerFormat() {
        return false;
    }

    public String getTrackInfo() {
        //01-06 01:16:19.875: W/dalvikvm(496): JNI WARNING: illegal start byte 0x81
        //this happen when using newStringUTF in native code.
        byte[] system = new byte[spcGetTrackInfoLength(currentTrack, 0)];
        byte[] game= new byte[spcGetTrackInfoLength(currentTrack, 1)];
        byte[] song = new byte[spcGetTrackInfoLength(currentTrack, 2)];
        byte[] author = new byte[spcGetTrackInfoLength(currentTrack, 3)];
        byte[] copyright = new byte[spcGetTrackInfoLength(currentTrack, 4)];
        byte[] comment = new byte[spcGetTrackInfoLength(currentTrack, 5)];
        byte[] dumper = new byte[spcGetTrackInfoLength(currentTrack, 6)];

        spcGetTrackInfo(currentTrack, 0, system);
        spcGetTrackInfo(currentTrack, 1, game);
        spcGetTrackInfo(currentTrack, 2, song);
        spcGetTrackInfo(currentTrack, 3, author);
        spcGetTrackInfo(currentTrack, 4, copyright);
        spcGetTrackInfo(currentTrack, 5, comment);
        spcGetTrackInfo(currentTrack, 6, dumper);

        int playerversion = spcAYplayerVersion();
        int sysPreset = spcAYsysPreset(currentTrack);
        int lowerByte = 0x000000FF & sysPreset; // computer type
        int higherByte = (0x0000FF00 & sysPreset) >> 8; // soundchip type
        int firstsong = spcAYfirstsong();

        StringBuffer sb = new StringBuffer(256*9);

        // Debug output
        if (DEBUG_INFO && playerversion != -1) {

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
            sb.append("FirstSong: " + firstsong + "\n");
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
                    sb.append("SAM Coupé");//sb.append("ZX Spectrum (SAA-1099)");
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

        sb.append(new String(game)).append("\n");
        sb.append(new String(song)).append("\n");
        sb.append(new String(author)).append("\n");
        sb.append(new String(copyright)).append("\n");
        sb.append(new String(comment)).append("\n");
        sb.append(new String(dumper));
        return sb.toString();
    }
}