package de.illogical.modo;

import java.util.Arrays;

final class XmpDecoder implements Decoder {

	static {
		System.loadLibrary("xmp");
	}
	
	private native static void xmpReset();
	private native static int xmpLoadFile(String path);
	private native static int xmpLoadFromZip(String zipFile, String zipEntry);
	private native static void xmpSetTrack(int track);
	private native static int xmpGetTrack();
	private native static int xmpTracks();
	private native static int xmpGetSamples(short[] samples);
	private native static void xmpSetStereoSeparation(int stereoSeparation);
	private native static int xmpLoopCount();
	
	private native static void xmpGetInfo(int what, byte[] info);
	private native static int xmpGetInfoLength(int what);
	private native static int xmpGetInstrumentsCount();
	private native static int xmpGetInstrumentLength(int nr);
	private native static void xmpGetInstrumentName(int nr, byte[] name);
	private native static int xmpGetSamplesCount();
	private native static int xmpGetSamplesLength(int nr);
	private native static void xmpGetSamplesName(int nr, byte[] name);
	
	private short[] samples = new short[17640];	
	private long samplesPlayed = 0;
	private int isLoadingOkay = 0;
	private int playerStatus = 0; // 0 = ok, anything else error during get samples
	
	void setStereoSeparation(int stereoSeparation) {
		// libkmikmod: 128 max stereo, 0 mono
		// libxmp: 100 max stereo, 0 mono
		xmpSetStereoSeparation(stereoSeparation * 100/128);		
	}
	
	public void reset() {
		xmpReset();
		samplesPlayed = 0;
		isLoadingOkay = 0;
		playerStatus = 0;
	}

	public int getStatus() {
		if (isLoadingOkay == 0)
			return Decoder.STATUS_FILE_ERROR;
		
		if (playerStatus != 0)
			return Decoder.STATUS_EOF;
		
		return Decoder.STATUS_OK;		
	}

	public int tracks() {
		return xmpTracks();
	}

	public int trackLength() {
		return -1;
	}

	public void setTrack(int track) {
		if (track >= 0 && track < xmpTracks())
			xmpSetTrack(track);
	}

	public void setPlaylistTrack(int track) {}

	public int getTrack() {
		return xmpGetTrack();
	}

	public void forward() {}

	public int playtime() {
		return  (int)(1000 * samplesPlayed / 88200);		
	}

	public int loadFile(String path) {
		reset();
		isLoadingOkay = xmpLoadFile(path);
		return isLoadingOkay;
	}

	public int loadFromZip(String zipFile, String zipEntry) {
		reset();
		isLoadingOkay = xmpLoadFromZip(zipFile, zipEntry);
		return isLoadingOkay;
	}

	public short[] getSamples() {
		int old_pos = xmpGetTrack();
		playerStatus = xmpGetSamples(samples);
		int new_pos = xmpGetTrack();
		if (new_pos == 0 && old_pos == (xmpTracks() - 1) && xmpLoopCount() > 0) {
				//android.util.Log.e("XMP", "End reached, not looping." );
				playerStatus = 1;
				Arrays.fill(samples, (short)0);
		}
		if (playerStatus == 0)
			samplesPlayed += samples.length;
		return samples;
	}

	public boolean supportTrackLength() {
		return false;
	}

	public boolean isTrackerFormat() {
		return true;
	}

	public String getTrackInfo() {
		byte[] title = new byte[xmpGetInfoLength(0)];
		byte[] tracker = new byte[xmpGetInfoLength(1)];
		byte[] comment = new byte[xmpGetInfoLength(2)];
		
		StringBuffer sb = new StringBuffer(5000);

		if (title.length > 0) {
			xmpGetInfo(0, title);
			sb.append(new String(title));
			sb.append("\n");			
		}
		if (comment.length > 0) {
			xmpGetInfo(2, comment);
			sb.append("\n");
			sb.append(new String(comment));
			sb.append("\n");
		}
		
		int samplesTotalSize = 0;
		for (int i = 0, smp = xmpGetSamplesCount(); i < smp; i++)			
			samplesTotalSize += xmpGetSamplesLength(i);
		
		int instrumentsTotalSize = 0;
		for (int i = 0, ins = xmpGetInstrumentsCount(); i < ins; i++)			
			instrumentsTotalSize += xmpGetInstrumentLength(i);
		
		if (samplesTotalSize > 0) {
			for (int i = 0, smp = xmpGetSamplesCount(); i < smp; i++) {
				byte[] chars = new byte[xmpGetSamplesLength(i)];
				xmpGetSamplesName(i, chars);
				sb.append(String.format("\nS%03d: %s",i , new String(chars)));				
			}
			sb.append("\n");
		}

		if (instrumentsTotalSize > 0) {
			for (int i = 0, ins = xmpGetInstrumentsCount(); i < ins; i++) {
				byte[] chars = new byte[xmpGetInstrumentLength(i)];
				xmpGetInstrumentName(i, chars);
				sb.append(String.format("\nI%03d: %s",i , new String(chars)));				
			}
			sb.append("\n");
		}
	
		if (tracker.length > 0) {
			xmpGetInfo(1, tracker);
			sb.append("\n");
			sb.append(new String(tracker));
			sb.append("\n");
		}
		
		sb.trimToSize();
		return sb.toString();
	}
}
