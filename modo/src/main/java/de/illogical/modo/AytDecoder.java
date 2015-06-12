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
		// libkmikmod: 128 max stereo, 0 mono
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
		return 0;
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
		return isLoadingOkay;		
	}

	public int loadFromZip(String zipFile, String zipEntry) {
		reset();
		if (zipFile == null)
			return 0;
		if (zipEntry == null)
			return 0;
		isLoadingOkay = aytLoadFromZip(zipFile, zipEntry);
		if (isLoadingOkay > 0)
			isLoadingOkay = (aytTracks() % 2)== 0 ? 1 : 0; // make sure there is an even number of tracks for turbosound
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
		byte[] system = new byte[aytGetTrackInfoLength(currentTrack, 0)];
		byte[] game= new byte[aytGetTrackInfoLength(currentTrack, 1)];
		byte[] song = new byte[aytGetTrackInfoLength(currentTrack, 2)];
		byte[] author = new byte[aytGetTrackInfoLength(currentTrack, 3)];
		byte[] copyright = new byte[aytGetTrackInfoLength(currentTrack, 4)];
		byte[] comment = new byte[aytGetTrackInfoLength(currentTrack, 5)];
		byte[] dumper = new byte[aytGetTrackInfoLength(currentTrack, 6)];
		
		aytGetTrackInfo(currentTrack, 0, system);
		aytGetTrackInfo(currentTrack, 1, game);
		aytGetTrackInfo(currentTrack, 2, song);
		aytGetTrackInfo(currentTrack, 3, author);
		aytGetTrackInfo(currentTrack, 4, copyright);
		aytGetTrackInfo(currentTrack, 5, comment);
		aytGetTrackInfo(currentTrack, 6, dumper);
		
		StringBuffer sb = new StringBuffer(256*8);
		sb.append(new String(system)).append("\n");
		sb.append(new String(game)).append("\n");
		sb.append(new String(song)).append("\n");
		sb.append(new String(author)).append("\n");
		sb.append(new String(copyright)).append("\n");
		sb.append(new String(comment)).append("\n");
		sb.append(new String(dumper));
		return sb.toString();		
	}
}