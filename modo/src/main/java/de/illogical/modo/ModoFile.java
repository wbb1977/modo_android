package de.illogical.modo;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/* This is just for me, because I forget over time how it is supposed to work :)
 * 
 * A directory entry in a Zip:
 * dir1/
 * dir1/dir2/
 * 
 * A file entry in a Zip:
 * file1.xm
 * dir1/file1.xm
 * dir1/dir2/file1.xm
 * 
 * This class tries to emulate the java.io.File for a ZipEntry.
 * 
 * Another import thing to know, how to get path of ZipEntry.
 * FILE: "dir1/file1" -> substring(0, lastIndexOf('/') -> "dir1"
 * DIRECTORY: "dir1/dir2/" -> remove last / -> then see one line above
 * 
 * Always store directory entries with trailing '/', just to make clear
 * it is a directory! A plain string does not contain other information.
 * 
 */
final class ModoFile extends File {

	static final long serialVersionUID = 4342345252L;
	
	private String zipentry;
	private boolean isDirectory;
	private File src;
	private long uncompressedSize;

	public ModoFile(String zipentry, File src, boolean isDirectory) {
		this(zipentry, src, isDirectory, -1);
	}

	public ModoFile(String zipentry, File src, boolean isDirectory, long uncompressedSize) {
		super((src.getParent() == null ? "/" : src.getParent()), zipentry);
		this.zipentry = zipentry;
		this.src = src;
		this.isDirectory = isDirectory;
		this.uncompressedSize = uncompressedSize;
	}
	
	public void setLength(long size) {
		uncompressedSize = size;
	}
	
	public long length() {
		return uncompressedSize;
	}

	public boolean isFile() {
		return !isDirectory;
	}
	
	public boolean exists() {
		return true;
	}
	
	public boolean isDirectory() {
		return isDirectory;
	}
	
	File getSrc() {
		return src;
	}
	
	String getZipEntry() {
		return zipentry;
	}
	
	/**
	 * If ZipEntry is a file it is undefined.
	 * 
	 * If it is a subdirectory within the zip file it returns it parent:
	 * "subdir1/subdir2/" returns "subdir1/";
	 * 
	 * Otherwise the parent would be the the root directory of the zip file. In this
	 * case it returns the the path of the location of the zip file:
	 * "/sdcard/Music/samples.zip" returns a java.io.File directory "/sdcard/Music".
	 * 
	 * This logic is critical for the entire file browser and player routines. If the directory,
	 * from which the files are played, is an instance of ModoFile it is assumed that we browse
	 * within a zip file.
	 */
	public File getParentFile() {
		if (isDirectory) {
			String modpath = zipentry.substring(0, zipentry.length() - 1);
			if (modpath.lastIndexOf('/') > -1)
				return new ModoFile(modpath.substring(0, modpath.lastIndexOf('/')) + "/", src, true);
		}
		
		// ** MISSING
		// For files we don't need it, yet
		
		// otherwise return normal directory, for scanning 
		return src.getParentFile();
	}
	
	/**
	 * Parameter "dir" should have not the trailing slash.
	 * 
	 * Returns all zip entries which are in the directory given by "dir".
	 * 
	 */
	final static ArrayList<ModoFile> getEntriesForDirectory(ZipFile f, String dir, File src) {
		
		ArrayList<ModoFile> validChilds = new ArrayList<ModoFile>(50);
		
		for(Enumeration<? extends ZipEntry> allEntries = f.entries(); allEntries.hasMoreElements();) {
			ZipEntry entry = allEntries.nextElement();
			String path = "";
			String name = entry.getName();
			if (entry.isDirectory()) {
				// remove trailing slash for directory
				name = name.substring(0, name.length() - 1);
				// if we are not at the root, take the path
				if (name.lastIndexOf('/') > -1)
					path = name.substring(0, name.lastIndexOf('/'));
			} else {
				// if we are not at the root, take the path
				if (name.lastIndexOf('/') > -1)
					path = name.substring(0, name.lastIndexOf('/'));				
			}
			// Only if file is "dir" and is a supported file
			if (path.equals(dir) && FileBrowser.acceptZipEntry(entry))
				validChilds.add(new ModoFile(entry.getName(), src, entry.isDirectory(), entry.getSize()));
		}
		validChilds.trimToSize();
		return validChilds;
	}
	
	// Adds all entries from a zip, flat structure result
	final static ArrayList<ModoFile> getAllZipEntries(ZipFile f, File src) {
		ArrayList<ModoFile> validChilds = new ArrayList<ModoFile>(50);

		for(Enumeration<? extends ZipEntry> allEntries = f.entries(); allEntries.hasMoreElements() && validChilds.size() < Modo.MAX_PLAYLIST_FILES;) {
			ZipEntry entry = allEntries.nextElement();
			if (!entry.isDirectory() && FileBrowser.acceptZipEntry(entry))
				validChilds.add(new ModoFile(entry.getName(), src, false, entry.getSize()));
		}
		
		return validChilds;
	}
}
