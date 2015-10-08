package de.illogical.modo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import de.illogical.modo.Playlist.Entry;

import android.*;
import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.AdapterView.OnItemSelectedListener;

final public class ModoPlaylistEntries extends AppCompatActivity
implements
    OnItemLongClickListener,
    OnItemClickListener,
    OnItemSelectedListener,
    Modo.OnNextPlaylistEntryListener
{

    private static HashMap<String, Integer> savedPositions = new HashMap<String, Integer>(10);
    private static HashMap<String, Integer> savedTops = new HashMap<String, Integer>(10); // :-)

    private int selectedIndex = -1;
    private MenuItem menuRemove;
    private TextView infoText;
    private ListView vEntries;
    private ArrayList<Playlist.Entry> entries = new ArrayList<Playlist.Entry>(0);
    private EntryAdapter adapter;
    private String playlist;

    static class ViewHolder {
        TextView textFile;
        TextView textTracknr;
        //TextView textTrack;
        ImageView icon;
    }

    static class EntryAdapter extends BaseAdapter {

        private LayoutInflater inflater;
        private ArrayList<Playlist.Entry> e;
        private int selectedIndex = -1;
        private Playlist.Entry pe;

        public EntryAdapter(Context c) {
            inflater = (LayoutInflater)c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        void setSelectedIndex(int index) {
            selectedIndex = index;
            notifyDataSetChanged();
        }

        void setEntries(ArrayList<Playlist.Entry> entries) {
            e = entries;
            selectedIndex = -1;
            notifyDataSetChanged();
        }

        public int getCount() {
            return e.size();
        }

        public Object getItem(int index) {
            synchronized (Modo.playlistSync) { return e.get(index); }
        }

        public long getItemId(int index) {
            return index;
        }

        public View getView(int index, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.playlist_file, null);
                holder = new ViewHolder();
                holder.textFile = (TextView)convertView.findViewById(R.id.playlist_entry_filename);
                holder.textTracknr = (TextView)convertView.findViewById(R.id.playlist_entry_tracknr);
                //holder.textTrack = (TextView)convertView.findViewById(R.id.playlist_entry_track);
                holder.icon = (ImageView)convertView.findViewById(R.id.playlist_entry_icon);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder)convertView.getTag();
            }
            synchronized (Modo.playlistSync) { pe = e.get(index); } // only useful if this playlist is in use
            convertView.setBackgroundColor(Color.TRANSPARENT);
            if (pe == Modo.playing)
                convertView.setBackgroundColor(ModoPlaylists.HIGHLIGHT_PLAYING_COLOR);
            if (index == selectedIndex)
                convertView.setBackgroundColor(ModoPlaylists.HIGHLIGHT_SELECTED_COLOR);
            if (pe.zipEntry == null)
                holder.icon.setImageResource(FileBrowser.getDrawableResourceForName(pe.path.toLowerCase(Locale.getDefault())));
            else
                holder.icon.setImageResource(FileBrowser.getDrawableResourceForName(pe.zipEntry.toLowerCase(Locale.getDefault())));
            holder.textFile.setText(pe.displayname);
            holder.textFile.setSelected(true);
            holder.textTracknr.setText(pe.start >= 0 ? String.valueOf(pe.start + 1) : "-");
            //holder.textTracknr.setText(String.valueOf(pe.start + 1));
            //holder.textTrack.setVisibility(pe.start >= 0 ? View.VISIBLE : View.INVISIBLE);
            //holder.textTracknr.setVisibility(pe.start >= 0 ? View.VISIBLE : View.INVISIBLE);
            return convertView;
        }

        public boolean areAllItemsEnabled() {
            return true;
        }

        public boolean hasStableIds() {
            return false;
        }

        public int getViewTypeCount() {
            return 1;
        }
    }

    class DialogRemoveYes implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                synchronized (Modo.playlistSync) { PlaylistManager.removeEntry(playlist, entries.get(selectedIndex)); }
                Modo.myModo.backup.dataChanged();
                selectedIndex = -1;
                updateList();
            }
        }
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.playlist_files);

        playlist = getIntent().getAction();

        setTitle(playlist);

        adapter = new EntryAdapter(getApplicationContext());
        adapter.setEntries(entries);

        infoText = (TextView)findViewById(R.id.playlists_entries_infottext);
        vEntries = (ListView)findViewById(R.id.list_playlists_entries);
        vEntries.setAdapter(adapter);
        vEntries.setLongClickable(true);
        vEntries.setOnItemClickListener(this);
        vEntries.setOnItemLongClickListener(this);
        vEntries.setOnItemSelectedListener(this);

        updateList();

        if (savedInstanceState != null) {
            selectedIndex = savedInstanceState.getInt("selectedIndex", -1);
            adapter.setSelectedIndex(selectedIndex);
        }

        vEntries.setSelectionFromTop(savedPositions.containsKey(playlist) ? savedPositions.get(playlist) : 0, savedTops.containsKey(playlist) ? savedTops.get(playlist) : 0);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //vEntries.getRootView().setBackgroundColor(prefs.getInt("overlay_color", 0xaa000000));
    }

    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("selectedIndex", selectedIndex);
    }

    protected void onStart() {
        super.onStart();
        Modo.myModo.addOnNextPlaylistEntryListener(this);
    }

    protected void onDestroy() {
        Modo.myModo.removeOnNextPlaylistEntryListener(this);
        savedPositions.put(playlist, vEntries.getFirstVisiblePosition());
        savedTops.put(playlist, vEntries.getChildAt(0) == null ? 0 : vEntries.getChildAt(0).getTop());
        super.onDestroy();
    }

    void updateList() {
        // currently not a copy, but a direct reference to the Playlist entries!
        entries = PlaylistManager.getEntries(playlist);
        adapter.setEntries(entries);
        updateMenuStatus();
    }

    void askRemoveEntry() {
        if (entries.size() > 1) {
            AlertDialog.Builder ab = new AlertDialog.Builder(this);
            ab.setCancelable(false);
            ab.setTitle(R.string.dialog_remove_entry_title);
            synchronized ( Modo.playlistSync) {
                if (entries.get(selectedIndex).start >= 0)
                    ab.setMessage(getResources().getString(R.string.dialog_remove_entry_message, entries.get(selectedIndex).displayname, String.valueOf(entries.get(selectedIndex).start + 1)));
                else
                    ab.setMessage(getResources().getString(R.string.dialog_delete_playlist_message, entries.get(selectedIndex).displayname));
            }
            ab.setNegativeButton(android.R.string.no, null);
            ab.setPositiveButton(android.R.string.yes, new DialogRemoveYes());
            ab.create().show();
        } else {
            Boast.makeText(this, R.string.dialog_remove_entry_fail, Toast.LENGTH_LONG).show();
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.playlist_entries, menu);
        return true;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        menuRemove = menu.findItem(R.id.menu_delete_playlistentry);
        updateMenuStatus();
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_delete_playlistentry:
                if (selectedIndex >= 0)
                    askRemoveEntry();
                break;
            default:
                return false;
        }
        return true;
    }

    void updateMenuStatus() {
        if (menuRemove != null)
            menuRemove.setEnabled(selectedIndex >= 0);
        if (entries.isEmpty()) {
            infoText.setVisibility(View.VISIBLE);
            vEntries.setVisibility(View.INVISIBLE);
        } else {
            infoText.setVisibility(View.GONE);
            vEntries.setVisibility(View.VISIBLE);
        }

    }

    public void onItemSelected(AdapterView<?> parent, View view, int index, long id) {
        selectedIndex = index;
        adapter.setSelectedIndex(index);
        updateMenuStatus();
    }

    public void onNothingSelected(AdapterView<?> parent) {
        selectedIndex = -1;
        adapter.setSelectedIndex(-1);
        updateMenuStatus();
    }

    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
            onItemClick(null, null, requestCode, 0);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED)
            Boast.makeText(getApplicationContext(), R.string.permission_request, Toast.LENGTH_LONG).show();
    }

    public void onItemClick(AdapterView<?> parent, View view, int index, long id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE}, index);
            return;
        }
        selectedIndex = -1;
        adapter.setSelectedIndex(-1);
        Modo.myModo.setPlaylist(playlist, index);
        updateMenuStatus();
    }

    public boolean onItemLongClick(AdapterView<?> parent, View view, int index, long id) {
        if (index == selectedIndex) {
            selectedIndex = -1;
            adapter.setSelectedIndex(-1);
        } else if (selectedIndex != index) {
            selectedIndex = index;
            adapter.setSelectedIndex(selectedIndex);
        }
        updateMenuStatus();
        return true;
    }

    public void nextEntry(String playlistname, Entry entry) {
        adapter.notifyDataSetChanged();
        // Modo.playing refers to this entry
    }
}
