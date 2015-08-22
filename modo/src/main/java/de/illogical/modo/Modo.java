/* 
 * 
 * Modo v2.3
 * 
 * by
 * 
 * wb@illogical.de
 * 
 * 
 * ToDo:
 * * modo single, use intent from receiver
 */

package de.illogical.modo;

import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.session.MediaController;
import android.os.Build;
import android.support.v7.app.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.backup.BackupManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Handler.Callback;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

final public class Modo
extends ActionBarActivity
implements	OnSeekBarChangeListener,
            OnLongClickListener,
            OnAudioFocusChangeListener,
            Callback,
            ShakeEventListener.OnShakeListener {

    private static final String TAG = "Modo";

    // For the Android emulator set this to false to disable accelmeter (app wont start), fixed in 4.4
    final static boolean isRealMachine = true;

    // Sync JNI calls / decoder calls via this
    public final static Object sync = new Object();
    final static Object playlistSync = new Object();

    // Message types (what value)
    // Everything > 0 is used for play time, track, status progress
    static final int MESSAGE_AUDIOFOCUS_CHANGE = -10;
    static final int MESSAGE_SHAKE = -20;

    // Playlist
    final static int MAX_PLAYLIST_FILES = 2000;
    static Playlist playlist;
    static String playlistname;
    static Playlist.Entry playing;
    private static ModoFile withinZipfile;
    private static Playlist.Entry[] peCache = new Playlist.Entry[MAX_PLAYLIST_FILES];
    private static Playlist playlistFiles = new Playlist();
    static {
        for (int i = 0; i < peCache.length; ++i) {
            peCache[i] = new Playlist.Entry();
            peCache[i].displayname = "";
            peCache[i].start = -1;
        }
    }

    // Import control variables to restore state
    private static File path = null;
    private static int sleepTimer = 0;
    static Decoder decoder;
    private static boolean isLoadingOkay = false;
    private static int track = 0;
    private static boolean isPause = false;
    private static String fileInfo;

    // Import control variables, can be computed
    private long trackLength = -1;
    private int tracks = 0;
    private String strTrackLength = "";
    int playtimeStamp = 0;
    boolean forceUpdate = false;

    // Rest
    static private boolean pauseStatusOnAudioFocusLost = false;
    private boolean isFastForward = false;
    private int playTimeFirstClickOnTrackZero = 0;
    private boolean skipTrackUpdate =  false;

    // Preferences settings
    static int prefsSoundboost = 0; // Used by playback service.
    private int[] prefsFormatSoundboost = new int[12];
    private final int BOOST_MODULES = 0;
    private final int BOOST_NSF = 1;
    private final int BOOST_SPC = 2;
    private final int BOOST_VGM = 3;
    private final int BOOST_AY  = 4;
    private final int BOOST_SAP = 5;
    private final int BOOST_HES = 6;
    private final int BOOST_KSS = 7;
    private final int BOOST_YM = 8;
    private final int BOOST_GYM = 9;
    private final int BOOST_GBS = 10;
    private final int BOOST_SID = 11;
    private final int ACTION_LOOP = 0;
    private final int ACTION_PAUSE = 1;
    private final int ACTION_EXIT = 2;

    boolean prefsIsMediabuttons; // ModoReceiver access this directly! do better!!
    private int prefsDefaultTrackLength;
    private int prefSilence;
    private boolean prefsIsStereoAY;
    private boolean prefsIsShuffle;
    private boolean prefsIsLoop;
    private boolean prefsIs99; // It could be done better, instead of these two, just use one. But this feature was added later. sorry.
    private boolean prefsIsNotifyOnTrackChange;
    private boolean prefsIsInterpolation;
    //private boolean prefsIsAutomaticExit;
    private boolean prefsIsSeekbarDisabled;
    static boolean prefsIsScanFiles;
    static boolean prefsIsZipFlat;
    private int prefsStereoSeparation;
    private int prefsShakeLevel;
    private int prefsAutomaticAction;
    private Editor edit;

    // Seekbar
    private boolean ymSeeking = false; // ugly quick hack
    private boolean isUserNotfied = false;
    private boolean isUserSeeking = false;
    private int seekBarLastPosition = 0;

    // self reference for media button event receiver, ToDo: improve
    static Modo myModo = null;

    // GUI Elements
    private SeekBar seekPlaytime;
    private TextView textFileInfo;
    private TextView textPlaytimeMinutes;
    private TextView textPlaytimeSeconds;
    private TextView textTrackLength;
    private TextView textTrack;
    private TextView textFileDetails;
    private TextView textSleeptimer;
    private TextView textPlaylistname;
    private ImageButton buttonPrevTrack;
    private ImageButton buttonNextTrack;
    private ImageButton buttonPauseResume;
    private View buttonFilebrowser; // can be Imagebutton or Button
    private MenuItem menu_shuffle;
    private MenuItem menu_loop;
    private MenuItem menu_add;

    // System Services & helpers
    private NotificationManager mNotificationManager;
    private AudioManager audioManager;
    private ComponentName cc;
    private SensorManager mSensorManager;
    private ShakeEventListener mSensorListener;


    // Sleep timer sounds
    private SoundPool soundPool = null;
    private int[] soundBeeps = { -1, -1};
    private static final int SOUND_INDEX_BEEP_LOW = 0;
    private static final int SOUND_INDEX_BEEP_HIGH = 1;

    // Background service spaning "Player" thread
    private ServicePlayer sp = null;
    private MyServiceConnection servicePlayerConnection = null;
    private Intent servicePlayerIntent;

    // Binding to native implementations
    private GmeDecoder gmeDecoder = new GmeDecoder();
    private MikModDecoder mikmodDecoder = new MikModDecoder();
    private SidDecoder sidDecoder = new SidDecoder();
    private YmDecoder ymDecoder = new YmDecoder();
    private AsapDecoder asapDecoder = new AsapDecoder();
    private NezplugDecoder nezplugDecoder = new NezplugDecoder();
    private RsnDecoder rsnDecoder = new RsnDecoder();
    private SpcDecoder spcDecoder = new SpcDecoder();
    private AytDecoder aytDecoder = new AytDecoder();
    //private XmpDecoder xmpDecoder = new XmpDecoder();

    // Used if load failure happened. Loads next file after 5 seconds timeout.
    private LoadNextFileAfterLoadError loadNextFile = null;
    private LoadFile loadFile = null;

    // Handler for GUI Updates
    Handler mHandler;

    // Restore / backup from cloud
    BackupManager backup;

    // Intents for notification
    final static String INTENT_NOTIFICATION_NEXT = "de.illogical.modo.next";
    final static String INTENT_NOTIFICATION_PREV = "de.illogical.modo.prev";
    final static String INTENT_NOTIFICATION_PLAY = "de.illogical.modo.play";
    final static String INTENT_NOTIFICATION_PAUSE = "de.illogical.modo.pause";
    final static String INTENT_NOTIFICATION_STOP = "de.illogical.modo.stop";

    private PendingIntent intentNext;// = PendingIntent.getBroadcast(this, 2, new Intent("de.illogical.modo.next"), PendingIntent.FLAG_CANCEL_CURRENT);
    private PendingIntent intentPrev;// = PendingIntent.getBroadcast(this, 2, new Intent("de.illogical.modo.prev"), PendingIntent.FLAG_CANCEL_CURRENT);
    private PendingIntent intentPause;// = PendingIntent.getBroadcast(this, 2, new Intent("de.illogical.modo.play"), PendingIntent.FLAG_CANCEL_CURRENT);
    private PendingIntent intentPlay;// = PendingIntent.getBroadcast(this, 2, new Intent("de.illogical.modo.pause"), PendingIntent.FLAG_CANCEL_CURRENT);
    private PendingIntent intentStop;

    private NotificationReceiver notificationReceiver = null;

    // Listener when a new file is loaded to highlight current playlist / track
    private ArrayList<OnNextPlaylistEntryListener> listeners = new ArrayList<Modo.OnNextPlaylistEntryListener>(20);

    interface OnNextPlaylistEntryListener {
        public void nextEntry(String playlistname, Playlist.Entry entry);
    }


    // Service listener, restores state after reconnect
    class MyServiceConnection implements ServiceConnection
    {
        @SuppressWarnings("deprecation")
        public void onServiceConnected(ComponentName name, IBinder iBinder) {

            sp = ((ServicePlayer.LocalBinder)iBinder).getService();
            sp.startPlayerThread();
            ServicePlayer.p.setHandler(mHandler);

            if (!sp.isForeground()) {
                Notification n = new Notification(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? R.drawable.modo_white : R.drawable.modo, getText(R.string.enjoy_some_retro_music), System.currentTimeMillis());
                PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 2, new Intent(Modo.this, Modo.class), PendingIntent.FLAG_CANCEL_CURRENT);//Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
                n.setLatestEventInfo(getApplicationContext(), getText(R.string.service_started), getText(R.string.enjoy_some_retro_music), contentIntent);
                sp.startForeground(1, n);
                sp.setIsForeground(true);
            }

            //Log.e(TAG, "Service connected ");

            if (decoder != null && path != null) {
                synchronized (sync) {
                    // restore state
                    forceUpdate = true;

                    if (decoder.getStatus() != Decoder.STATUS_FILE_ERROR)
                    {
                        //android.util.Log.e(TAG, "Service connected => trying to restore song");

                        isLoadingOkay = true;

                        clearPlaytimeDisplay();

                        // compute status
                        ServicePlayer.p.setDecoder(decoder);
                        playtimeStamp = decoder.playtime();
                        tracks = decoder.tracks();
                        saveTrackInfo();

                        // update GUI
                        // restore songlength and track display
                        updatePlaytimeDisplayAfterTrackChange();

                        // restore progressbar
                        seekPlaytime.setMax(decoder.isTrackerFormat() ? decoder.tracks() - 1 : 100);
                        if (decoder.isTrackerFormat())
                            seekPlaytime.setProgress((int)track); // mikmod
                        else
                            seekPlaytime.setProgress((int)(100 * decoder.playtime() / trackLength));

                        // restore filedetails
                        textFileDetails.setText(isLoadingOkay ? decoder.getTrackInfo() : "<no info>");
                        textFileInfo.setCompoundDrawablesWithIntrinsicBounds(FileBrowser.getDrawableResourceForFile(path), 0, 0, 0);
                        textFileInfo.setText(fileInfo);

                        // buttons and app title / sleep timer
                        updateStatusButtons();
                        updateTitle();
                        updatePlaylistname();
                        updateSoundBoost();

                        // playtime update! dirty!
                        int seconds = (int)(decoder.playtime() / 1000);
                        int min = seconds / 60;
                        int sec = seconds - (min * 60);

                        if (min > 99) {
                            min = 99;
                            sec = 00;
                        }

                        textPlaytimeMinutes.setText(TimeToString[min]);
                        textPlaytimeSeconds.setText(TimeToString[sec]);

                        // player was paused before rotate?
                        if (!isPause)
                            ServicePlayer.p.resumePlayer();
                        else
                            ServicePlayer.p.pausePlayer();
                    } else
                    {
                        //android.util.Log.e(TAG, "Service connected => trying standard playlist222");
                        if (playlist != null && playlist.size() > 0)
                            prepareNextFile(0);
                    }
                }
            } else {
                //android.util.Log.e(TAG, "Service connected => trying standard playlist");
                if (playlist != null && playlist.size() > 0)
                    prepareNextFile(0);
            }
        }

        public void onServiceDisconnected(ComponentName name) {}
    }

    // SQL save in background on app exit
    private SavePlaylist saveplaylist = null;
    class SavePlaylist extends AsyncTask<Integer, Integer, Integer> {
        protected Integer doInBackground(Integer... params) {
            PlaylistManager.saveToSQL(getApplicationContext());
            return 1;
        }
    }

    // If a file fails to load, wait 5sec to load next file
    class LoadNextFileAfterLoadError extends AsyncTask<Integer, Integer, Integer>
    {
        protected void onPostExecute(Integer result) {
            if (!isLoadingOkay) { // maybe in the meantime the user loaded something valid
                playlist.advancePlayPosition();
                Modo.this.prepareNextFile(0);
            }
        }

        protected Integer doInBackground(Integer... params) {
            try { Thread.sleep(5000); } catch (InterruptedException e ) {}
            return 1;
        }
    }

    // Loads next file in background and does GUI updates
    class LoadFile extends AsyncTask<Integer, Integer, Integer>	{

        Playlist.Entry pe;

        protected Integer doInBackground(Integer... params) {

            synchronized(playlistSync) {
                try { Thread.sleep(200); } catch (InterruptedException e ) {}
                playing = pe = playlist.get();
                if (pe == null)
                    path = new File("Playlist empty?");
                else if (pe.zipEntry != null)
                    path = new ModoFile(pe.zipEntry, new File(pe.path), false);
                else
                    path = new File(pe.path);
            }

            // Check if we loaded all files in the playlist at least once
            if (prefsAutomaticAction == ACTION_LOOP)
                playlist.resetPlayedAll();

            synchronized(sync) {

                resetDecoders();
                decoder = getDecoderForFile(path);

                if (path instanceof ModoFile) {
                    int uncompressedSize = decoder.loadFromZip(((ModoFile) path).getSrc().getAbsolutePath(), ((ModoFile) path).getZipEntry());
                    ((ModoFile)path).setLength(uncompressedSize);
                    isLoadingOkay = uncompressedSize > 0;

                } else {
                    isLoadingOkay = decoder.loadFile(path.getAbsolutePath()) > 0;
                }

                if (isLoadingOkay && pe.start >= 0)
                    decoder.setPlaylistTrack(pe.start);

                playtimeStamp = 0;
                track = 0;
                tracks = isLoadingOkay ? decoder.tracks() : 0;
            }

            return isLoadingOkay ? 1 : 0;
        }

        protected void onPostExecute(Integer result) {
            clearPlaytimeDisplay();

            trackSelection(0);

            updateTitle();
            updateStatusButtons();

            ServicePlayer.p.setDecoder(decoder);

            seekPlaytime.setProgress(0);
            seekPlaytime.setMax(decoder.isTrackerFormat() ? decoder.tracks() - 1 : 100);

            if (isLoadingOkay) {
                textFileInfo.setText(String.format("%s\n%d %s - %s",
                        path.getName(),
                        path.length() <= 1024 ? path.length() : path.length() / 1024,
                        path.length() <= 1024 ? "bytes" : "kb",
                        FileBrowser.getDescription(path)));
                textFileDetails.setText(decoder.getTrackInfo());
                playTimeFirstClickOnTrackZero = 0;
                sendNotification(R.string.now_playing);
                updateSoundBoost();
                if (!isPause)
                    ServicePlayer.p.resumePlayer();
            } else {
                textFileInfo.setText(String.format("%s\n%s", path.getName(), getString(R.string.loading_failed)));
                textFileDetails.setText("<no info>");
                sendNotification(R.string.loading_failed);
                loadNextFile = new LoadNextFileAfterLoadError();
                loadNextFile.execute();
            }

            fileInfo = textFileInfo.getText().toString();
            textFileInfo.setCompoundDrawablesWithIntrinsicBounds(FileBrowser.getDrawableResourceForFile(path), 0, 0, 0);

            if (pe != null)
                fireNextPlaylistEntry(pe);
        }

        protected void onCancelled() {
            updateStatusButtons();
        }
    }

    /** Called when the activity is first created. */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        backup = new BackupManager(getApplicationContext());
        
        edit = PreferenceManager.getDefaultSharedPreferences(this).edit();
        
        setTitle("");
        getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME);
        getSupportActionBar().setIcon(R.drawable.modo);

        PlaylistManager.loadFromSQL(getApplicationContext());

        mHandler = new Handler(this);
        
        setContentView(R.layout.player2);

        // GUI
        seekPlaytime = (SeekBar)findViewById(R.id.seekPlaytime);
        seekPlaytime.setOnSeekBarChangeListener(this);
        seekPlaytime.setEnabled(false);
        seekPlaytime.setIndeterminate(false);
        
        textSleeptimer = (TextView)findViewById(R.id.textSleeptimer);
        textPlaylistname = (TextView)findViewById(R.id.textPlaylistname);
        textFileInfo = (TextView)findViewById(R.id.textFileInfo);
        textTrackLength = (TextView)findViewById(R.id.mytextTrackLength);
        textPlaytimeMinutes = (TextView)findViewById(R.id.textPlaytimeMinutes);
        textPlaytimeSeconds = (TextView)findViewById(R.id.textPlaytimeSeconds);
        textTrack = (TextView)findViewById(R.id.textTrack);
        textFileDetails = (TextView)findViewById(R.id.textFileDetails);
        buttonPrevTrack = (ImageButton)findViewById(R.id.buttonPrevTrack);
        buttonNextTrack = (ImageButton)findViewById(R.id.buttonNextTrack);
        buttonPauseResume = (ImageButton)findViewById(R.id.buttonPauseResume);
        buttonFilebrowser = (View)findViewById(R.id.buttonFilebrowser);
        
        Typeface font = Typeface.createFromAsset(getAssets(), "digital-7 (mono).ttf");
        textTrackLength.setTypeface(font);
        textTrackLength.setTextScaleX(2f);
        textPlaytimeMinutes.setTypeface(font);
        textPlaytimeMinutes.setTextScaleX(2f);
        textPlaytimeSeconds.setTypeface(font);
        textPlaytimeSeconds.setTextScaleX(2f);
        textTrack.setTypeface(font);
        textTrack.setTextScaleX(2f);
        ((TextView)findViewById(R.id.textPlaytimeColon)).setTypeface(font);
        ((TextView)findViewById(R.id.textPlaytimeColon)).setTextScaleX(2f);
                
        textFileDetails.setScrollContainer(true);
        buttonPauseResume.setEnabled(false);
        buttonPrevTrack.setEnabled(false);
        buttonNextTrack.setEnabled(false);
        
        buttonNextTrack.setOnLongClickListener(this);
        buttonPrevTrack.setOnLongClickListener(this);      

        // System services
        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);	
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (isRealMachine) {
            mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
            mSensorListener = new ShakeEventListener();
            mSensorListener.setOnShakeListener(this);
        }

        cc = new ComponentName(getPackageName(), ModoReceiver.class.getName());

        soundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);
        soundBeeps[SOUND_INDEX_BEEP_LOW] = soundPool.load(this, R.raw.beep1000_low, 1);
        soundBeeps[SOUND_INDEX_BEEP_HIGH] = soundPool.load(this, R.raw.beep1000_high, 1);

        // Background service spawn player thread
        servicePlayerConnection = new MyServiceConnection();
        servicePlayerIntent = new Intent();
        servicePlayerIntent.setClassName(this, "de.illogical.modo.ServicePlayer");
        
        // this ensures it is sticky
        startService(servicePlayerIntent);
        
        // this ensures we have a direct connection to the service
        bindService(servicePlayerIntent, servicePlayerConnection, BIND_AUTO_CREATE);
        
        //Log.e(TAG, "OnCreate - executing bind service");        

        // Dirty hack for Media Buttons receiver, improve??
        myModo = this;
        
        //
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefsIsShuffle = prefs.getBoolean("shuffle", false);
        prefsIsLoop = prefs.getBoolean("loop", false);
        prefsIs99 = prefs.getBoolean("loop99", false);
        prefsIsMediabuttons = prefs.getBoolean("mediabuttons", false);

        //
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(INTENT_NOTIFICATION_NEXT);
            filter.addAction(INTENT_NOTIFICATION_PREV);
            filter.addAction(INTENT_NOTIFICATION_PLAY);
            filter.addAction(INTENT_NOTIFICATION_PAUSE);
            filter.addAction(INTENT_NOTIFICATION_STOP);
            notificationReceiver = new NotificationReceiver();
            registerReceiver(notificationReceiver, filter);

            intentNext = PendingIntent.getBroadcast(this, 2, new Intent(INTENT_NOTIFICATION_NEXT), PendingIntent.FLAG_UPDATE_CURRENT);
            intentPrev = PendingIntent.getBroadcast(this, 2, new Intent(INTENT_NOTIFICATION_PREV), PendingIntent.FLAG_UPDATE_CURRENT);
            intentPause = PendingIntent.getBroadcast(this, 2, new Intent(INTENT_NOTIFICATION_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT);
            intentPlay = PendingIntent.getBroadcast(this, 2, new Intent(INTENT_NOTIFICATION_PLAY), PendingIntent.FLAG_UPDATE_CURRENT);
            intentStop = PendingIntent.getBroadcast(this, 2, new Intent(INTENT_NOTIFICATION_STOP), PendingIntent.FLAG_UPDATE_CURRENT);
        }
    }
        
    protected void onStart() {
        super.onStart();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefsIsNotifyOnTrackChange = true; //prefs.getBoolean("notification", true);
        prefsDefaultTrackLength = Integer.valueOf(prefs.getString("stdplaytime", "180000"));
        prefSilence = Integer.valueOf(prefs.getString("silence", "4"));
        prefsIsInterpolation = prefs.getBoolean("interpolation", true);
        prefsStereoSeparation = prefs.getInt("stereo", 128);
        prefsShakeLevel = Integer.valueOf(prefs.getString("shakelevel", "0"));
        seekPlaytime.getRootView().setBackgroundColor(prefs.getInt("overlay_color", 0xaa000000));
        prefsIsMediabuttons = prefs.getBoolean("mediabuttons", false);
        prefsIsStereoAY = prefs.getBoolean("aystereo", true);
        prefsAutomaticAction = Integer.valueOf(prefs.getString("automaticaction", "0"));
        prefsIsSeekbarDisabled = prefs.getBoolean("seekbar_disabled", false);
        prefsIsScanFiles = prefs.getBoolean("scanfiles", false);
        prefsIsZipFlat = prefs.getBoolean("flatzip", false);
        prefsFormatSoundboost[BOOST_MODULES] = Integer.valueOf(prefs.getString("soundboost_modules", "0"));
        prefsFormatSoundboost[BOOST_NSF] = Integer.valueOf(prefs.getString("soundboost_nsf", "0"));
        prefsFormatSoundboost[BOOST_SPC] = Integer.valueOf(prefs.getString("soundboost_spc", "0"));
        prefsFormatSoundboost[BOOST_VGM] = Integer.valueOf(prefs.getString("soundboost_vgm", "0"));
        prefsFormatSoundboost[BOOST_AY] = Integer.valueOf(prefs.getString("soundboost_ay", "0"));
        prefsFormatSoundboost[BOOST_SAP] = Integer.valueOf(prefs.getString("soundboost_sap", "0"));
        prefsFormatSoundboost[BOOST_HES] = Integer.valueOf(prefs.getString("soundboost_hes", "0"));
        prefsFormatSoundboost[BOOST_KSS] = Integer.valueOf(prefs.getString("soundboost_kss", "0"));
        prefsFormatSoundboost[BOOST_YM] = Integer.valueOf(prefs.getString("soundboost_ym", "0"));
        prefsFormatSoundboost[BOOST_GYM] = Integer.valueOf(prefs.getString("soundboost_gym", "0"));
        prefsFormatSoundboost[BOOST_SID] = Integer.valueOf(prefs.getString("soundboost_sid", "0"));
        prefsFormatSoundboost[BOOST_GBS] = Integer.valueOf(prefs.getString("soundboost_gbs", "0"));
        
        if (prefs.getBoolean("showWelcome", true)) {
        //if (true) {
            edit.putBoolean("showWelcome", false).commit();
            showWelcomeMessage();
        }
        
        updateShuffleLoopText();
        updateDecoders();
        updateSoundBoost();
        updateStatusButtons();
        updatePlaylistname();
        
        synchronized (sync) {
            mikmodDecoder.isInterpolationMixingEnabled(prefsIsInterpolation);
            mikmodDecoder.setStereoSeparation(prefsStereoSeparation);
            //xmpDecoder.setStereoSeparation(prefsStereoSeparation);
            gmeDecoder.setStereoSeparation(prefsStereoSeparation);
            spcDecoder.setStereoSeparation(prefsStereoSeparation);
            spcDecoder.setStereoAY(prefsIsStereoAY ? 1 : 0);
            aytDecoder.setStereoSeparation(prefsStereoSeparation);
        }
        
        if (isRealMachine)
        {
            mSensorManager.unregisterListener(mSensorListener);
            if (prefsShakeLevel > 0) {
                switch (prefsShakeLevel) {
                    case 1: mSensorListener.setForce(10, 2, 300);
                            break;
                    case 2: mSensorListener.setForce(30, 2, 300);
                            break;
                    case 3: mSensorListener.setForce(60, 2, 300);
                            break;
                    default: mSensorListener.setForce(10, 2, 300);
                             break;

                }
                mSensorManager.registerListener(mSensorListener, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI);

            }
        }
        
        //Log.e(TAG, "OnStart");
    }

    protected void onResume() {
        super.onResume();
        buttonFilebrowser.setEnabled(true);
        audioManager.registerMediaButtonEventReceiver(cc);
    }
    
    public void onBackPressed() {
        // only terminate if not playing music
        if (buttonPauseResume.isEnabled() && isPause == false) { // isplaying!
            moveTaskToBack(true);
            Toast.makeText(getApplicationContext(), R.string.info_service_continue, Toast.LENGTH_LONG).show();
        } else {
            super.onBackPressed();
            finish();
            //Log.v(TAG, "OnBackPressed - executing service SHUTDOWN");
        }
    }
    
    protected void onDestroy() {
//        android.util.Log.e(TAG, "OnDestroy - executing service unbind");

        backup.dataChanged();

        if (isRealMachine)
            mSensorManager.unregisterListener(mSensorListener);

        if (sp != null)
            sp.sendNoMoreGUIUpdates();

        if (loadNextFile != null)
            loadNextFile.cancel(true);
        
        if (loadFile != null)
            loadFile.cancel(true);

        if (notificationReceiver != null)
            unregisterReceiver(notificationReceiver);

        unbindService(servicePlayerConnection);

        mNotificationManager.cancelAll();

        audioManager.unregisterMediaButtonEventReceiver(cc);
        soundPool.release();


        edit.putBoolean("loop", prefsIsLoop);
        edit.putBoolean("loop99", prefsIs99);
        edit.putBoolean("shuffle", prefsIsShuffle);
        edit.commit();

        super.onDestroy();
    }
    
    public void finish() {
        Toast.makeText(getApplicationContext(), R.string.info_service_terminated, Toast.LENGTH_LONG).show();
        if (sp != null)
            sp.stopSelf();
        if (loadFile != null)
            loadFile.cancel(false);
        if (playlist != null)
            playlist.resetPlayedAll();
        // Save to SQL only once and only on real app exit
        if (saveplaylist == null) {
            saveplaylist = new SavePlaylist();
            saveplaylist.execute(1);
        }
        super.finish();
    }
    
    void copyExamplePlaylistFromAssetToCache() {
        try {
            byte[] buffer = new byte[64000];
            InputStream in = getAssets().open(PlaylistManager.EXAMPLE_DIRECTORY + PlaylistManager.EXAMPLE_PLAYLIST_ZIP);
            FileOutputStream out = new FileOutputStream(getCacheDir().getAbsolutePath() + File.separatorChar + PlaylistManager.EXAMPLE_PLAYLIST_ZIP);
            int readCount = buffer.length;
            while (readCount == buffer.length) {
                readCount = in.read(buffer);
                out.write(buffer, 0, readCount);
            }
            in.close();
            out.close();
            buffer = null;
        } catch (IOException e) {
            android.util.Log.e(TAG, e.toString());
        }
    }
    
    void showWelcomeMessage() {
        AlertDialog.Builder ab = new AlertDialog.Builder(this);
        ab.setCancelable(false);
        ab.setMessage(R.string.welcome_msg);
        ab.setPositiveButton(android.R.string.yes, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                copyExamplePlaylistFromAssetToCache();
                PlaylistManager.createExample(getResources().getString(R.string.examples_playlist_name), getCacheDir());
                startActivity(new Intent(getApplicationContext(), ModoPlaylists.class));
            }
        });
        ab.setNegativeButton(android.R.string.no, null);
        ab.create().show();
    }
    
    void updateShuffleLoopText() {    	
        if (menu_loop != null) {
            menu_loop.setTitle(prefsIs99 ? R.string.menu_loop_99 : (prefsIsLoop ? R.string.menu_loop_is_on : R.string.menu_loop_is_off));
            menu_loop.setIcon(prefsIs99 ? R.drawable.ic_action_repeat_light_99 : (prefsIsLoop ? R.drawable.ic_action_repeat_light : R.drawable.ic_action_repeat_dark));
        }

        if (menu_shuffle != null) {
            menu_shuffle.setTitle(prefsIsShuffle ? R.string.menu_shuffle_is_on : R.string.menu_shuffle_is_off);
            menu_shuffle.setIcon(prefsIsShuffle ? R.drawable.ic_action_shuffle_light : R.drawable.ic_action_shuffle_dark);
        }

    }
    
    void updatePlaylistname() {
        if (playlistname == null) {
            textPlaylistname.setVisibility(View.GONE);
        } else {
            textPlaylistname.setText(playlistname);
            textPlaylistname.setVisibility(View.VISIBLE);
        }
    }
    
    void updateTitle() {
        if (sleepTimer > 0) {
            textSleeptimer.setText("zZz " + (sleepTimer / 1000 / 60 + 1)  + "min");
            textSleeptimer.setVisibility(View.VISIBLE);
        } else
            textSleeptimer.setVisibility(View.GONE);
    }

    void updatePlaytimeDisplayAfterTrackChange() {   	
        if (isLoadingOkay) {
            textTrackLength.setText(trackLength > 0 ? " / " + strTrackLength : "");
            textTrack.setText((track + 1) + " / " + tracks);
        }
    }
    
    void clearPlaytimeDisplay() {
        textPlaytimeMinutes.setText("00");
        textPlaytimeSeconds.setText("00");
        textTrackLength.setText(" / 00:00");
        textTrack.setText("0 / 0");
    }
    
    void updateStatusButtons() {    	
        if (isFastForward || isPause)
            buttonPauseResume.setImageResource(android.R.drawable.ic_media_play);
        else
            buttonPauseResume.setImageResource(android.R.drawable.ic_media_pause);

        buttonPauseResume.setEnabled(isLoadingOkay);
        buttonNextTrack.setEnabled(isLoadingOkay && !isPause);
        buttonPrevTrack.setEnabled(isLoadingOkay && !isPause);
        buttonPrevTrack.setPressed(false);
        buttonNextTrack.setPressed(false);
        buttonFilebrowser.setEnabled(true);

        if (menu_add != null)
            menu_add.setEnabled(isLoadingOkay && PlaylistManager.hasPlaylists() && playlist != null && playlist.size() > 0);

        seekPlaytime.setEnabled(isLoadingOkay && !isPause);
    }
    
    void sendNotification(int resid) {
        sendNotification(resid, -1);
    }

    void sendNotification(int resid, int progress) {
        if (prefsIsNotifyOnTrackChange) {
            if (resid == R.string.now_playing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Try for Android 5
                Notification.Builder builder = new Notification.Builder(getApplicationContext());
                builder.setVisibility(Notification.VISIBILITY_PUBLIC);
                builder.setSmallIcon(R.drawable.modo_white);
                //builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.modo_white_2));
                builder.setContentTitle(path.getName());
                builder.setContentText(decoder instanceof MikModDecoder ? "" : track + 1 + " / " + tracks);
                builder.setSubText(FileBrowser.getDescription(path));
                builder.addAction(android.R.drawable.ic_media_previous, "", intentPrev);
                if (isFastForward || isPause)
                    builder.addAction(android.R.drawable.ic_media_play, "", intentPlay);
                else
                    builder.addAction(android.R.drawable.ic_media_pause, "", intentPause);
                builder.addAction(android.R.drawable.ic_media_next, "", intentNext);
                builder.addAction(android.R.drawable.ic_delete, "", intentStop);
                builder.setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2));
                PendingIntent contentIntent = PendingIntent.getActivity(this, 2, new Intent(this, Modo.class), PendingIntent.FLAG_CANCEL_CURRENT);
                builder.setContentIntent(contentIntent);
                builder.setShowWhen(false);
                Notification n = builder.build();
                mNotificationManager.notify(1, n);
            } else {
                Notification n = new Notification(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? R.drawable.modo_white : R.drawable.modo, path.getName(), System.currentTimeMillis());
                PendingIntent contentIntent = PendingIntent.getActivity(this, 2, new Intent(this, Modo.class), PendingIntent.FLAG_CANCEL_CURRENT);
                n.setLatestEventInfo(this, path.getName(), getText(resid), contentIntent);
                mNotificationManager.notify(1, n);
            }
        }
    }


    // only call if service is bound: sp != null, startTrack currently not used
    protected void prepareNextFile(int startTrack) {

        ServicePlayer.p.pausePlayer();

        textFileInfo.setText(R.string.please_wait);
        textFileDetails.setText("");
        buttonNextTrack.setEnabled(false);
        buttonPrevTrack.setEnabled(false);
        buttonPrevTrack.setPressed(false);
        buttonNextTrack.setPressed(false);
        buttonPauseResume.setEnabled(false);
        seekPlaytime.setPressed(false);
        seekPlaytime.clearFocus();
        seekPlaytime.setSelected(false);

        // Always cancel first
        if (loadFile != null)
            loadFile.cancel(true);

        // Check for automatic exit of the player
        if (playlist.allPlayed() && prefsAutomaticAction == ACTION_EXIT) {
            finish();
            return;
        }

        // Check for automatic stop of the player
        if (playlist.allPlayed() && prefsAutomaticAction == ACTION_PAUSE) {
            playlist.resetPlayedAll();
            isPause = true;
        }

        loadFile = new LoadFile();
        loadFile.execute();
    }
    
    // directions should be only +1 or -1
    protected void trackSelection(int direction) {

        synchronized (sync) {

            if (!decoder.isTrackerFormat())
                playtimeStamp = 0;

            forceUpdate = true;
            isUserNotfied = false;
            isFastForward = false;

            track = track + direction;
            if (track >= tracks)
                track = tracks - 1;
            if (track < 0)
                track = 0;

            if (isLoadingOkay) {
                decoder.setTrack(track);
                if (decoder instanceof GmeDecoder || decoder instanceof RsnDecoder || decoder instanceof SpcDecoder || decoder instanceof AytDecoder)
                    textFileDetails.setText(decoder.getTrackInfo());
                ServicePlayer.p.noFastForward();
            }

            playTimeFirstClickOnTrackZero = decoder.playtime();

            saveTrackInfo();
            updateStatusButtons();
            updatePlaytimeDisplayAfterTrackChange();
        }
    }
    
    public boolean handleMessage(Message msg) {

        if (!isLoadingOkay)
            return true;

        if(msg.what == MESSAGE_AUDIOFOCUS_CHANGE) {
            onAudioFocusChange(msg.arg1);
            sendNotification(R.string.now_playing); // update notification controls if another app returns or get the audio focus
            return true;
        }

        if (msg.what == MESSAGE_SHAKE) {
            soundPool.play(soundBeeps[SOUND_INDEX_BEEP_LOW], 1f, 1f, 0, 0, 1f);
            if (buttonNextTrack.isEnabled())
                buttonNextTrack.performLongClick();
            return true;
        }

        int playtime = msg.what;
        int currentTrack = msg.arg1;
        int status = msg.arg2;

        if (decoder.isTrackerFormat()) {
            track = currentTrack; // keep player logic together
            if (currentTrack >= tracks || status == Decoder.STATUS_EOF) { // EOF / looped enough?
                if (prefsIsLoop) {
                    prepareNextFile(0);
                } else {
                    playlist.advancePlayPosition();
                    prepareNextFile(0);
                }
                return true;
            }
        } else if (status == Decoder.STATUS_SILENCE || (trackLength > 0 && playtime > trackLength)) {
            if (prefsIsLoop) {
                trackSelection(0);
            } else {
                if ((track + 1) >= tracks) {
                    playlist.advancePlayPosition();
                    prepareNextFile(0);
                } else {
                    trackSelection(+1);
                    sendNotification(R.string.now_playing);
                }
            }
        }

        // if this is first time after track change
        int delta = playtime - playtimeStamp;

        // ugyl hack
        if (ymSeeking) {
            delta = 0;
        }

        // after trackChange, bad things can happen
        if (delta > 2000) {
            playtimeStamp = playtime;
            return true;
        }

        if (delta >= 100 || forceUpdate) {
            playtimeStamp = playtime;
            forceUpdate = false;
            ymSeeking = false;

            updateSleepTimer(delta);

            int seconds = (int)(playtime / 1000);
            int min = seconds / 60;
            int sec = seconds - (min * 60);

            if (min > 99) {
                min = 99;
                sec = 00;
            }

            textPlaytimeMinutes.setText(TimeToString[min]);
            textPlaytimeSeconds.setText(TimeToString[sec]);

            if (!isUserSeeking)	{
                if (decoder.isTrackerFormat()) {
                    seekPlaytime.setProgress((int) currentTrack); // mikmod
                } else {
                    seekPlaytime.setProgress((int) (100 * playtime / trackLength));
                }
            }

            if (decoder.isTrackerFormat() && !skipTrackUpdate)
                textTrack.setText((currentTrack + 1) + " / " + tracks);

            skipTrackUpdate = false;
        }
        return true;
    }
    
    void updateSleepTimer(int deltaTime)
    {
        if (deltaTime < 0 || deltaTime > 1200)
            return;
        if (sleepTimer > 0) {
            sleepTimer -= (isFastForward ? deltaTime / 2 : deltaTime);
            if (sleepTimer <= 0) {
                sleepTimer = 0;
                finish();
            } else if (sleepTimer < 11000 && deltaTime >= 1000) {
                soundPool.play(soundBeeps[SOUND_INDEX_BEEP_HIGH], 1f, 1f, 0, 1, 2f);
            } else if (sleepTimer < 31000 && deltaTime >= 1000) {
                soundPool.play(soundBeeps[SOUND_INDEX_BEEP_LOW], 1f, 1f, 0, 0, 1f);
            }
            updateTitle();
        }
    }
    
    public void onClick_ButtonFilebrowser(View v) {
        buttonFilebrowser.setEnabled(false);
        buttonNextTrack.setEnabled(false);
        buttonPrevTrack.setEnabled(false);
        buttonPauseResume.setEnabled(false);
        seekPlaytime.setEnabled(false);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(Modo.this);
        Intent i = new Intent(this, FileBrowser.class);

        // Show standard music directory if a sdcard is available
        String sdcardStatus = Environment.getExternalStorageState();
        File userMusicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        File userDirectory = Environment.getExternalStorageDirectory();
        File rootDirectory = Environment.getRootDirectory();

        i.setAction(rootDirectory.getAbsolutePath());
        if (sdcardStatus.equals(Environment.MEDIA_MOUNTED) || sdcardStatus.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
            if (userMusicDirectory.exists()) {
                i.setAction(userMusicDirectory.getAbsolutePath());
            } else if (userDirectory.exists()) {
                i.setAction(userDirectory.getAbsolutePath());
            }
        }

        // Overwrite default behaviour if user has stored a valid last directory
        if (prefs.contains("last_directory")) {
            File lastUserDirectory = new File(prefs.getString("last_directory", ""));
            if (lastUserDirectory.exists()) {
                i.setAction(lastUserDirectory.getAbsolutePath());
            }
        }

        startActivityForResult(i, 0);
    }
    
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            // Assign correct playlist and starting position
            synchronized (Modo.playlistSync) {
                playlist.syncPlayPositionForShuffle();
                playlist.setShuffleMode(prefsIsShuffle);
            }

            playlistname = null;
            updatePlaylistname();

            // Load selected file, assigned by playlist
            if (sp != null)
                prepareNextFile(0);

            // save last user directory
            edit.putString("last_directory", new File(data.getAction()).getParentFile().getAbsolutePath());
            edit.remove("zipdir");
            edit.remove("zipfile");
            // Are we at NOT at the root of the zipfile, then store all information necessary to resume later
            if (withinZipfile != null) {
                //ModoFile f = (ModoFile)playList.get(0);
                ModoFile f = withinZipfile;
                // If isWithinZipFile is true, we are one level deep into the zip, so this test should not be necessary,
                // but just to be sure
                if (f.getZipEntry().lastIndexOf('/' ) > -1) {
                    edit.putString("zipdir", f.getZipEntry().substring(0, f.getZipEntry().lastIndexOf('/' )) + "/");
                    edit.putString("zipfile", f.getSrc().getAbsolutePath());
                    //Log.e(TAG, "Storing information about ZipFile for resume.");
                } else {
                    //Log.e(TAG, "In Zip, but at the root, so no need.");
                }
            } else {
                //Log.e(TAG, "No Zip, so do not store zip information.");
            }
            edit.commit();
        } else {
            updateStatusButtons();
        }
    } 
    
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                isPause = pauseStatusOnAudioFocusLost;
                isFastForward = false;
                if (!isPause)
                    ServicePlayer.p.resumePlayer();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                pauseStatusOnAudioFocusLost = isPause;
                isFastForward = false;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS:
                pauseStatusOnAudioFocusLost = isPause;
                isPause = true;
                isFastForward = false;
                ServicePlayer.p.pausePlayer();
                break;
        }
        updateStatusButtons();
    }

    public void MediaPause() {
        isFastForward = false;
        isPause = false;
        onClick_ButtonPauseResume(null);
    }
    public void MediaPlay() {
        isFastForward = false;
        isPause = true;
        onClick_ButtonPauseResume(null);
    }

    public void onClick_ButtonPauseResume(View v) {
        synchronized (sync) {
            if (isFastForward) {
                isFastForward = false;
                isPause = false;
                ServicePlayer.p.resumePlayer();
            } else {
                isPause = !isPause;
                if (isPause)
                    ServicePlayer.p.pausePlayer();
                else
                    ServicePlayer.p.resumePlayer();
            }
            updateStatusButtons();
            sendNotification(R.string.now_playing);
        }
    }
   
    public boolean onLongClick(View v) {
        synchronized (sync) {
            if (v == buttonPrevTrack)
                playlist.reducePlayPosition();
            if (v == buttonNextTrack)
                playlist.advancePlayPosition();
            prepareNextFile(0);
        }
        return true;
    }
       
    public void onClick_ButtonPrevTrack(View v) {
        synchronized (sync) {
            //if (isPause)
            //	return;
            if (!isLoadingOkay)
                return;
            int diff = decoder.playtime() - playTimeFirstClickOnTrackZero;
            if (track == 0 && diff < 3000) {
                playlist.reducePlayPosition();
                prepareNextFile(0);
            } else {
                trackSelection(-1);
                skipTrackUpdate = true;
                sendNotification(R.string.now_playing);
            }
        }
    }

    public void onClick_ButtonNextTrack(View v) {
        synchronized (sync) {
            //if (isPause)
            //	return;
            if (!isLoadingOkay)
                return;
            if ((track + 1) >= tracks) {
                playlist.advancePlayPosition();
                prepareNextFile(0);
            } else {
                trackSelection(+1);
                skipTrackUpdate = true;
                sendNotification(R.string.now_playing);
            }
        }
    }
    
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.player, menu);
        return true;
    }
    
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuItem menu_5min = menu.findItem(R.id.menu_sleeptimer_plus_5min);
        MenuItem menu_reset = menu.findItem(R.id.menu_stop_sleeptimer);
        menu_shuffle = menu.findItem(R.id.menu_shuffle);
        menu_loop = menu.findItem(R.id.menu_loop);
        menu_add = menu.findItem(R.id.menu_add_tune);

        menu_add.setEnabled(isLoadingOkay && PlaylistManager.hasPlaylists());
        updateShuffleLoopText();

        //menu_5min.setEnabled(isLoadingOkay);
        //menu_reset.setEnabled(sleepTimer > 0);
        menu_5min.setEnabled(true);
        menu_reset.setEnabled(true);

        return true;
    }

    protected void showAbout() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(R.drawable.modo);
        b.setCancelable(true);
        b.setMessage(R.string.dialog_about);
        b.setPositiveButton(R.string.okay, new DialogInterface.OnClickListener() {
           public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
           }});
        b.show();
    }

    static class DialogWhichPlaylist implements OnClickListener {
        private ArrayList<String> lists;
        private String path;
        private String zipEntry;
        private int track;

        DialogWhichPlaylist(ArrayList<String> playlistNames, String path, String zipEntry, int currentTrack) {
            lists = playlistNames;
            this.path = path;
            this.zipEntry = zipEntry;
            track = currentTrack;
        }
        public void onClick(DialogInterface dialog, int which) {
            if (which >= 0 && which < lists.size()) {
                synchronized(playlistSync) { PlaylistManager.addEntry(lists.get(which), path, zipEntry, track); }
            }
        }
    }
    void askWhichPlaylist() {
        ArrayList<String> lists = PlaylistManager.getNames();
        // copy current parameters to the listener in case music advances to the next track before user decided
        DialogWhichPlaylist callback;
        synchronized(playlistSync) {
             callback = new DialogWhichPlaylist(
                    lists, playlist.get().path,
                    playlist.get().zipEntry,
                    playlist.get().start >= 0 ? playlist.get().start : decoder.isTrackerFormat() ? -1 : track);
        }
        AlertDialog.Builder ab = new AlertDialog.Builder(this);
        ab.setAdapter(new ArrayAdapter<String>(this, R.layout.arrayadapter, lists), callback);
        ab.setCancelable(false);
        ab.setTitle(R.string.dialog_add_tune_title);
        ab.setNegativeButton(android.R.string.cancel, null);
        ab.create().show();
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        /*
            case R.id.menu_tablet:
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("market://details?id=de.illogical.modo.tablet"));
                startActivity(intent);
                break;
        */
            case R.id.menu_add_tune:
                if (isLoadingOkay && PlaylistManager.hasPlaylists() && playlist != null && playlist.size() > 0)
                    askWhichPlaylist();
                break;
            case R.id.menu_playlists:
                Intent i = new Intent(this, ModoPlaylists.class);
                startActivity(i);
                break;
            case R.id.menu_loop:
                if (prefsIs99) {
                    prefsIs99 = false;
                    prefsIsLoop = false;
                } else if (prefsIsLoop) {
                    prefsIsLoop = false;
                    prefsIs99 = true;
                } else {
                    prefsIsLoop = true;
                    prefsIs99 = false;
                }
                //prefsIsLoop = !prefsIsLoop;
                updateShuffleLoopText();
                if (prefsIs99) {
                    Toast.makeText(getApplicationContext(), R.string.info_loop_99, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getApplicationContext(), prefsIsLoop ? R.string.info_loop_on : R.string.info_loop_off, Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.menu_shuffle:
                prefsIsShuffle = !prefsIsShuffle;
                updateShuffleLoopText();
                Toast.makeText(getApplicationContext(), prefsIsShuffle ? R.string.info_shuffle_on : R.string.info_shuffle_off, Toast.LENGTH_SHORT).show();
                if (playlist != null) {
                    synchronized (Modo.playlistSync) {
                        if (prefsIsShuffle)
                            playlist.syncPlayPositionForShuffle();
                        playlist.setShuffleMode(prefsIsShuffle);
                    }
                }
                break;
            case R.id.menu_settings:
                startActivity(new Intent(this, Prefs.class));
                break;
            case R.id.menu_about:
                showAbout();
                break;
            case R.id.menu_help:
                startActivity(new Intent(this, Help.class));
                break;
            case R.id.menu_sleeptimer_plus_5min:
                sleepTimer += 60000 * 10 - 1000;
                if (sleepTimer > 60000 * 99)
                    sleepTimer = 60000 * 99;
                updateTitle();
                break;
            case R.id.menu_stop_sleeptimer:
                sleepTimer = 0;
                updateTitle();
                break;
            default:
                return false;
        }
        return true;
    }
    
    void saveTrackInfo()
    {
        trackLength = -1;
        strTrackLength = "";

        if (isLoadingOkay && decoder.supportTrackLength()) {
            synchronized (sync) {
                trackLength = decoder.trackLength();
                //Log.e(TAG, "Decoder: " + trackLength);
            }
            if (trackLength > 0) {
                strTrackLength = msToString(trackLength);
            } else {
                strTrackLength = msToString(prefsDefaultTrackLength);
                trackLength = prefsDefaultTrackLength;
            }

            // If we are in 99min, the above does not matter anymore
            if (prefsIs99) {
                trackLength = 99 * 60 * 1000;
                strTrackLength = msToString(trackLength);
            }
        }
    }
    
    String msToString(long milli) {
        long seconds = milli / 1000;
        long min = seconds / 60;
        long sec = seconds - (min * 60);
        return String.format(Locale.getDefault(), "%02d:%02d", min, sec);
    }

    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser) {
            if (prefsIsSeekbarDisabled)
                return;
            if (progress != seekBarLastPosition) {
                if (decoder.isTrackerFormat()) {
                    synchronized (sync) {
                        forceUpdate = true;
                        decoder.setTrack(progress);
                        track = progress;
                        playTimeFirstClickOnTrackZero = decoder.playtime();
                    }
                } else if (decoder instanceof YmDecoder) {
                    synchronized (sync) {
                        // progress bar range from 0% (0ms) to 100% (tracklength in ms)
                        if (((YmDecoder)decoder).seek((int)(trackLength * progress / 100))) {
                            forceUpdate = true;
                            ymSeeking = true;
                            playTimeFirstClickOnTrackZero = 0;
                        }
                    }
                } else {
                    if (!isUserNotfied)
                        Toast.makeText(getApplicationContext(), R.string.seek_warning_forward, Toast.LENGTH_LONG).show();

                    isUserNotfied = true;
                    isFastForward = true;

                    updateStatusButtons();
                }
            }
        } else {
            seekBarLastPosition = progress;
        }
    }

    public void onStartTrackingTouch(SeekBar seekBar) {
        isUserSeeking = true;
        isUserNotfied = false;
    }

    public void onStopTrackingTouch(SeekBar seekBar) {
        isUserSeeking = false;
        isUserNotfied = false;

        if (decoder.isTrackerFormat() || decoder instanceof YmDecoder)
            return;

        if (isFastForward)
            ServicePlayer.p.fastForward();
        seekBar.setProgress(seekBarLastPosition);
    }

    // listener management
    void addOnNextPlaylistEntryListener(OnNextPlaylistEntryListener newlistener) {
        if (listeners.indexOf(newlistener) == -1)
            listeners.add(newlistener);
    }

    void removeOnNextPlaylistEntryListener(OnNextPlaylistEntryListener removelistener) {
        listeners.remove(removelistener);
    }

    void fireNextPlaylistEntry(Playlist.Entry pe) {
        for (OnNextPlaylistEntryListener l: listeners)
            if (l != null)
                l.nextEntry(playlistname, pe);
    }

    // set playlist form playlist manager
    void setPlaylist(String playlistName, int playPosition) {

        synchronized(playlistSync) {


            if (PlaylistManager.getPlaylist(playlistName) != null && PlaylistManager.getPlaylist(playlistName).size() > 0) {
                playlist = PlaylistManager.getPlaylist(playlistName);
                playlist.setPlayPosition(playPosition);
                playlist.syncPlayPositionForShuffle();
                playlist.setShuffleMode(prefsIsShuffle);
                prepareNextFile(0);

                Modo.playlistname = playlistName;
                updatePlaylistname();
            }
        }
    }

    // generate playlist from files
    static void setPlaylist(ArrayList<File> pl, File playing, boolean withinZip, int selectedIndex) {

        synchronized(playlistSync) {

            // If there is a mix of directories and files in current zipdir, selectedIndex get the right path of the user selected file.
            //withinZipfile = withinZip ? (ModoFile)pl.get(0) : null;
            withinZipfile = withinZip ? (ModoFile)pl.get(selectedIndex) : null;

            for (Playlist.Entry pe: peCache) {
                pe.path = null;
                pe.zipEntry = null;
            }

            // direct access and reusing array for speed
            playlistFiles.clear();
            playlistFiles.entries.ensureCapacity(peCache.length);
            playlistFiles.shadowEntries.ensureCapacity(peCache.length);

            int current = 0;
            for (File f: pl) {
                if (current >= peCache.length)
                    break;
                if (!f.isFile())
                    continue;
                if (f instanceof ModoFile) {
                    peCache[current].path = ((ModoFile) f).getSrc().getAbsolutePath();
                    peCache[current].zipEntry = ((ModoFile) f).getZipEntry();
                } else {
                    peCache[current].path = f.getAbsolutePath();
                    peCache[current].zipEntry = null;
                }
                playlistFiles.entries.add(peCache[current]);
                playlistFiles.shadowEntries.add(peCache[current]);

                if (f == playing)
                    playlistFiles.setPlayPosition(playlistFiles.entries.size()-1);

                current += 1;
            }

            Collections.shuffle(playlistFiles.shadowEntries);
            playlistFiles.resetPlayedAll();
            playlist = playlistFiles;
        }
    }

    void resetDecoders() {
        // Free memory, stop songs, etc.
        synchronized (sync) {
            gmeDecoder.reset();
            rsnDecoder.reset();
            mikmodDecoder.reset();
            sidDecoder.reset();
            ymDecoder.reset();
            asapDecoder.reset();
            nezplugDecoder.reset();
            spcDecoder.reset();
            aytDecoder.reset();
            //xmpDecoder.reset();
        }
    }

    void updateDecoders() {
        nezplugDecoder.setSilenceDetection(prefSilence);
        gmeDecoder.setSilenceDetection(prefSilence);
        sidDecoder.setSilenceDetection(prefSilence);
        ymDecoder.setSilenceDetection(prefSilence);
        asapDecoder.setSilenceDetection(prefSilence);
        rsnDecoder.setSilenceDetection(prefSilence);
        spcDecoder.setSilenceDetection(prefSilence);
        aytDecoder.setSilenceDetection(prefSilence);
    }

    void updateSoundBoost() {
        if (path != null) {
            String fname = path.getName().toLowerCase(Locale.getDefault());
            if (fname.endsWith(".ym")) { prefsSoundboost = prefsFormatSoundboost[BOOST_YM]; return; }
            if (fname.endsWith(".rsn")) { prefsSoundboost = prefsFormatSoundboost[BOOST_SPC]; return; }
            if (fname.endsWith(".kss")) { prefsSoundboost = prefsFormatSoundboost[BOOST_KSS]; return; }
            if (fname.endsWith(".hes")) { prefsSoundboost = prefsFormatSoundboost[BOOST_HES]; return; }
            if (fname.endsWith(".vgz")) { prefsSoundboost = prefsFormatSoundboost[BOOST_VGM]; return; }
            if (fname.endsWith(".vgm")) { prefsSoundboost = prefsFormatSoundboost[BOOST_VGM]; return; }
            if (fname.endsWith(".gym")) { prefsSoundboost = prefsFormatSoundboost[BOOST_GYM]; return; }
            if (fname.endsWith(".nsf")) { prefsSoundboost = prefsFormatSoundboost[BOOST_NSF]; return; }
            if (fname.endsWith(".nsfe")) { prefsSoundboost = prefsFormatSoundboost[BOOST_NSF]; return; }
            if (fname.endsWith(".ay")) { prefsSoundboost = prefsFormatSoundboost[BOOST_AY]; return; }
            if (fname.endsWith(".spc")) { prefsSoundboost = prefsFormatSoundboost[BOOST_SPC]; return; }
            if (fname.endsWith(".sap")) { prefsSoundboost = prefsFormatSoundboost[BOOST_SAP]; return; }
            if (fname.endsWith(".gbs")) { prefsSoundboost = prefsFormatSoundboost[BOOST_GBS]; return; }
            if (fname.endsWith(".mod")) { prefsSoundboost = prefsFormatSoundboost[BOOST_MODULES]; return; }
            if (fname.endsWith(".xm")) { prefsSoundboost = prefsFormatSoundboost[BOOST_MODULES]; return; }
            if (fname.endsWith(".s3m")) { prefsSoundboost = prefsFormatSoundboost[BOOST_MODULES]; return; }
            if (fname.endsWith(".it")) { prefsSoundboost = prefsFormatSoundboost[BOOST_MODULES]; return; }
            if (fname.endsWith(".med")) { prefsSoundboost = prefsFormatSoundboost[BOOST_MODULES]; return; }
            if (fname.endsWith(".okt")) { prefsSoundboost = prefsFormatSoundboost[BOOST_MODULES]; return; }
            if (fname.endsWith(".umx")) { prefsSoundboost = prefsFormatSoundboost[BOOST_MODULES]; return; }
            if (fname.startsWith("mod.")) { prefsSoundboost = prefsFormatSoundboost[BOOST_MODULES]; return; }
            if (fname.startsWith("xm.")) { prefsSoundboost = prefsFormatSoundboost[BOOST_MODULES]; return; }
            if (fname.startsWith("s3m.")) { prefsSoundboost = prefsFormatSoundboost[BOOST_MODULES]; return; }
            if (fname.startsWith("it.")) { prefsSoundboost = prefsFormatSoundboost[BOOST_MODULES]; return; }
            if (fname.startsWith("med.")) { prefsSoundboost = prefsFormatSoundboost[BOOST_MODULES]; return; }
            if (fname.startsWith("okt.")) { prefsSoundboost = prefsFormatSoundboost[BOOST_MODULES]; return; }
            if (fname.startsWith("umx.")) { prefsSoundboost = prefsFormatSoundboost[BOOST_MODULES]; return; }
            if (fname.endsWith(".sid")) { prefsSoundboost = prefsFormatSoundboost[BOOST_SID]; return; }
            if (fname.endsWith(".mus")) { prefsSoundboost = prefsFormatSoundboost[BOOST_SID]; return; }
        }
    }

    Decoder getDecoderForFile(File path) {
        String fname = path.getName().toLowerCase(Locale.getDefault());

        // YM Decoder supported files
        if (fname.endsWith(".ym"))
            return ymDecoder;

        // Rsn Decoder supported files
        if (fname.endsWith(".rsn"))
            return rsnDecoder;

        // GME Decoder supported files
        if (fname.endsWith(".kss"))
            return gmeDecoder;

        if (fname.endsWith(".hes"))
            return gmeDecoder;

        if (fname.endsWith(".vgz"))
            return gmeDecoder;

        if (fname.endsWith(".vgm"))
            return gmeDecoder;

        if (fname.endsWith(".gym"))
            return gmeDecoder;

        if (fname.endsWith(".nsf"))
            return gmeDecoder;

        if (fname.endsWith(".nsfe"))
            return gmeDecoder;

        // Spectrum Turbosound
        if (fname.endsWith(".ayt"))
            return aytDecoder;

        // SPC Decoder, fork of GME with fast SPC
        if (fname.endsWith(".ay"))
            return spcDecoder;

        if (fname.endsWith(".spc"))
            return spcDecoder;

        // ASAP
        if (fname.endsWith(".sap"))
            return asapDecoder;

        // Nezplug
        if (fname.endsWith(".gbs"))
            return nezplugDecoder;

        // Mikmod
        if (fname.endsWith(".mod"))
            return mikmodDecoder;

        if (fname.endsWith(".xm"))
            return mikmodDecoder;

        if (fname.endsWith(".s3m"))
            return mikmodDecoder;

        if (fname.endsWith(".it"))
            return mikmodDecoder;

        if (fname.endsWith(".med"))
            return mikmodDecoder;

        if (fname.endsWith(".okt"))
            return mikmodDecoder;

        //if (fname.endsWith(".umx"))
        //	return mikmodDecoder;

        if (fname.startsWith("mod."))
            return mikmodDecoder;

        if (fname.startsWith("xm."))
            return mikmodDecoder;

        if (fname.startsWith("s3m."))
            return mikmodDecoder;

        if (fname.startsWith("it."))
            return mikmodDecoder;

        if (fname.startsWith("med."))
            return mikmodDecoder;

        if (fname.startsWith("okt."))
            return mikmodDecoder;

        //if (fname.startsWith("umx."))
        //	return xmpDecoder;

        // C64
        if (fname.endsWith(".sid"))
            return sidDecoder;

        if (fname.endsWith(".mus"))
            return sidDecoder;

        // unknown file - just return random decoder for error message and avoid app crash with NullPointer
        return spcDecoder;
    }

    private static long lastShake = System.currentTimeMillis();
    public void onShake() {
        if (System.currentTimeMillis() - lastShake > 1000)
            mHandler.sendEmptyMessage(Modo.MESSAGE_SHAKE);
        lastShake = System.currentTimeMillis();
    }


    private String[] TimeToString = {
        "00", "01", "02", "03", "04", "05", "06", "07", "08", "09",
        "10", "11", "12", "13", "14", "15", "16", "17", "18", "19",
        "20", "21", "22", "23", "24", "25", "26", "27", "28", "29",
        "30", "31", "32", "33", "34", "35", "36", "37", "38", "39",
        "40", "41", "42", "43", "44", "45", "46", "47", "48", "49",
        "50", "51", "52", "53", "54", "55", "56", "57", "58", "59",
        "60", "61", "62", "63", "64", "65", "66", "67", "68", "69",
        "70", "71", "72", "73", "74", "75", "76", "77", "78", "79",
        "80", "81", "82", "83", "84", "85", "86", "87", "88", "89",
        "90", "91", "92", "93", "94", "95", "96", "97", "98", "99", "??" };


}