package de.illogical.modo;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

final class Playlist {

    static final class Entry {
        String path;
        String zipEntry;
        String displayname;
        int start;

        public boolean equals(Object o) {
            Entry e = (Entry)o;
            if (zipEntry == null)
                return e.zipEntry == null && e.path.equals(path) && e.start == start;
            return e.zipEntry != null && e.zipEntry.equals(zipEntry) && e.path.equals(path) && e.start == start;
        }
    }

    ArrayList<Entry> entries = new ArrayList<Playlist.Entry>(100);
    ArrayList<Entry> shadowEntries = new ArrayList<Playlist.Entry>(100);
    ArrayList<Integer> played = new ArrayList<Integer>(100);

    private boolean isShuffle = false;
    private int playPosition = 0;

    static EntrySort ENTRIES_SORTER = new EntrySort();
    static private class EntrySort implements Comparator<Entry> {
        public int compare(Entry lhs, Entry rhs) {
            int res = lhs.displayname.compareToIgnoreCase(rhs.displayname);
            if (res == 0) {
                if (lhs.start == rhs.start)
                    return 0;
                if (lhs.start < rhs.start)
                    return -1;
                return 1;
            }
            return res;
        }
    }

    void remove(Playlist.Entry pe) {
        entries.remove(pe);
        shadowEntries.remove(pe);
        resetPlayedAll();
    }

    void clear() {
        entries.clear();
        shadowEntries.clear();
        played.clear();
    }

    int add(String path, String zipEntry, int start) {
        return add(path, zipEntry, start, true);
    }

    int add(String path, String zipEntry, int start, boolean isAllowDups) {
        if (path == null)
            return 0;
        if (path.length() <= 0)
            return 0;
        if (path.endsWith(File.pathSeparator))
            return 0;
        if (zipEntry != null && zipEntry.length() <= 0)
            return 0;
        if (zipEntry != null && zipEntry.endsWith(File.pathSeparator))
            return 0;

        Entry e = new Entry();

        // remove directory
        if (zipEntry != null)
            e.displayname = zipEntry.lastIndexOf(File.separatorChar) >= 0 ? zipEntry.substring(zipEntry.lastIndexOf(File.separatorChar) + 1) : zipEntry;
        else
            e.displayname = path.lastIndexOf(File.separator) >= 0 ? path.substring(path.lastIndexOf(File.separatorChar) + 1) : path;

        // remove extension
        int posLastDot = e.displayname.lastIndexOf('.');
        if (posLastDot > 0 && (e.displayname.length() - posLastDot) < 6)
            e.displayname = e.displayname.substring(0, e.displayname.lastIndexOf('.'));

        e.zipEntry = zipEntry;
        e.path = path;
        e.start = start;

        if (isAllowDups == false && entries.contains(e))
            return 0;

        entries.add(e);
        shadowEntries.add(e);
        sort();
        return 1;
    }

    void sort() {
        Collections.sort(entries, ENTRIES_SORTER);
        Collections.shuffle(shadowEntries);
        resetPlayedAll();
    }

    int size() {
        return entries.size();
    }

    void resetPlayedAll() {
        played.clear();
        played.ensureCapacity(entries.size());
        played.addAll(Collections.nCopies(entries.size(), 0));
    }

    void setShuffleMode(boolean isShuffleMode) {
        isShuffle = isShuffleMode;

        if (isShuffleMode == false)
            playPosition = entries.indexOf(shadowEntries.get(playPosition));

        resetPlayedAll();
    }

    void advancePlayPosition() {
        setPlayPosition(playPosition + 1);
    }

    void reducePlayPosition() {
        setPlayPosition(playPosition - 1);
    }

    void syncPlayPositionForShuffle() {
        playPosition = shadowEntries.indexOf(entries.get(playPosition));
    }

    void setPlayPosition(int index) {
        playPosition = index;
        if (index >= entries.size())
            playPosition = 0;
        if (index < 0)
            playPosition = entries.size() - 1;
    }

    Entry get() {
        if (entries.isEmpty())
            return null;
        if (playPosition >= entries.size()) // Just for safety, not needed, as setPlayPosition take care. Just to ease my brain.
            playPosition = 0;
        ArrayList<Entry> l = isShuffle ? shadowEntries : entries;
        played.set(playPosition, 1);
        return l.get(playPosition);
    }

    boolean allPlayed() {
        int is = 0;
        int goal = entries.size();

        for (Integer i: played)
            is += i;

        return is == goal;
    }

    boolean hasEntries() {
        return !entries.isEmpty();
    }
}
