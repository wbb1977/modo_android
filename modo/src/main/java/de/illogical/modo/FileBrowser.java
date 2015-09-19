package de.illogical.modo;

import android.support.v7.app.*;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

final public class FileBrowser
extends ActionBarActivity
implements	OnItemClickListener,
            OnItemLongClickListener,
            OnClickListener,
            OnFocusChangeListener,
            OnTouchListener
{	
    static {
        System.loadLibrary("gme_kode54");
        System.loadLibrary("ym");
        System.loadLibrary("info");
    }

    private native static int loadTitle(String path, byte[] title);

    private static final String TAG = "FileBrowser";
    private Object syncFileAccess = new Object();

    static class ViewHolder {
        TextView fileName;
        TextView fileSize;
        TextView fileInfo;
        ImageView fileImage;
    }

    class SortFiles extends AsyncTask<Comparator<File>, Integer, Integer> {

        protected Integer doInBackground(Comparator<File>... sorter) {
            synchronized (syncFileAccess) {
                Collections.sort(validFiles, sorter[0]);
            }
            return 1;
        }

        protected void onPostExecute(Integer result) {
            finishSort();
        }
    }

    class ReadFiles extends AsyncTask<File, Integer, ArrayList<File>> {

        protected ArrayList<File> doInBackground(File... directory) {

            // wait for sort async task to finish
            synchronized (syncFileAccess) {
                validFiles.clear();
                validFiles.ensureCapacity(1000);
            }

            if (directory[0] instanceof ModoFile) {
                // we have to browse the zip file
                ModoFile currentDirectory = (ModoFile)directory[0];
                try {
                    ZipFile zf = new ZipFile(currentDirectory.getSrc());
                    if (Modo.prefsIsZipFlat) {
                        validFiles.addAll(ModoFile.getAllZipEntries(zf, currentDirectory.getSrc()));
                    } else {
                        // find entries in for current current directory zipentry
                        validFiles.addAll(ModoFile.getEntriesForDirectory(
                                zf,
                                currentDirectory.getZipEntry().substring(0, currentDirectory.getZipEntry().length()-1), // remove trailing slash from directory entry
                                currentDirectory.getSrc()));
                    }
                    zf.close();
                    zf = null;
                } catch (IOException e) {
                    Log.e(TAG, e.toString());
                }
            } else {
                // ok, real directory
                File[] files = directory[0].listFiles(new FilterMusicfiles());
                if (files != null) {
                    validFiles.addAll(Arrays.asList(files));
                }

                File[] zipFiles = directory[0].listFiles(new FilterZipFiles());
                if (zipFiles != null) {
                    if (Modo.prefsIsZipFlat == false) {
                        // standard, include root zip entries
                        //for (int i = 0, l = zipFiles.length; i < l; ++i) {
                        for (File f: zipFiles) {
                            try {
                                ZipFile zf = new ZipFile(f, ZipFile.OPEN_READ);
                                validFiles.addAll(ModoFile.getEntriesForDirectory(zf, "", f));
                                zf.close();
                                zf = null;
                            } catch (IOException e) {
                                Log.e(TAG, e.toString());
                            }
                        }
                    } else {
                        // create on fake directory entry for each zip file
                        for (File f: zipFiles)
                            validFiles.add(new ModoFile(f.getName(), f, true));
                    }
                }
            }
            //Collections.sort(validFiles, new DirectoryFirst());
            Collections.sort(validFiles, sortOrder);

            //get title for every file and store in hashmap
            titles.clear();
            if (Modo.prefsIsScanFiles) {
                byte[] s = new byte[256];
                for (int i = 0, l = validFiles.size(); i < l; ++i) {
                    File f = validFiles.get(i);
                    if (f instanceof ModoFile) {
                        // ToDo: getTitle from files within zip
                    } else if (f.isFile()) {
                        Arrays.fill(s, (byte)0);
                        int length = loadTitle(f.getAbsolutePath(), s);
                        if (length > 0 && length < 256)
                            titles.put(validFiles.get(i), new String(s, 0, length));
                    }
                }
            }
            //

            return validFiles;
        }

        protected void onPostExecute(ArrayList<File> result) {
            finishFileList();
        }
    }


    static class FileAdapter extends BaseAdapter {

        private ArrayList<File> files;
        private HashMap<File, String> des;
        private LayoutInflater inflater;
        private static SparseBooleanArray selected = new SparseBooleanArray();

        FileAdapter(Context c) {
            this.inflater = (LayoutInflater)c.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        void clear() {
            files.clear();
            //selected.clear();
            notifyDataSetChanged();
        }

        ArrayList<File> getVisibleFiles()
        {
            return files;
        }

        void setFiles(ArrayList<File> files, HashMap<File, String> des, boolean clear) {
            this.files = files;
            this.des = des;
            if (clear)
                selected.clear();
            notifyDataSetChanged();
        }

        void clearSelection() {
            selected.clear();
            notifyDataSetChanged();
        }
        void invertSelection(int position) {
            selected.put(position, !(selected.get(position)));
            notifyDataSetChanged();
        }

        boolean hasSelectedItems() {
            boolean ret = false;
            for (int i = 0; i < selected.size() && ret == false; ++i)
                ret = selected.valueAt(i);
            return ret;
        }

        File[] getSelectedFiles() {
            File[] f = new File[selected.size()]; // can contain !null!
            for (int i = 0; i < selected.size(); ++i) {
                int key = selected.keyAt(i);
                f[i] = selected.get(key) ? files.get(key) : null;
            }
            return f;
        }

        public int getViewTypeCount() {
            return 1;
        }

        public int getItemViewType(int position) {
            return 0;
        }

        public boolean areAllItemsEnabled() {
            return true;
        }

        public boolean hasStableIds() {
            return false;
        }

        public int getCount() {
            return files.size();
        }

        public Object getItem(int position) {
            return files.get(position);
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {

            File f = files.get(position);
            ViewHolder holder;

            if (convertView == null) {
                convertView = inflater.inflate(R.layout.file2, null);

                holder = new ViewHolder();
                holder.fileImage = (ImageView)convertView.findViewById(R.id.file_image);
                holder.fileName = (TextView)convertView.findViewById(R.id.file_name);
                holder.fileInfo = (TextView)convertView.findViewById(R.id.file_type);
                holder.fileSize = (TextView)convertView.findViewById(R.id.file_size);

                convertView.setTag(holder);

            } else {
                holder = (ViewHolder)convertView.getTag();
            }

            convertView.setBackgroundColor(selected.get(position) ? ModoPlaylists.HIGHLIGHT_SELECTED_COLOR : Color.TRANSPARENT);

            holder.fileName.setText(f.getName());
            holder.fileName.setTextColor(f instanceof ModoFile ? Color.YELLOW : Color.WHITE);

            if (f.isFile()) {
                if (f.length() <= 1024) {
                    holder.fileSize.setText(String.format("%d bytes - ", f.length()));
                } else {
                    holder.fileSize.setText(String.format("%d kb - ", f.length() / 1024));
                }
                holder.fileSize.setVisibility(View.VISIBLE);
            } else {
                holder.fileSize.setVisibility(View.GONE);
            }

            if (des != null && des.containsKey(f))
                holder.fileInfo.setText(des.get(f));
            else
                holder.fileInfo.setText(getDescription(f));
            holder.fileImage.setImageResource(getDrawableResourceForFile(f));

            return convertView;
        }
    }

    private ListView filegrid;
    private static File currentDirectory;
    private ReadFiles rf = null;
    private SortFiles sf = null;
    private ProgressDialog loadingPleaseWaitDialog;
    private FileAdapter fileAdapter = null;
    private TextView header = null;
    private MenuItem menuSort = null;
    private static Comparator<File> sortOrder = new DirectoryFirstNormal();
    private static HashMap<String, Integer> directoryViewPositions = new HashMap<String, Integer>(100);
    private static HashMap<String, Integer> directoryOffset = new HashMap<String, Integer>(100);
    private ArrayList<File> validFiles = new ArrayList<File>(1000);
    private HashMap<File, String> titles = new HashMap<File, String>(1000);
    private MenuItem menuAdd;
    private boolean clearAfterRotate = true;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.browser);

        header = (TextView)findViewById(R.id.browser_text_header);
        header.setText(Html.fromHtml("<b><font color=\"#FFFFFF\">" + getText(R.string.browser_one_level_up) + "</font></b><br><font color=\"#88FFFFFF\">" + getText(R.string.browser_parent_directory) + "</font>"));
        header.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.arrow_up_float, 0, /*android.R.drawable.ic_menu_revert,*/ android.R.drawable.arrow_up_float, 0);
        header.setOnClickListener(this);
        header.setEnabled(true);
        header.setClickable(true);
        header.setFocusable(true);
        header.setOnFocusChangeListener(this);
        header.setOnTouchListener(this);

        fileAdapter = new FileAdapter(this);
        fileAdapter.setFiles(validFiles, null, false);
        //fileAdapter.notifyDataSetInvalidated();

        filegrid = (ListView)findViewById(R.id.filegrid);
        filegrid.setOnItemClickListener(this);
        filegrid.setOnItemLongClickListener(this);
        filegrid.setAdapter(null);
        filegrid.setDividerHeight(0);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //filegrid.getRootView().setBackgroundColor(prefs.getInt("overlay_color", 0xaa000000));
               
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        
        // currentDirectory is static, so do not override on rotate
        if (savedInstanceState == null)
            currentDirectory = new File(getIntent().getAction());

        clearAfterRotate = (savedInstanceState == null);

        // Check if we have to resume within a zipfile, but only if did not rotate, just one time on startup
        if (savedInstanceState == null) {
            //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String zipFileStr = prefs.getString("zipfile", null);
            String zipDirStr = prefs.getString("zipdir", null); // Stored with trailing slash
            if (zipFileStr != null && zipDirStr != null) {
                // We have to resume from a zip file
                File zipSrc = new File(zipFileStr);
                if (zipSrc.exists())
                    currentDirectory = new ModoFile(Modo.prefsIsZipFlat ? zipSrc.getName() : zipDirStr, zipSrc, true);
                // zipSrc.getName() so that the current file name is displayed in title. Entry does not matter for parsing.
            }
        }

        // In case everything goes wrong this is the fall back
        if (currentDirectory == null)
            currentDirectory = new File("/mnt/");

        populateFilelist(currentDirectory);
    }


    private void savePos() {
        if (validFiles.size() > 0 && currentDirectory != null) {

            directoryViewPositions.put(currentDirectory.getAbsolutePath(), filegrid.getFirstVisiblePosition());
            View v = filegrid.getChildAt(0);
            directoryOffset.put(currentDirectory.getAbsolutePath(), v == null ? 0 : v.getTop());
        }
    }

    private void restorePos() {
        if (currentDirectory != null
            && directoryViewPositions.containsKey(currentDirectory.getAbsolutePath())
            && directoryOffset.containsKey(currentDirectory.getAbsolutePath())) {

            filegrid.setSelectionFromTop(directoryViewPositions.get(currentDirectory.getAbsolutePath()),
                                         directoryOffset.get(currentDirectory.getAbsolutePath()));
        }
    }
   
    protected void onStart() {
        super.onStart();
    }
    
    protected void onStop() {
        super.onStop();
        savePos();
    }
    
    protected void onDestroy() {
        if (rf != null)
            rf.cancel(true);
        rf = null;
        if (sf != null)
            sf.cancel(true);
        sf = null;
        if (loadingPleaseWaitDialog != null)
            loadingPleaseWaitDialog.dismiss();
        super.onDestroy();
    }	

    
    public void onClick(View v) {
        header.setBackgroundResource(android.R.drawable.list_selector_background);
        if (currentDirectory.getParentFile() != null) {
            savePos();
            populateFilelist(currentDirectory.getParentFile());
        } else {
            Boast.makeText(getApplicationContext(), "You did something I was not prepared for. Write me a mail. Thank you!", Toast.LENGTH_LONG).show();
        }
    }
    
    public boolean onTouch(View v, MotionEvent event) {
        header.setBackgroundResource(android.R.drawable.list_selector_background);
        return false;
    }

    public void onFocusChange(View view, boolean hasFocus) {
        header.setBackgroundResource(hasFocus ? android.R.drawable.list_selector_background : android.R.color.transparent);
    }

    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        File selected = (File)fileAdapter.getItem(position);
        if (selected.isFile()) {
            fileAdapter.invertSelection(position);
            updateMenu();
        } else {
            Boast.makeText(this, R.string.dialog_no_directory_atm, Toast.LENGTH_LONG).show();
        }
        return true;
    }

    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        File selected = (File)fileAdapter.getItem(position);
        savePos();
        if (!selected.exists()) {
            populateFilelist(new File("/mnt/"));
        } else if (selected.isDirectory()) {
            populateFilelist(selected);
        } else {
            Modo.setPlaylist(fileAdapter.getVisibleFiles(), selected, currentDirectory instanceof ModoFile ? true : false, position);
            Intent modoplay  = new Intent(selected.getAbsolutePath());
            setResult(RESULT_OK, modoplay);
            finish();
        }
    }

    void finishSort() {
        fileAdapter.setFiles(validFiles, titles, true);
        sf = null;
        loadingPleaseWaitDialog.cancel();
        updateMenu();
    }

    void finishFileList() {
        try {
            getSupportActionBar().setTitle(currentDirectory.getAbsolutePath());
            setTitle(currentDirectory.getAbsolutePath());
            header.setVisibility( currentDirectory.getParentFile() != null ? View.VISIBLE : View.GONE);
            fileAdapter.setFiles(validFiles, titles, clearAfterRotate);
            filegrid.setAdapter(fileAdapter);
            clearAfterRotate = true;
            restorePos();
        } catch (Exception e) {
            Log.e(TAG, "finishFileList(): " + e);
        } finally {
            rf = null;
            loadingPleaseWaitDialog.cancel();
            updateMenu();
        }
    }

    void populateFilelist(File directory) {
        if (rf != null)
            return;

        if (sf != null) {
            sf.cancel(true);
            sf = null;
        }

        fileAdapter.clear();
        filegrid.setAdapter(null);

        currentDirectory = directory;
        //setTitle(directory.getAbsolutePath());
        getSupportActionBar().setTitle(currentDirectory.getAbsolutePath());

        rf = new ReadFiles();
        rf.execute(currentDirectory);
        loadingPleaseWaitDialog = ProgressDialog.show(this, "", getText(R.string.reading_directory), true, true);
        updateMenu();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.browser, menu);
        return true;

    }

    private void updateMenu() {
        if (menuSort != null) {
            menuSort.setEnabled(rf == null && sf == null);
            if (sortOrder instanceof DirectoryFirstNormal)
                menuSort.setTitle(R.string.browser_menu_sort_invert);
            if (sortOrder instanceof DirectoryFirstInvert)
                menuSort.setTitle(R.string.browser_menu_sort_normal);
        }
        if (menuAdd != null)
            menuAdd.setEnabled(PlaylistManager.hasPlaylists() && rf == null && sf == null && fileAdapter.hasSelectedItems());
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        menuSort = menu.findItem(R.id.menu_sort);
        menuAdd = menu.findItem(R.id.menu_add_tune);
        updateMenu();
        return true;
    }

    class DialogWhichPlaylist implements android.content.DialogInterface.OnClickListener {
        private ArrayList<String> lists;
        private File[] filesToAdd;

        DialogWhichPlaylist(ArrayList<String> playlistNames) {
            lists = playlistNames;
            filesToAdd = fileAdapter.getSelectedFiles();
        }
        public void onClick(DialogInterface dialog, int which) {
            if (which < 0)
                return;
            if (which >= lists.size())
                return;
            synchronized(Modo.playlistSync) {
                for (int i = 0; i < filesToAdd.length; ++i) {
                    if (filesToAdd[i] == null)
                        continue;
                    if (filesToAdd[i] instanceof ModoFile)
                        PlaylistManager.addEntry(lists.get(which), ((ModoFile)filesToAdd[i]).getSrc().getAbsolutePath(), ((ModoFile)filesToAdd[i]).getZipEntry(), -1);
                    else
                        PlaylistManager.addEntry(lists.get(which), filesToAdd[i].getAbsolutePath(), null, -1);
                }
            }
            fileAdapter.clearSelection();
        }
    }
    
    void askWhichPlaylist() {
        ArrayList<String> lists = PlaylistManager.getNames();
        AlertDialog.Builder ab = new AlertDialog.Builder(this);
        ab.setAdapter(new ArrayAdapter<String>(this, R.layout.arrayadapter, lists), new DialogWhichPlaylist(lists));
        ab.setCancelable(false);
        ab.setTitle(R.string.dialog_add_files_title);
        ab.setNegativeButton(android.R.string.cancel, null);
        ab.create().show();
    }	

    @SuppressWarnings("unchecked")
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_sort:
                if (sortOrder instanceof DirectoryFirstNormal)
                    sortOrder = new DirectoryFirstInvert();
                else if (sortOrder instanceof DirectoryFirstInvert)
                    sortOrder = new DirectoryFirstNormal();
                if (sf == null) {
                    //savePos();
                    sf = new SortFiles();
                    sf.execute(sortOrder);
                    loadingPleaseWaitDialog = ProgressDialog.show(this, "", getText(R.string.reading_directory), true, true);
                }
                updateMenu();
                break;
            case R.id.menu_add_tune:
                if (fileAdapter.hasSelectedItems() && PlaylistManager.hasPlaylists())
                    askWhichPlaylist();
            default:
                return false;
        }
        return true;
    }

    static String getDescription(File file) {
        if (file.isDirectory()) {
            // Hack to localize without access to AooContext
            if (Locale.getDefault().getLanguage().equalsIgnoreCase("de"))
                return "Verzeichnis";
            if (Locale.getDefault().getLanguage().equalsIgnoreCase("es"))
                return "Directorio";
            if (Locale.getDefault().getLanguage().equalsIgnoreCase("fr"))
                return "Annuaire";
            return "Directory";
        }

        String fname = file.getName().toLowerCase(Locale.getDefault());

        // YM Decoder supported files
        if (fname.endsWith(".ym"))
            return "Atari ST / Amstrad CPC";

        // GME Decoder supported files
        if (fname.endsWith(".kss"))
            return "MSX / Sega";

        if (fname.endsWith(".hes"))
            return "PC-Engine";

        if (fname.endsWith(".rsn"))
            return "Super Nintendo";

        if (fname.endsWith(".rsn"))
            return "Super Nintendo";

        if (fname.endsWith(".vgm"))
            return "Sega Genesis / SMS";

        if (fname.endsWith(".vgz"))
            return "Sega Genesis / SMS";

        if (fname.endsWith(".ay"))
            return "ZX Spectrum / Amstrad CPC";

        if (fname.endsWith(".gym"))
            return "Sega Mega Drive";

        if (fname.endsWith(".nsf"))
            return "Nintendo NES";

        if (fname.endsWith(".nsfe"))
            return "Nintendo NES";

        if (fname.endsWith(".sap"))
            return "Atari Pokey Sound";

        if (fname.endsWith(".spc"))
            return "Super Nintendo";

        if (fname.endsWith(".gbs"))
            return "Nintendo Game Boy";

        // Mikmod
        if (fname.endsWith(".mod"))
            return "Amiga ProTracker";

        if (fname.endsWith(".xm"))
            return "FastTracker";

        if (fname.endsWith(".s3m"))
            return "ScreamTracker";

        if (fname.endsWith(".it"))
            return "ImpulseTracker";

        if (fname.endsWith(".med"))
            return "OctaMED";

        if (fname.endsWith(".okt"))
            return "Amiga Oktalyzer";

        //if (fname.endsWith(".umx"))
        //	return "Unreal";

        if (fname.startsWith("mod."))
            return "Amiga ProTracker";

        if (fname.startsWith("xm."))
            return "FastTracker";

        if (fname.startsWith("s3m."))
            return "ScreamTracker";

        if (fname.startsWith("it."))
            return "ImpulseTracker";

        if (fname.startsWith("med."))
            return "OctaMED";

        if (fname.startsWith("okt."))
            return "Amiga Oktalyzer";

        //if (fname.startsWith(".umx"))
        //	return "Unreal";

        // sidplay2
        if (fname.endsWith(".sid"))
            return "C64 Music";

        if (fname.endsWith(".mus"))
            return "C64 Music";

        if (fname.endsWith(".cop"))
            return "Sam Coupe";

        if (fname.endsWith(".sng"))
            return "Same Coupe";

        return "??";
    }

    static int getDrawableResourceForFile(File file) {
        if (file.isDirectory())
            return R.drawable.ic_file_folder;
        return getDrawableResourceForName(file.getName().toLowerCase(Locale.getDefault()));
    }

    static int getDrawableResourceForName(String fname) {

        if (fname.indexOf(File.separatorChar) > 0)
            fname = fname.substring(fname.lastIndexOf(File.separatorChar) + 1);

        // YM Decoder supported files
        if (fname.endsWith(".ym"))
            return R.drawable.file_atari_red;

        // GME Decoder supported files
        if (fname.endsWith(".kss"))
            return R.drawable.file_msx;

        if (fname.endsWith(".hes"))
            return R.drawable.file_pcengine;

        if (fname.endsWith(".vgz"))
            return R.drawable.file_sega;

        if (fname.endsWith(".vgm"))
            return R.drawable.file_sega;

        if (fname.endsWith(".ay"))
            return R.drawable.file_ay;

        if (fname.endsWith(".gym"))
            return R.drawable.file_sega;

        if (fname.endsWith(".nsf"))
            return R.drawable.file_nes;

        if (fname.endsWith(".nsfe"))
            return R.drawable.file_nes;

        if (fname.endsWith(".sap"))
            return R.drawable.file_atari_green;

        if (fname.endsWith(".spc"))
            return R.drawable.file_snes;

        if (fname.endsWith(".rsn"))
            return R.drawable.file_snes;

        if (fname.endsWith(".gbs"))
            return R.drawable.file_gameboy;

        // Mikmod
        if (fname.endsWith(".mod"))
            return R.drawable.file_amiga;

        if (fname.endsWith(".xm"))
            return R.drawable.file_amiga;

        if (fname.endsWith(".s3m"))
            return R.drawable.file_amiga;

        if (fname.endsWith(".it"))
            return R.drawable.file_amiga;

        if (fname.endsWith(".med"))
            return R.drawable.file_amiga;

        if (fname.endsWith(".okt"))
            return R.drawable.file_amiga;

        //if (fname.endsWith(".umx"))
        //	return R.drawable.file_abri_amiga;

        if (fname.startsWith("mod."))
            return R.drawable.file_amiga;

        if (fname.startsWith("xm."))
            return R.drawable.file_amiga;

        if (fname.startsWith("s3m."))
            return R.drawable.file_amiga;

        if (fname.startsWith("it."))
            return R.drawable.file_amiga;

        if (fname.startsWith("med."))
            return R.drawable.file_amiga;

        if (fname.startsWith("okt."))
            return R.drawable.file_amiga;

        //if (fname.startsWith("umx."))
        //	return R.drawable.file_abri_amiga;

        // sidplay2
        if (fname.endsWith(".sid"))
            return R.drawable.file_c64;

        if (fname.endsWith(".mus"))
            return R.drawable.file_c64;

        // unknown file
        return R.drawable.icon;
    }


    static class DirectoryFirstNormal implements Comparator<File> {
        public int compare(File file1, File file2) {
            if (file1.isDirectory() && file2.isFile())
                return -1;
            if (file1.isFile() && file2.isDirectory())
                return 1;
            return file1.getName().compareToIgnoreCase(file2.getName());
        }
    }

    static class DirectoryFirstInvert implements Comparator<File> {
        public int compare(File file1, File file2) {
            if (file1.isDirectory() && file2.isFile())
                return -1;
            if (file1.isFile() && file2.isDirectory())
                return 1;
            return file1.getName().compareToIgnoreCase(file2.getName()) * -1;
        }
    }

    static class FilterZipFiles implements FileFilter {
        public boolean accept(File pathname) {
            if (pathname.getName().toLowerCase(Locale.getDefault()).endsWith(".zip") && pathname.isFile() && pathname.canRead())
                return true;
            return false;
        }
    }

    static class FilterMusicfiles implements FileFilter {
        public boolean accept(File pathname) {

            if (pathname.isHidden())
                return false;
            if (pathname.isDirectory())
                return true;
            if (!pathname.canRead())
                return false;
            if (pathname.length() < 100)
                return false;

            String fname = pathname.getName().toLowerCase(Locale.getDefault());

            // YM Decoder supported files
            if (fname.endsWith(".ym"))
                return true;

            // GME Decoder supported files
            if (fname.endsWith(".hes"))
                return true;

            if (fname.endsWith(".kss"))
                return true;

            if (fname.endsWith(".vgz"))
                return true;

            if (fname.endsWith(".vgm"))
                return true;

            if (fname.endsWith(".ay"))
                return true;

            if (fname.endsWith(".gym"))
                return true;

            if (fname.endsWith(".nsf"))
                return true;

            if (fname.endsWith(".nsfe"))
                return true;

            if (fname.endsWith(".sap"))
                return true;

            if (fname.endsWith(".spc"))
                return true;

            if (fname.endsWith(".rsn"))
                return true;

            if (fname.endsWith(".gbs"))
                return true;

            // Mikmod
            if (fname.endsWith(".mod"))
                return true;

            if (fname.endsWith(".xm"))
                return true;

            if (fname.endsWith(".it"))
                return true;

            if (fname.endsWith(".s3m"))
                return true;

            if (fname.endsWith(".med"))
                return true;

            if (fname.endsWith(".okt"))
                return true;

            //if (fname.endsWith(".umx"))
            //	return true;

            if (fname.startsWith("mod."))
                return true;

            if (fname.startsWith("xm."))
                return true;

            if (fname.startsWith("it."))
                return true;

            if (fname.startsWith("s3m."))
                return true;

            if (fname.startsWith("med."))
                return true;

            if (fname.startsWith("okt."))
                return true;

            //if (fname.startsWith("umx."))
            //	return true;

            // sidplay2
            if (fname.endsWith(".sid"))
                    return true;

            if (fname.endsWith(".mus"))
                return true;

            // Not supported
            return false;
        }
    }

    static boolean acceptZipEntry(ZipEntry e) {

        if (e.isDirectory())
            return true;
        if (e.getSize() < 100)
            return false;

        String fname = e.getName().toLowerCase(Locale.getDefault());

        // ZipEntry name() contains the entire path. Only filename is required to check filename first and lasts characters.
        if (fname.lastIndexOf(File.separatorChar) != -1)
            fname = fname.substring(fname.lastIndexOf(File.separatorChar) + 1);

        // YM Decoder supported files
        if (fname.endsWith(".ym"))
            return true;

        // GME Decoder supported files
        if (fname.endsWith(".hes"))
            return true;

        if (fname.endsWith(".kss"))
            return true;

        if (fname.endsWith(".vgz"))
            return true;

        if (fname.endsWith(".vgm"))
            return true;

        if (fname.endsWith(".ay"))
            return true;

        if (fname.endsWith(".gym"))
            return true;

        if (fname.endsWith(".nsf"))
            return true;

        if (fname.endsWith(".nsfe"))
            return true;

        if (fname.endsWith(".sap"))
            return true;

        if (fname.endsWith(".spc"))
            return true;

        if (fname.endsWith(".rsn"))
            return true;

        if (fname.endsWith(".gbs"))
            return true;

        // Mikmod
        if (fname.endsWith(".mod"))
            return true;

        if (fname.endsWith(".xm"))
            return true;

        if (fname.endsWith(".it"))
            return true;

        if (fname.endsWith(".s3m"))
            return true;

        if (fname.endsWith(".med"))
            return true;

        if (fname.endsWith(".okt"))
            return true;

        //if (fname.endsWith(".umx"))
        //	return true;

        if (fname.startsWith("mod."))
            return true;

        if (fname.startsWith("xm."))
            return true;

        if (fname.startsWith("it."))
            return true;

        if (fname.startsWith("s3m."))
            return true;

        if (fname.startsWith("med."))
            return true;

        if (fname.startsWith("okt."))
            return true;

        //if (fname.startsWith("umx."))
        //	return true;

        // sidplay2
        if (fname.endsWith(".sid"))
            return true;

        if (fname.endsWith(".mus"))
            return true;

        return false;
    }
}
