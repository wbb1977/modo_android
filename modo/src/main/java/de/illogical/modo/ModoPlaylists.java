package de.illogical.modo;

import java.util.ArrayList;
import de.illogical.modo.Playlist.Entry;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

final public class ModoPlaylists extends ActionBarActivity
implements
	OnItemLongClickListener,
	OnItemClickListener,
	OnItemSelectedListener,
	Modo.OnNextPlaylistEntryListener
{

	static final int HIGHLIGHT_PLAYING_COLOR = 0x550000FF;
	static final int HIGHLIGHT_SELECTED_COLOR = 0x55FFFFFF;
	
	private static int savedPosition = 0;
	private static int savedY = 0;
	
	private MenuItem menuNew;
	private MenuItem menuDelete;
	private MenuItem menuRename;
	private int selectedIndex = -1;
	private ListView vPlaylists;
	private ArrayList<String> names = new ArrayList<String>(0);
	private PlaylistAdapter adapter;
	
	static class ViewHolder {
		TextView vPlaylistName;
	}
	
	static class PlaylistAdapter extends BaseAdapter {

		private LayoutInflater inflater;
		private ArrayList<String> n;
		private int selectedIndex = -1;
		
		PlaylistAdapter(Context c) {
			inflater = (LayoutInflater)c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}
		
		void setSelectedIndex(int index) {
			selectedIndex = index;
			notifyDataSetChanged();
		}
		
		void setNames(ArrayList<String> names) {
			n = names;
			selectedIndex = -1;
			notifyDataSetChanged();
		}
		
		public int getCount() {
			return n.size();
		}

		public Object getItem(int index) {
			return n.get(index);
		}

		public long getItemId(int index) {
			return index;
		}

		public View getView(int index, View convertView, ViewGroup parent) {
			ViewHolder holder;
			if (convertView == null) {
				convertView = inflater.inflate(R.layout.playlist_entry, null);
				holder = new ViewHolder();
				holder.vPlaylistName = (TextView)convertView.findViewById(R.id.pl_entry_name);
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder)convertView.getTag();
			}
			convertView.setBackgroundColor(Color.TRANSPARENT);
			if (n.get(index).equals(Modo.playlistname))
				convertView.setBackgroundColor(ModoPlaylists.HIGHLIGHT_PLAYING_COLOR);
			if (index == selectedIndex)
				convertView.setBackgroundColor(ModoPlaylists.HIGHLIGHT_SELECTED_COLOR);
			holder.vPlaylistName.setText(n.get(index));
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
	
	class DialogDeleteYes implements DialogInterface.OnClickListener {
		public void onClick(DialogInterface dialog, int which) {
			if (which == DialogInterface.BUTTON_POSITIVE) {
				PlaylistManager.deletePlaylist(names.get(selectedIndex));
				selectedIndex = -1;
				Modo.myModo.backup.dataChanged();
				updateList();
			}
		}
	}
	
	
	class DialogNewYes implements DialogInterface.OnClickListener {
		private Context c;
		private EditText input; 
		
		public DialogNewYes(Context context, EditText inputText) {
			c = context;
			input = inputText;
		}
		
		public void onClick(DialogInterface dialog, int which) {
			if (which == DialogInterface.BUTTON_POSITIVE) {
				String newName = input.getText().toString().trim();
				if (newName.length() > 0) {
					if (PlaylistManager.newPlaylist(newName) <= 0)
						Toast.makeText(c, R.string.dialog_new_playlist_fail, Toast.LENGTH_LONG).show();
					else
						Modo.myModo.backup.dataChanged();
				} else {
					Toast.makeText(c, R.string.dialog_new_playlist_fail2, Toast.LENGTH_LONG).show();
				}
				selectedIndex = -1;
				updateList();
			}
		}		
	}	
	
	class DialogRenameYes implements DialogInterface.OnClickListener {
		private Context c;
		private EditText input; 
		
		public DialogRenameYes(Context context, EditText inputText) {
			c = context;
			input = inputText;
		}
		
		public void onClick(DialogInterface dialog, int which) {
			if (which == DialogInterface.BUTTON_POSITIVE) {
				String newName = input.getText().toString().trim();
				String oldName = names.get(selectedIndex);
				if (newName.length() > 0) {
					if (PlaylistManager.renamePlaylist(names.get(selectedIndex), newName) > 0) {
						// Trying to update playlist name on player view
						if (oldName.equals(Modo.playlistname)) {
							Modo.playlistname = newName;
							Modo.myModo.updatePlaylistname();
							Modo.myModo.backup.dataChanged();
						}
					} else {
						Toast.makeText(c, R.string.dialog_rename_playlist_fail, Toast.LENGTH_LONG).show();
					}
				} else {
					Toast.makeText(c, R.string.dialog_rename_playlist_fail2, Toast.LENGTH_LONG).show();
				}
				selectedIndex = -1;
				updateList();
			}
		}		
	}
	
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.playlist);
	
		vPlaylists = (ListView)findViewById(R.id.list_playlists);
		
		adapter = new PlaylistAdapter(getApplicationContext());
		adapter.setNames(names);
		vPlaylists.setAdapter(adapter);
		vPlaylists.setLongClickable(true);
		vPlaylists.setOnItemClickListener(this);
		vPlaylists.setOnItemLongClickListener(this);
		vPlaylists.setOnItemSelectedListener(this);
		
		updateList();

		if (savedInstanceState != null) {
			selectedIndex = savedInstanceState.getInt("selectedIndex", -1);
			adapter.setSelectedIndex(selectedIndex);
		}
		
		vPlaylists.setSelectionFromTop(savedPosition, savedY);
		
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        vPlaylists.getRootView().setBackgroundColor(prefs.getInt("overlay_color", 0xaa000000));		
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
		savedPosition = vPlaylists.getFirstVisiblePosition();
		savedY = (vPlaylists.getChildAt(0) == null ? 0 : vPlaylists.getChildAt(0).getTop());
		super.onDestroy();
	}
	
	void updateList() {
		names = PlaylistManager.getNames();
		adapter.setNames(names);
		updateMenuStatus();
	}
	
	void askNewPlaylist() {
		EditText input = new EditText(this);
		input.setMaxLines(1);
		input.setSingleLine(true);
		input.setInputType(InputType.TYPE_CLASS_TEXT);
		if (selectedIndex >= 0)
			input.setText(names.get(selectedIndex));
		AlertDialog.Builder ab = new AlertDialog.Builder(this);
		ab.setCancelable(false);
		ab.setTitle(R.string.dialog_new_playlist_title);
		ab.setView(input);
		ab.setNegativeButton(android.R.string.cancel, null);
		ab.setPositiveButton(android.R.string.ok, new DialogNewYes(this, input));
		ab.create().show();		
	}
	
	void askRenamePlaylist() {
		EditText input = new EditText(this);
		input.setText(names.get(selectedIndex));
		input.setMaxLines(1);
		input.setSingleLine(true);
		input.setInputType(InputType.TYPE_CLASS_TEXT);
		AlertDialog.Builder ab = new AlertDialog.Builder(this);
		ab.setCancelable(false);
		ab.setTitle(R.string.dialog_rename_playlist_title);
		ab.setView(input);
		ab.setNegativeButton(android.R.string.cancel, null);
		ab.setPositiveButton(android.R.string.ok, new DialogRenameYes(this, input));
		ab.create().show();
	}
	
	void askDeletePlaylist() {
		AlertDialog.Builder ab = new AlertDialog.Builder(this);
		ab.setCancelable(false);
		ab.setTitle(R.string.dialog_delete_playlist_title);		
		ab.setMessage(getResources().getString(R.string.dialog_delete_playlist_message, names.get(selectedIndex)));
		ab.setNegativeButton(android.R.string.no, null);
		ab.setPositiveButton(android.R.string.yes, new DialogDeleteYes());
		ab.create().show();
	}
	
	public boolean onCreateOptionsMenu(Menu menu) {
    	getMenuInflater().inflate(R.menu.playlists, menu);
		return true;
	}
	
	public boolean onPrepareOptionsMenu(Menu menu) {
		menuNew = menu.findItem(R.id.menu_new_playlist);
		menuDelete = menu.findItem(R.id.menu_delete_playlist);
		menuRename = menu.findItem(R.id.menu_rename_playlist);
		updateMenuStatus();
		return true;
	}
	
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_delete_playlist:
				if (selectedIndex >= 0)
					askDeletePlaylist();
				break;
			case R.id.menu_rename_playlist:
				if (selectedIndex >= 0)
					askRenamePlaylist();
				break;
			case R.id.menu_new_playlist:
				askNewPlaylist();
				break;
			default:
				return false;
		}
		return true;
	}
	
	void updateMenuStatus() {
		if (menuNew != null) {
			menuNew.setEnabled(true);
			menuDelete.setEnabled(selectedIndex >= 0);
			menuRename.setEnabled(selectedIndex >= 0);
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

	public void onItemClick(AdapterView<?> parent, View view, int index, long id) {
		selectedIndex = -1;
		adapter.setSelectedIndex(-1);
		updateMenuStatus();
		String playlist = (String)adapter.getItem(index);
		Intent i = new Intent(this, ModoPlaylistEntries.class);
		i.setAction(playlist);
		startActivity(i);
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
	}
}
