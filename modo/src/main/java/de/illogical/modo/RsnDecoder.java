// Using SpcDecoder for fast SPC playback.

package de.illogical.modo;

final class RsnDecoder implements Decoder {

    private short[] samples = new short[17640];
    private int currentTrack = 0;
    private long samplesPlayed = 0;
    private int fastForward = 1;
    private int silence = 0;
    private int isLoadingOkay = 0;
    private int silencePeriod = 20;
    private int playlistTrack = -1;
    private int rsnCount = 0;
    private String rsnInfoText = "";
    private boolean isConvertStereoToMono = false;

    void setMonoOutput(boolean isConvertStereoToMono) {
        this.isConvertStereoToMono = isConvertStereoToMono;
    }

    void setSilenceDetection(int seconds) {
        silencePeriod = seconds * 5;
    }

    public void reset() {
        SpcDecoder.spcReset();
        SpcDecoder.spcResetRsnEntries();
        samplesPlayed = 0;
        currentTrack = 0;
        playlistTrack = -1;
        fastForward = 1;
        silence = 0;
        isLoadingOkay = 0;
        rsnCount = 0;
        rsnInfoText = "";
    }

    public int tracks() {
        return playlistTrack >= 0 ? 1 : rsnCount;
    }

    public int trackLength() {
        return SpcDecoder.spcTrackLength(0);
    }

    public void setPlaylistTrack(int track) {
        playlistTrack = track;
    }

    public void setTrack(int track) {
        if (track < 0)
            track = 0;
        if (track >= rsnCount)
            track = rsnCount - 1;
        currentTrack = playlistTrack >= 0 ? playlistTrack : track;
        samplesPlayed = 0;
        silence = 0;
        fastForward = 1;

        SpcDecoder.spcReset();
        isLoadingOkay = SpcDecoder.spcPlayRSN(currentTrack);
    }

    public void forward() {
        SpcDecoder.spcSeek(+200);
        fastForward = 2;
    }

    public int playtime() {
        return (int)(1000 * samplesPlayed / 88200);
    }

    public int loadFile(String path) {
        reset();

        if (path == null)
            return 0;

        rsnCount = SpcDecoder.spcLoadRSN(path);
        isLoadingOkay = rsnCount > 0 ? 1 : 0;

        byte[] rsnInfoBytes = new byte[SpcDecoder.spcRSNInfoLength()];
        SpcDecoder.spcGetRSNInfo(rsnInfoBytes);
        rsnInfoText = new String(rsnInfoBytes);

        return isLoadingOkay;
    }

    public int loadFromZip(String zipFile, String zipEntry) {
        // TODO Dont know how atm.
        return 0;
    }

    public short[] getSamples() {
        SpcDecoder.spcGetSamples(samples);
        samplesPlayed += (samples.length * fastForward);
        fastForward = 1;

        // check for silence
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
        byte[] system = new byte[SpcDecoder.spcGetTrackInfoLength(0, 0)];
        byte[] game= new byte[SpcDecoder.spcGetTrackInfoLength(0, 1)];
        byte[] song = new byte[SpcDecoder.spcGetTrackInfoLength(0, 2)];
        byte[] author = new byte[SpcDecoder.spcGetTrackInfoLength(0, 3)];
        byte[] copyright = new byte[SpcDecoder.spcGetTrackInfoLength(0, 4)];
        byte[] comment = new byte[SpcDecoder.spcGetTrackInfoLength(0, 5)];
        byte[] dumper = new byte[SpcDecoder.spcGetTrackInfoLength(0, 6)];

        SpcDecoder.spcGetTrackInfo(0, 0, system);
        SpcDecoder.spcGetTrackInfo(0, 1, game);
        SpcDecoder.spcGetTrackInfo(0, 2, song);
        SpcDecoder.spcGetTrackInfo(0, 3, author);
        SpcDecoder.spcGetTrackInfo(0, 4, copyright);
        SpcDecoder.spcGetTrackInfo(0, 5, comment);
        SpcDecoder.spcGetTrackInfo(0, 6, dumper);

        StringBuffer sb = new StringBuffer(256*8);
        sb.append(new String(system)).append("\n");
        sb.append(new String(game)).append("\n");
        sb.append(new String(song)).append("\n");
        sb.append(new String(author)).append("\n");
        sb.append(new String(copyright)).append("\n");
        sb.append(new String(comment)).append("\n");
        sb.append(new String(dumper)).append("\n\n");
        sb.append(rsnInfoText);
        return sb.toString();
    }
}