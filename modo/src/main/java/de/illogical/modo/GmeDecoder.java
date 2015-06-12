// Kode54's GME version. VGM  & NES (all expansion chips) playback.
package de.illogical.modo;

import java.util.Locale;

final class GmeDecoder implements Decoder {

	static {
		System.loadLibrary("unzip");
		System.loadLibrary("gme_kode54");
	}
	
	// Standard GME
	private static native void gmeReset();
	private static native int gmeTracks();
	private static native int gmeLoadFile(String path);
	private static native int gmeLoadM3u(String path);
	private static native int gmeLoadFromZip(String zipfile, String entry, int vgzfile);
	private static native int gmeLoadM3uFromZip(String zipfile, String entry);
	private static native int gmeGetSamples(short[] samples);
	private static native int gmeSetTrack(int track);
	private static native int gmeTrackLength(int track);
	private static native int gmePlaytime();
	private static native int gmeSeek(long milli);
	private static native void gmeGetTrackInfo(int track, int what, byte[] s);
	private static native int gmeGetTrackInfoLength(int track, int what);
	private static native void gmeSetStereoSeparation(int depth);

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
		gmeSetStereoSeparation(sep * 100/128);
	}
	
	public void reset() {
		gmeReset();
		samplesPlayed = 0;
		currentTrack = 0;
		playlistTrack = -1;
		fastForward = 1;
		silence = 0;
		isLoadingOkay = 0;
	}
	
	public int tracks() {
		return playlistTrack >= 0 ? 1 : gmeTracks();
	}

	public int trackLength() {
		return gmeTrackLength(currentTrack);
	}

	public void setPlaylistTrack(int track) {
		playlistTrack = track;
	}
	
	public void setTrack(int track) {
		if (track < 0)
			track = 0;
		if (track >= gmeTracks())
			track = gmeTracks() - 1;
		currentTrack = playlistTrack >= 0 ? playlistTrack : track;
		samplesPlayed = 0;
		silence = 0;
		fastForward = 1;
		gmeSetTrack(currentTrack);
	}

	public void forward() {
		gmeSeek(+200);
		fastForward = 2;
	}

	public int playtime() {
		return (int)(1000 * samplesPlayed / 88200);
	}

	public int loadFile(String path) {
		reset();		
		if (path == null)
			return 0;		
		isLoadingOkay = gmeLoadFile(path);
		// Load M3u for Kss / Hes
		if (isLoadingOkay > 0 && (path.toLowerCase(Locale.getDefault()).endsWith(".hes") || path.toLowerCase(Locale.getDefault()).endsWith(".kss"))) {
			String m3u = path.substring(0, path.lastIndexOf('.')) + ".m3u";
			gmeLoadM3u(m3u);
		}
		return isLoadingOkay;
	}
	
	public int loadFromZip(String zipFile, String zipEntry) {
		reset();
		if (zipFile == null)
			return 0;
		if (zipEntry == null)
			return 0;	
		isLoadingOkay = gmeLoadFromZip(zipFile, zipEntry, zipEntry.toLowerCase(Locale.getDefault()).endsWith(".vgz") ? 1 : 0);
		// Load M3u for Kss / Hes
		if (isLoadingOkay > 0 && (zipEntry.toLowerCase(Locale.getDefault()).endsWith(".hes") || zipEntry.toLowerCase(Locale.getDefault()).endsWith(".kss"))) {
			String m3u = zipEntry.substring(0, zipEntry.lastIndexOf('.')) + ".m3u";
			gmeLoadM3uFromZip(zipFile, m3u);
		}
		return isLoadingOkay;
	}	

	public short[] getSamples() {
		gmeGetSamples(samples);
		samplesPlayed += (samples.length * fastForward);
		fastForward = 1;
		
		// check for silence
		if (samples[2000] == 0 && samples[4001] == 0)
			++silence;
		else
			silence = 0;
		
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
		byte[] system = new byte[gmeGetTrackInfoLength(currentTrack, 0)];
		byte[] game= new byte[gmeGetTrackInfoLength(currentTrack, 1)];
		byte[] song = new byte[gmeGetTrackInfoLength(currentTrack, 2)];
		byte[] author = new byte[gmeGetTrackInfoLength(currentTrack, 3)];
		byte[] copyright = new byte[gmeGetTrackInfoLength(currentTrack, 4)];
		byte[] comment = new byte[gmeGetTrackInfoLength(currentTrack, 5)];
		byte[] dumper = new byte[gmeGetTrackInfoLength(currentTrack, 6)];
		
		gmeGetTrackInfo(currentTrack, 0, system);
		gmeGetTrackInfo(currentTrack, 1, game);
		gmeGetTrackInfo(currentTrack, 2, song);
		gmeGetTrackInfo(currentTrack, 3, author);
		gmeGetTrackInfo(currentTrack, 4, copyright);
		gmeGetTrackInfo(currentTrack, 5, comment);
		gmeGetTrackInfo(currentTrack, 6, dumper);
		
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