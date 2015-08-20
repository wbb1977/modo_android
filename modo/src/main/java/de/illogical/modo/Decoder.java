package de.illogical.modo;

interface Decoder {
    // Is set if loadFile() failed
    public static int STATUS_FILE_ERROR = 8;
    // Is set when two seconds of silence are detected
    public static int STATUS_SILENCE = 4;
    // Is set when end of file is reached, currently only used by MikModDecode
    public static int STATUS_EOF = 2;
    // Is set when normal operation is possible
    public static int STATUS_OK = 1;

    // Release any native resources and reset control variables
    public void reset();
    // Return current status of operation
    public int getStatus();
    // Total number of tracks
    public int tracks();
    // Total time of current track in milliseconds
    public int trackLength();
    // Select new track
    public void setTrack(int track);
    // Play only this track from the file
    public void setPlaylistTrack(int track);
    // Return number of selected track (playback)
    public int getTrack();
    // Called to advance 200ms
    public void forward();
    // Playtime of current track in milliseconds
    public int playtime();
    // Load given file and set status (OK / FILE_ERROR)
    public int loadFile(String path);
    // Load file from Zipfile, uncompress into memory and read from memory. set status (OK / FILE_ERROR)
    public int loadFromZip(String zipFile, String zipEntry);
    // Returns 200ms of samples. Can set SILENCE / EOF flag.
    public short[] getSamples();
    // Songlength can be read from the file, not true for trackers
    public boolean supportTrackLength();
    // True if track/tracks are indicate positions instead of tracks (subsongs)
    public boolean isTrackerFormat();
    // Returns a description of the current track
    public String getTrackInfo();
}
