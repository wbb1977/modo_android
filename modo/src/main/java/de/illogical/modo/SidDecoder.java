package de.illogical.modo;

final class SidDecoder implements Decoder {

    static
    {
        System.loadLibrary("sidplay2");
    }

    private static final int SIDTUNE_MAX_CREDIT_STRLEN = 80+1;
    private static final int SIDTUNE_CLOCK_UNKNOWN = 0x00;
    private static final int SIDTUNE_CLOCK_PAL     = 0x01; // These are also used in the
    private static final int SIDTUNE_CLOCK_NTSC    = 0x02; // emulator engine!
    private static final int SIDTUNE_CLOCK_ANY     = (SIDTUNE_CLOCK_PAL | SIDTUNE_CLOCK_NTSC);
    private static final int SIDTUNE_SPEED_VBI     = 0; // Vertical-Blanking-Interrupt
    private static final int SIDTUNE_SPEED_CIA_1A  = 60; // CIA 1 Timer A
    private static final int SIDTUNE_SIDMODEL_UNKNOWN = 0x00;
    private static final int SIDTUNE_SIDMODEL_6581    = 0x01; // These are also used in the
    private static final int SIDTUNE_SIDMODEL_8580    = 0x02; // emulator engine!
    private static final int SIDTUNE_SIDMODEL_ANY     = (SIDTUNE_SIDMODEL_6581 | SIDTUNE_SIDMODEL_8580);

    // control
    native private static void sidReset();
    native private static int sidGetSamples(short[] samples);
    native private static int sidLoadFile(String path);
    native private static int sidLoadFromZip(String zipFile, String zipEntry);
    native private static int sidTracks();
    native private static int sidSetTrack(int track);
    native private static int sidGetTrackLengths(int md5, int[] trackLengths);
    native private static String sidGetMD5();
    native private static void sidSetTempo(int tempo);

    // track info
    native private static int sidCreditsCount();
    native private static int sidGetCreditLength(int position);
    native private static void sidGetCreditBytes(int position, byte[] credit, int length);
    native private static int sidGetClockspeed();
    native private static int sidGetSongspeed();
    native private static int sidGetModel();

    private short[] samples = new short[17640];
    private int currentTrack = 0;
    private long samplesPlayed = 0;
    private int[] trackLengths = new int[0];
    private String md5 = null;
    private int isMD5inDatabase = 0;
    private int fastForward = 1;
    private int silence = 0;
    private int firstSample = 0;
    private int secondSample = 0;
    private int isLoadingOkay = 0;
    private int silencePeriod = 20;
    private int playlistTrack = -1;

    void setSilenceDetection(int seconds) {
        silencePeriod = seconds * 5;
    }

    public void reset() {
        sidReset();
        samplesPlayed = 0;
        md5 = "0";
        isMD5inDatabase = 0;
        trackLengths = new int[0];
        currentTrack = 0;
        playlistTrack = -1;
        fastForward = 1;
        silence = 0;
        isLoadingOkay = 0;
    }

    public int tracks() {
        return playlistTrack >= 0 ? 1 : sidTracks();
    }

    public int trackLength() {
        return isMD5inDatabase == 1 ? trackLengths[currentTrack] * 1000 : 0;
    }

    public void setPlaylistTrack(int track) {
        playlistTrack = track;
    }

    public void setTrack(int track) {
        if (track < 0)
            track = 0;
        if (track >= sidTracks())
            track = sidTracks() - 1;
        currentTrack = playlistTrack >= 0 ? playlistTrack : track;
        samplesPlayed = 0;
        silence = 0;
        sidSetTrack(currentTrack);
    }

    public void forward() {
        sidSetTempo(200);
        fastForward = 2;
    }

    public int playtime() {
        return (int)(1000 * samplesPlayed / 88200);
    }

    private int convertMd5ToInt(String md5_str)
    {
        int md5_int = 0;
        for (int i = 0, length = md5_str.length(); i < length; ++i) {
            md5_int = md5_int << 1;
            if (md5_str.charAt(i) > '6')
                md5_int = md5_int | 1;
        }
        return md5_int;
    }

    public int loadFile(String path) {
        reset();
        isLoadingOkay = sidLoadFile(path);
        if (isLoadingOkay > 0) {
            md5 = sidGetMD5();
            trackLengths = new int[sidTracks()];
            isMD5inDatabase = sidGetTrackLengths(convertMd5ToInt(md5), trackLengths);
            return isLoadingOkay;
        }
        return 0;
    }

    public int loadFromZip(String zipFile, String zipEntry) {
        reset();
        isLoadingOkay = sidLoadFromZip(zipFile, zipEntry);
        if (isLoadingOkay > 0) {
            md5 = sidGetMD5();
            trackLengths = new int[sidTracks()];
            isMD5inDatabase = sidGetTrackLengths(convertMd5ToInt(md5), trackLengths);
            return isLoadingOkay;
        }
        return 0;
    }

    public short[] getSamples() {
        sidGetSamples(samples);
        samplesPlayed += (samples.length * fastForward);
        fastForward = 1;

        // libsidplay2 keeps the last sample played
        if (samples[0] == firstSample && samples[1] == secondSample)
            ++silence;
        else
            silence = 0;

        firstSample = samples[0];
        secondSample = samples[1];

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
        StringBuffer sb = new StringBuffer(1000);

        // Credit strings
        for (int i = 0, creditsLength = sidCreditsCount(); i < creditsLength; ++i)
        {
            byte[] credit = new byte[sidGetCreditLength(i)];
            sidGetCreditBytes(i, credit, credit.length);
            sb.append(new String(credit));
            sb.append("\n");
        }
        sb.append("\n");

        // Sid Models
        sb.append(" Sid-Model: ");
        switch (sidGetModel())
        {
            case SIDTUNE_SIDMODEL_UNKNOWN:
                sb.append("Unknown");
                break;
            case SIDTUNE_SIDMODEL_6581:
                sb.append("6581");
                break;
            case SIDTUNE_SIDMODEL_8580:
                sb.append("8580");
                break;
            case SIDTUNE_SIDMODEL_ANY:
                sb.append("Any");
                break;
            default:
                sb.append("??");
                break;
        }
        sb.append("\n");

        // Clockspeed
        sb.append("Clockspeed: ");
        switch (sidGetClockspeed())
        {
            case SIDTUNE_CLOCK_UNKNOWN:
                sb.append("Unknown");
                break;
            case SIDTUNE_CLOCK_PAL:
                sb.append("PAL");
                break;
            case SIDTUNE_CLOCK_NTSC:
                sb.append("NTSC");
                break;
            case SIDTUNE_CLOCK_ANY:
                sb.append("Any");
                break;
            default:
                sb.append("??");
                break;
        }
        sb.append("\n");

        // Songspeed
        sb.append(" Songspeed: ");
        switch (sidGetSongspeed())
        {
            case SIDTUNE_SPEED_CIA_1A:
                sb.append("CIA 1 Timer A");
                break;
            case SIDTUNE_SPEED_VBI:
                sb.append("VBI");
                break;
            default:
                sb.append("??");
                break;
        }
        sb.append("\n\n");

        // MD5 and songlength db
        sb.append("MD5: " + md5);
        sb.append("\n");
        sb.append(isMD5inDatabase != 0 ? "(in songlength database)" : "(not found in songlength database)");

        sb.trimToSize();

        return sb.toString();
    }
}