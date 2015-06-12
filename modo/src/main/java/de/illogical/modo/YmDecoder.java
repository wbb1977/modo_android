package de.illogical.modo;

final class YmDecoder implements Decoder {

	static {
		System.loadLibrary("ym");
	}
	
	// YM Format does not support sub songs
	private static final int INFO_SONGNAME = 1;
	private static final int INFO_AUTHOR = 2;
	private static final int INFO_COMMENT = 3;
	private static final int INFO_TYPE = 4;
	private static final int INFO_PLAYER = 5;
	
	private static native void ymReset();
	private static native int ymSeek(long milli);
	private static native int ymTrackLength();
	private static native int ymRestart();
	private static native int ymLoadFile(String path);
	private static native int ymLoadFromZip(String zipfile, String entry);	
	private static native int ymGetSamples(short[] samples);
	private static native int ymGetInfoLength(int what);
	private static native int ymGetInfoBytes(byte[] chars, int len, int what);
	private static native int ymSeek(int time);
	
	private short[] samplesMono = new short[17640 / 2];
	private short[] samplesStereo = new short[17640];
	private long samplesPlayed = 0;
	private int isLoadingOkay = 0;
	private int silence = 0;
	private int fastForward = 1;
	private int silencePeriod = 20;
	
	void setSilenceDetection(int seconds) {
		silencePeriod = seconds * 5;
	}
	
	public void reset() {
		ymReset();
		samplesPlayed = 0;
		isLoadingOkay = 0;
		silence = 0;
		fastForward = 1;
	}

	public void setPlaylistTrack(int track) {} // no effect, YM always have only one track
	
	public int getStatus() {
		if (isLoadingOkay == 0)
			return Decoder.STATUS_FILE_ERROR;
		
		if (silence >= silencePeriod)
			return Decoder.STATUS_SILENCE;
		
		return Decoder.STATUS_OK;
	}

	public int tracks() {
		return 1;
	}

	public int trackLength() {
		return ymTrackLength();
	}

	public void setTrack(int track) {
		samplesPlayed = 0;
		silence = 0;
		ymRestart();
	}
	
	public int getTrack() {
		return 1;
	}

	public void forward() {
		ymSeek(+200);
		fastForward = 2;
	}

	public int playtime() {
		return (int)(1000 * samplesPlayed / 88200);
	}

	public int loadFile(String path) {
		reset();		
		isLoadingOkay = ymLoadFile(path);
		return isLoadingOkay;
	}
	
	public int loadFromZip(String zipFile, String zipEntry) {
		reset();
		isLoadingOkay = ymLoadFromZip(zipFile, zipEntry);
		return isLoadingOkay;
	}	

	public short[] getSamples() {
		// YM Library uses only mono.
		ymGetSamples(samplesMono);

		// Mix the mono data into stereo data
		for (int i = 0, st = 0, len = samplesMono.length; i < len; ++i) {
			samplesStereo[st++] = samplesMono[i];
			samplesStereo[st++] = samplesMono[i];
		}
		
		samplesPlayed += (samplesStereo.length * fastForward);
		fastForward = 1;
		
		// check for silence
		if (samplesStereo[0] == 0)
			++silence;
		else
			silence = 0;
		
		return samplesStereo;
	}

	public boolean supportTrackLength() {
		return true;
	}

	public boolean isTrackerFormat() {
		return false;
	}

	public String getTrackInfo() {
		StringBuffer b = new StringBuffer(2000);

		byte[] name = new byte[ymGetInfoLength(INFO_SONGNAME)];
		ymGetInfoBytes(name, name.length, INFO_SONGNAME);
		if (name.length > 0) {
			b.append(new String(name));
			b.append("\n");
		}
		
		byte[] author = new byte[ymGetInfoLength(INFO_AUTHOR)];
		ymGetInfoBytes(author, author.length, INFO_AUTHOR);
		if (author.length > 0) {
			b.append(new String(author));
			b.append("\n");
		}

		byte[] comment = new byte[ymGetInfoLength(INFO_COMMENT)];
		ymGetInfoBytes(comment, comment.length, INFO_COMMENT);
		if (comment.length > 0) {
			b.append(new String(comment));
			b.append("\n");
		}
		
		return b.toString();
	}

	boolean seek(int time) {
		if (time < 0)
			time = 0;
		if (time > trackLength())
			time = trackLength();
		//time = time / 1000;
		//time = time * 1000;
		samplesPlayed = time / 1000 * 88200;
		// ymSeek returns 0 if unpossible to seek
		return ymSeek(time) == 1;
	}
}