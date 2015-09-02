package de.illogical.modo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import android.app.backup.BackupDataInputStream;
import android.app.backup.BackupDataOutput;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.DatabaseUtils.InsertHelper;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

@SuppressWarnings("deprecation")
final class PlaylistManager {

    static HashMap<String, Playlist> playlists = new HashMap<String, Playlist>(20);
    static ArrayList<String> names = new ArrayList<String>(20);

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "modo";

    static class MySqlPlaylist extends SQLiteOpenHelper {

        private static final String TABLE_PLAYLISTS = "playlists";
        private static final String FIELD_ID = "id";
        private static final String FIELD_NAME = "name";

        private static final String TABLE_ENTRIES = "entries";
        private static final String FIELD_PLID = "plid";
        private static final String FIELD_PATH = "path";
        private static final String FIELD_ZIP = "zip";
        private static final String FIELD_TRACK = "track";
        private static final String FIELD_DISPLAYNAME = "displayname";

        private static final String SQL_CREATE_TABLE_PLAYLISTS = "CREATE TABLE entries (plid INTEGER, path TEXT, zip TEXT, track INTEGER, displayname TEXT)";
        private static final String SQL_DROP_TABLE_PLAYLISTS   = "DROP TABLE IF EXISTS entries";

        private static final String SQL_CREATE_TABLE_ENTRIES = "CREATE TABLE playlists (id INTEGER PRIMARY KEY, name TEXT)";
        private static final String SQL_DROP_TABLE_ENTRIES   = "DROP TABLE IF EXISTS playlists";

        public MySqlPlaylist(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SQL_CREATE_TABLE_PLAYLISTS);
            db.execSQL(SQL_CREATE_TABLE_ENTRIES);
        }

        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL(SQL_DROP_TABLE_PLAYLISTS);
            db.execSQL(SQL_DROP_TABLE_ENTRIES);
            onCreate(db);
        }
    }

    static boolean isAllowDups = true;
    static boolean isLoaded = false;

    static void loadFromSQL(Context c) {

        //android.util.Log.e("SQL", " LOAD FROM SQL1");

        if (isLoaded)
            return;

        //android.util.Log.e("SQL", " LOAD FROM SQL2");

        MySqlPlaylist sqlPlaylists = new MySqlPlaylist(c);

        SQLiteDatabase db = sqlPlaylists.getReadableDatabase();

        if (db != null) {
            try {
                Cursor curLists = db.rawQuery("SELECT * FROM playlists", null);
                if (curLists.moveToFirst()) {
                    do {
                        Playlist p = new Playlist();
                        int id = curLists.getInt(0);
                        String name = curLists.getString(1);
                        Cursor curEntries = db.rawQuery("SELECT path,zip,track,displayname FROM entries WHERE plid=" + id, null);
                        if (curEntries.moveToFirst()) {
                            do {
                                Playlist.Entry e = new Playlist.Entry();
                                e.path = curEntries.getString(0);
                                e.zipEntry = curEntries.getString(1).length() > 1 ? curEntries.getString(1) : null;
                                e.start = curEntries.getInt(2);
                                e.displayname = curEntries.getString(3);
                                p.entries.add(e);
                                p.shadowEntries.add(e);
                            } while (curEntries.moveToNext());
                            p.sort();
                        }
                        playlists.put(name, p);
                        curEntries.close();
                    } while (curLists.moveToNext());
                }
                curLists.close();
            } catch (SQLException e) {
                Log.e("SQL", e.toString());
            }
            db.close();
        }
        sqlPlaylists.close();
        rebuildNamesList();
        isLoaded = true;
    }

    static void  saveToSQL(Context c) {
        //android.util.Log.e("SQL", "SAVE TO SQL1");

        MySqlPlaylist sqlPlaylists = new MySqlPlaylist(c);

        SQLiteDatabase db = sqlPlaylists.getWritableDatabase();

        if (db != null) {
            try {
                db.beginTransaction();

                db.delete(MySqlPlaylist.TABLE_PLAYLISTS, null, null);
                db.delete(MySqlPlaylist.TABLE_ENTRIES, null, null);

                int id = 1;

                InsertHelper helperPlaylists = new InsertHelper(db, MySqlPlaylist.TABLE_PLAYLISTS);
                int index_playlists_id  = helperPlaylists.getColumnIndex(MySqlPlaylist.FIELD_ID);
                int index_playlists_name = helperPlaylists.getColumnIndex(MySqlPlaylist.FIELD_NAME);

                InsertHelper helperEntries = new InsertHelper(db, MySqlPlaylist.TABLE_ENTRIES);
                int index_entries_plid = helperEntries.getColumnIndex(MySqlPlaylist.FIELD_PLID);
                int index_entries_path = helperEntries.getColumnIndex(MySqlPlaylist.FIELD_PATH);
                int index_entries_zip = helperEntries.getColumnIndex(MySqlPlaylist.FIELD_ZIP);
                int index_entries_track = helperEntries.getColumnIndex(MySqlPlaylist.FIELD_TRACK);
                int index_entries_displayname = helperEntries.getColumnIndex(MySqlPlaylist.FIELD_DISPLAYNAME);

                for(String key: playlists.keySet()) {
                    // Store playlist name and id
                    helperPlaylists.prepareForInsert();
                    helperPlaylists.bind(index_playlists_id, id);
                    helperPlaylists.bind(index_playlists_name, key);
                    helperPlaylists.execute();

                    // Store all entries of current playlist with current plid
                    ArrayList<Playlist.Entry> entries = getEntries(key);
                    for (Playlist.Entry pe: entries) {
                        helperEntries.prepareForInsert();
                        helperEntries.bind(index_entries_plid, id);
                        helperEntries.bind(index_entries_path, pe.path);
                        helperEntries.bind(index_entries_zip, pe.zipEntry == null ? "" : pe.zipEntry);
                        helperEntries.bind(index_entries_displayname, pe.displayname);
                        helperEntries.bind(index_entries_track, pe.start);
                        helperEntries.execute();
                    }
                    id += 1;
                }
                db.setTransactionSuccessful();
            } catch (SQLException e) {
                Log.e("SQL", e.toString());
            } finally {
                if (db.inTransaction())
                    db.endTransaction();
                db.close();
            }
        }
        sqlPlaylists.close();
    }

    static void backupToCloud(Context c, BackupDataOutput out) {
        //android.util.Log.e("SQL", "BACKUP TO CLOUD");
        loadFromSQL(c);
        try {
            ByteArrayOutputStream bytesOut = new ByteArrayOutputStream(100000);
            DataOutputStream dataOut = new DataOutputStream(bytesOut);
            for (String key: playlists.keySet()) {
                dataOut.writeInt(-255);
                dataOut.writeUTF(key);
                dataOut.writeInt(getEntries(key).size());
                for (Playlist.Entry pe: getEntries(key)) {
                    dataOut.writeInt(pe.start);
                    dataOut.writeUTF(pe.path);
                    dataOut.writeUTF(pe.zipEntry == null ? "" : pe.zipEntry);
                    dataOut.writeUTF(pe.displayname);
                }
            }
            dataOut.writeInt(-100);
            dataOut.close();

            byte[] buffer = bytesOut.toByteArray();
            out.writeEntityHeader("MODO_PLAYLISTS", buffer.length);
            out.writeEntityData(buffer, buffer.length);

            bytesOut.close();
            buffer = null;
        } catch (IOException e) {
            android.util.Log.e("SQL", e.toString());
        }
    }

    static void restoreFromCloud(Context c, BackupDataInputStream in) {
        //android.util.Log.e("SQL", "RESTORE FROM CLOUD");
        try {
            byte[] data = new byte[in.size()];
            in.read(data);

            DataInputStream dataIn = new DataInputStream(new ByteArrayInputStream(data));

            // Just add the data which is stored in SQL
            playlists.clear();
            isLoaded = false;

            int marker = dataIn.readInt();
            while (marker == -255) {
                String playlistName = dataIn.readUTF();
                Playlist p = new Playlist();
                int size = dataIn.readInt();
                for (int i = 0; i < size; ++i) {
                    Playlist.Entry pe = new Playlist.Entry();
                    pe.start = dataIn.readInt();
                    pe.path = dataIn.readUTF();
                    pe.zipEntry = dataIn.readUTF();
                    pe.displayname = dataIn.readUTF();
                    p.entries.add(pe);
                }
                playlists.put(playlistName, p);
                marker = dataIn.readInt();
            }
            dataIn.close();
            data = null;
            saveToSQL(c);
        } catch (IOException e) {
            android.util.Log.e("SQL", e.toString());
        }
    }

    static int addEntry(String playlist_name, String path, String zipEntry, int start) {
        if (playlists.containsKey(playlist_name)) {
            return playlists.get(playlist_name).add(path, zipEntry, start, PlaylistManager.isAllowDups);
        }
        return 0;
    }

    static ArrayList<Playlist.Entry> getEntries(String playlist_name) {
        ArrayList<Playlist.Entry> allEntries = playlists.get(playlist_name).entries;
        if (allEntries == null)
            allEntries = new ArrayList<Playlist.Entry>(0);
        return allEntries;
    }

    static int removeEntry(String playlist_name, Playlist.Entry pe) {
        Playlist playlist = playlists.get(playlist_name);
        if (playlist != null) {
            playlist.remove(pe);
            return 1;
        }
        return 0;
    }

    static ArrayList<String> getNames() {
        return names;
    }

    static Playlist getPlaylist(String name) {
        return playlists.get(name);
    }

    static int newPlaylist(String name) {
        if (playlists.containsKey(name) == false) {
            playlists.put(name, new Playlist());
            rebuildNamesList();
            return 1;
        }
        return 0;
    }

    static void deletePlaylist(String name) {
        playlists.remove(name);
        rebuildNamesList();
    }

    static boolean hasPlaylists() {
        return !playlists.isEmpty();
    }

    static int renamePlaylist(String oldName, String newName) {
        if (playlists.containsKey(oldName) && playlists.containsKey(newName) == false) {
            playlists.put(newName, playlists.get(oldName));
            playlists.remove(oldName);
            rebuildNamesList();
            return 1;
        }
        return 0;
    }

    // Cache list with all names for quick access (Add button, playlists editor)
    private static void rebuildNamesList() {
        names.clear();
        names.ensureCapacity(playlists.size());
        names.addAll(playlists.keySet());
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
    }

    static final String EXAMPLE_DIRECTORY = "examples" + File.separatorChar;
    static final String EXAMPLE_PLAYLIST_ZIP = "playlist.zip";

    static void createExample(String name, File dir) {
        String pathPlaylist = dir.getAbsolutePath() + File.separatorChar + PlaylistManager.EXAMPLE_PLAYLIST_ZIP;
        Playlist p = new Playlist();
        p.add(pathPlaylist, "Alloyrun Mix.nsf", 0);
        //p.add(pathPlaylist, "Altered Beast.vgz", 0);
        p.add(pathPlaylist, "Another Day in Paradise.sap", 0);
        p.add(pathPlaylist, "Aurora.sid", 0);
        p.add(pathPlaylist, "Banger Management.ay", 0);
        p.add(pathPlaylist, "Big Demo - Starpaws.ym", 0);
        p.add(pathPlaylist, "Cadaver - Goldrunner.nsf", 0);
        //p.add(pathPlaylist, "Castlevania - Map.gym", 0);
        p.add(pathPlaylist, "Chronos in Time.sid", 0);
        p.add(pathPlaylist, "Commando - Highscore.ym", 0);
        p.add(pathPlaylist, "DarkMusicDemo - Part 6.ay", 0);
        p.add(pathPlaylist, "Exolon-MW.it", -1);
        //p.add(pathPlaylist, "Gallantry.spc", 0);
        p.add(pathPlaylist, "Gianna Sisters Remix.nsf", 0);
        p.add(pathPlaylist, "Gray Set Willy.sap", 0);
        p.add(pathPlaylist, "Ikari Tune.sid", 0);
        p.add(pathPlaylist, "Inside Moves.mod", -1);
        p.add(pathPlaylist, "Jogging Olympics 2016.sap", 0);
        p.add(pathPlaylist, "Just Cant Get Enough.ay", 0);
        p.add(pathPlaylist, "Metroid - Rogue Trooper.s3m", -1);
        p.add(pathPlaylist, "MidiaNoctuM.ay", 0);
        //p.add(pathPlaylist, "Robocop.gbs", -1);
        //p.add(pathPlaylist, "Super Boy III.kss", -1);
        p.add(pathPlaylist, "Swirling-Mist.mod", -1);
        //p.add(pathPlaylist, "Terra Cresta II.hes", -1);
        p.add(pathPlaylist, "Toreador_Song.mus", 0);
        playlists.put(name, p);
        rebuildNamesList();
    }
}
