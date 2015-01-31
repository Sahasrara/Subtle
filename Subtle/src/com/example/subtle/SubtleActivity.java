package com.example.subtle;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.nhaarman.listviewanimations.itemmanipulation.DynamicListView;
import com.nhaarman.listviewanimations.itemmanipulation.swipedismiss.OnDismissCallback;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.Toast;


public class SubtleActivity extends FragmentActivity implements OnSeekBarChangeListener, TabListener {
	/**
	 * Global Settings
	 */
	public static final String SUBTAG = "Subtle";
	public static final String CACHE_DIR_NAME = "Cache";
	public static File CURRENT_CACHE_LOCATION;
	private final static int PROGRESS_REFRESH_RATE = 100; // Milliseconds
	
	/**
	 * Message Types
	 */
	public static final int DIALOG_MESSAGE = 1;
	public static final int SEEK_MESSAGE = 2;
	public static final int DOWNLOAD_PROGRESS_MESSAGE = 3;
	public static final int DOWNLOAD_COMPLETE = 4;
	public static final int LISTING_RETRIEVED = 5;
	public static final int UNLOCK_BROWSER = 6;
	public static final int PLAYBACK_COMPLETE = 7;
	public static final int PARSED_LISTING = 8;
	
	/**
	 * Animation Constants
	 */
	public static final int LOADING_FADE_TIME = 500;
	
	/**
	 * Comparators
	 */
	public static final Comparator<ServerFileData> SERVER_FILE_DATA_TITLE_COMPARATOR = new Comparator<ServerFileData>() {
        @Override
        public int compare(ServerFileData  sfd1, ServerFileData  sfd2) {
        	if (sfd1.getTrackNumber() != -1 && sfd2.getTrackNumber() != -1) {// First sort by Track Number
        		return sfd1.getTrackNumber().compareTo(sfd2.getTrackNumber());
        	} else { // Second sort by name
        		return sfd1.getTitle().compareTo(sfd2.getTitle());
        	}
        }
    };
	
	/**
	 * Subtle Resources
	 */
	public Handler appRefreshHandler;
	private ActionBar actionBar;
	private Button playButton;
	private Button prevButton;
	private Button nextButton;
	private SeekBar seekBar;
	private boolean seeking;

	private Tab browserTab;
	private Tab queueTab;
	private Tab settingsTab;
	private SystemIntentReceiver systemIntentReceiver;
	
	/**
	 * Lifecycle Methods
	 */	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        /**
         * Logging Uncaught Exceptions
         */
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread paramThread, Throwable paramThrowable) {
            	File output = new File(SubtleActivity.CURRENT_CACHE_LOCATION.getAbsolutePath(), "ERROR");
            	try {
	            	if (!output.exists()) {
	            		output.createNewFile();
	    			}
	            	FileWriter fw = new FileWriter(output.getAbsoluteFile());
	    			BufferedWriter bw = new BufferedWriter(fw);
	    			StringWriter errors = new StringWriter();
	    			paramThrowable.printStackTrace(new PrintWriter(errors));
	    			bw.write(paramThrowable.getMessage() + "\n" + errors.toString());
	    			bw.close();
            	} catch (Exception e) {
            		
            	}
            }
        });

        /**
         * Load Default Preferences
         */
        loadDefaultPreferences();
                		
		/**
		 * Setup Cache Directory
		 */
		Map<File, Boolean> mounts = new HashMap<File, Boolean>(); // Location -- exists or not
		String secondaryStorage = System.getenv("SECONDARY_STORAGE");
		if (isExternalStorageWritable()) {
			// Get Possible Write Directories
			File[] cacheLocations = this.getExternalFilesDirs(Environment.DIRECTORY_MUSIC);
			for (File cacheLocation : cacheLocations) {
				try {
					Log.v(SUBTAG, "Good cache location: " + cacheLocation.toString());
				} catch (Exception e) {
					Log.v(SUBTAG, "Bad cache location found");
					continue;
				}
				cacheLocation = new File(cacheLocation.getAbsolutePath(), CACHE_DIR_NAME);
				if (!cacheLocation.exists()) {
					if (cacheLocation.mkdirs()) {
						Log.v(SUBTAG, "Succeeded in to creating "+cacheLocation.toString());
						mounts.put(cacheLocation, true);
						if (secondaryStorage != null && cacheLocation.toString().contains(secondaryStorage)) {
							SubtleActivity.CURRENT_CACHE_LOCATION = cacheLocation;
						}
					} else {
						Log.v(SUBTAG, "Failed to create "+cacheLocation.toString());
						mounts.put(cacheLocation, false);
					}
				} else {
					mounts.put(cacheLocation, true);
					if (secondaryStorage != null && cacheLocation.toString().contains(secondaryStorage)) {
						Log.v(SUBTAG, "Setting cache location to " + cacheLocation.toString());
						SubtleActivity.CURRENT_CACHE_LOCATION = cacheLocation;
					}
				}
			}
		} else {
			throw new RuntimeException("Could not write to external storage.");
		}
		
		// No sdcard, Mount Whatever
		if (SubtleActivity.CURRENT_CACHE_LOCATION == null) {
			for (Map.Entry<File, Boolean> entry: mounts.entrySet()) {
				if (entry.getValue()) {
					Log.v(SUBTAG, "Could not find external storage, using: " + entry.getKey().toString());
					SubtleActivity.CURRENT_CACHE_LOCATION = entry.getKey();
					break;
				}
			}
		}
		// Couldn't Mount Whatever!
		if (SubtleActivity.CURRENT_CACHE_LOCATION == null) {
			throw new RuntimeException("Could not create cache dir!");	
		}
		Log.v(SUBTAG, "Cache dir is "+SubtleActivity.CURRENT_CACHE_LOCATION);
		
    	/**
    	 * Setup Main Activity View
    	 */
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subtle);  
        
        /**
         * Setup Fragments
         */
		this.fragmentViews[BROWSER_FRAGMENT] = new BrowserFragment();
		this.fragmentViews[QUEUE_FRAGMENT] = new QueueFragment();
		this.fragmentViews[SETTINGS_FRAGMENT] = new SettingsFragment();
        
        /**
         * Setup Tab Bar
         */
        this.actionBar = getActionBar();
        this.actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS); 
        this.actionBar.setDisplayShowHomeEnabled(false);
        this.actionBar.setDisplayShowTitleEnabled(false);
        //Settings Tab
        this.settingsTab = this.actionBar.newTab();
        this.settingsTab.setText("Settings");
        this.settingsTab.setTabListener(this);
        this.actionBar.addTab(this.settingsTab);
        //Queue Tab
        this.queueTab = this.actionBar.newTab();
        this.queueTab.setText("Queue");
        this.queueTab.setTabListener(this);
        this.actionBar.addTab(this.queueTab);
        //Browser Tab
        this.browserTab = this.actionBar.newTab();
        this.browserTab.setText("Browser");
        this.browserTab.setTabListener(this);
        this.actionBar.addTab(this.browserTab);

        /**
         * Setup Controller
         */
        this.playButton = (Button) findViewById(R.id.play_button);
        this.playButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                play(v);
            }
        });
        this.prevButton = (Button) findViewById(R.id.previous_button);
        this.prevButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                previous(v);
            }
        });
        this.nextButton = (Button) findViewById(R.id.next_button);
        this.nextButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                next(v);
            }
        });
        this.seekBar = (SeekBar) findViewById(R.id.seek_bar);
        
        /**
         * Set All Listeners and Permanent Views
         */
        this.seekBar.setOnSeekBarChangeListener(this);

        /**
         * Setup UI Handler
         * This is the messaging system that handles UI updates as a 
         * response to asynchronous events
         */
        this.appRefreshHandler = new Handler(Looper.getMainLooper()) {
        	@Override
        	public void handleMessage(Message inputMessage) {
        		Integer resourceID;
        		switch (inputMessage.what) {
	                case DIALOG_MESSAGE:
	                	// Present Dialog Box
	                	((Dialog) inputMessage.obj).show();
	                	
						// Stop Refresh Animation (if there)
	                	((BrowserFragment) fragmentViews[BROWSER_FRAGMENT]).setBrowserLoading(false, false);
						
	                    break;
	                case SEEK_MESSAGE:
	                	// Update Seek Bar
	                	updateSeekBar((inputMessage.arg1 > 100) ? 100 : inputMessage.arg1);
	                	
	                	break;
	                case PLAYBACK_COMPLETE:
	                	// Playback Complete (carry on!)
	                	setPlayingButton(false);
                		if (((QueueFragment) fragmentViews[QUEUE_FRAGMENT]).isNext()) { // If next, play
                			next(null);
                		}
                		
                		break;
	                case DOWNLOAD_PROGRESS_MESSAGE:	                	
	                	// Update Progress Bar
	                	if (currentFragment == QUEUE_FRAGMENT) {
	                		@SuppressWarnings("unchecked")
							Set<Map.Entry<Integer, Integer>> progresses = (Set<Map.Entry<Integer, Integer>>) inputMessage.obj; // UID - Progress
	                		((QueueFragment) fragmentViews[QUEUE_FRAGMENT]).updateDownloadProgresses(progresses);
	                	}
	                	
	                	break;
	                case DOWNLOAD_COMPLETE:
	                	resourceID = inputMessage.arg1;
	                	
	                	// Update Queue Datastore
	                	((QueueFragment) fragmentViews[QUEUE_FRAGMENT]).setItemCached(resourceID);
	                	
	                	// Update Browser Datastore
	                	((BrowserFragment) fragmentViews[BROWSER_FRAGMENT]).setCachedInCurrentList(resourceID);
	                	
	                	// Update Database
	                	ServerFileData row = Database.getInstance(null).getRow(resourceID);
	                	row.setCached(true);
	                	Database.getInstance(null).addMusic(row);
	                	
	                	break;
	                case LISTING_RETRIEVED:
	                	// Grab XML byte[]
	                	((RawDirectoryListing) inputMessage.obj).parseListing(SubtleActivity.this);
	                	
						break;
	                case PARSED_LISTING:
	                	ParsedDirectoryListing newList = (ParsedDirectoryListing) inputMessage.obj;
	                	
						// Clear out Old Data
	                	Database.getInstance(null).deleteChildren(newList.getParent().getUid());
						// Sort by Name
						Collections.sort(newList.getListing(), SERVER_FILE_DATA_TITLE_COMPARATOR);
	                	// Update Database
						Database.getInstance(null).addMusic(newList.getListing().toArray(new ServerFileData[newList.getListing().size()]));
	                	// Update UI Element
						((BrowserFragment) fragmentViews[BROWSER_FRAGMENT]).swapCurrentList(newList.getListing());
						// Set Current Directory
						if (!((BrowserFragment) fragmentViews[BROWSER_FRAGMENT]).isSwipeRefreshing()) {
							// If this refresh was caused by a swipe, we didn't change directory
							((BrowserFragment) fragmentViews[BROWSER_FRAGMENT]).setCurrentDirectory(newList.getParent());
						}
						// Stop Refresh Animation (if there)
						((BrowserFragment) fragmentViews[BROWSER_FRAGMENT]).setBrowserLoading(false, false);
						
						break;
	            }
            }
        };
        
        /**
         * Create System Intent Receiver
         */
        this.systemIntentReceiver = new SystemIntentReceiver();
        IntentFilter filter = new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        registerReceiver(this.systemIntentReceiver, filter);
 
        
        /**
         * Initialize Server and Database
         */
        Database.getInstance(this);
        SubsonicServer.getInstance(this);
        
        // Setup Sound Machine with Handler for Callbacks
        SoundMachine.getInstance().setHandler(this.appRefreshHandler);
        
        // Seeking
        this.seeking = false;
        
        // Start UI Updater
        UIRefreshThread.start(this.appRefreshHandler, PROGRESS_REFRESH_RATE);
        
        // Show Browser
        this.actionBar.selectTab(this.queueTab);
        this.actionBar.selectTab(this.browserTab);
    }

    
    @Override
    public void onDestroy() {
    	// Super
    	super.onDestroy();
    	
    	// Kill Sound Machine
    	SoundMachine.getInstance().shutdown();
    	
    	// Kill UIRefreshThread     	
    	UIRefreshThread.shutdown();
    	
    	// Kill Seamstress
    	Seamstress.getInstance().shutdown();
    }
	
    /**
     * Playback Methods
     */
    public void pause(View view) {
    	SoundMachine.getInstance().pause();
    }
    public void next(View view) {
    	UIRefreshThread.setProgressPaused(true);
    	((QueueFragment) this.fragmentViews[QUEUE_FRAGMENT]).advance();
    	SoundMachine.getInstance().stop();
		play(null);
		UIRefreshThread.setProgressPaused(false);
    }
    public void previous(View view) {
    	UIRefreshThread.setProgressPaused(true);
    	((QueueFragment) this.fragmentViews[QUEUE_FRAGMENT]).retreat();
		SoundMachine.getInstance().stop();
		play(null);
		UIRefreshThread.setProgressPaused(false);
    }
    public void play(View view) {
    	ServerFileData current = ((QueueFragment) this.fragmentViews[QUEUE_FRAGMENT]).getCurrent();
    	if (current != null && current.getCached()) {
    		SoundMachine soundMachine = SoundMachine.getInstance();
    		if (soundMachine.isPlaying()) { // Pause
    			soundMachine.pause();
    			setPlayingButton(false);
    		} else if (soundMachine.isStopped()){ // Play
    			try {
    				if (!soundMachine.setTrack(current)) {
    					return;
    				}
    			} catch (Exception e) {
    				throw new RuntimeException(e);
    			}
        		soundMachine.play();
        		setPlayingButton(true);
        		
    		} else if (soundMachine.isPaused()) {
    			soundMachine.play();
    			setPlayingButton(true);
    			
    		}	
    	}
    }
    public void selectTrack(int index) {
    	UIRefreshThread.setProgressPaused(true);
    	((QueueFragment) this.fragmentViews[QUEUE_FRAGMENT]).setCurrent(index);
    	SoundMachine.getInstance().stop();
    	play(null);
    	UIRefreshThread.setProgressPaused(false);
    }
    
    /**
     * Playback UI Changes
     */
    private void setPlayingButton(boolean isPlaying) {
    	if (isPlaying) {
    		this.playButton.setText("Pause");
    	} else {
    		this.playButton.setText("Play");
    	}
    }

    /**
     * Preferences
     */
    public static final String BASE_URL_KEY = "base_url";
    public static final String USER_KEY = "user";
    public static final String PASSWORD_KEY = "password";
    public static final String CLIENT_NAME_KEY = "client_name";
    public static final String API_VERSION_KEY = "api_version";
    
    public static final String DEFAULT_BASE_URL = "http://10.0.2.2:8080/subsonic/";
    public static final String DEFAULT_USER = "admin";
    public static final String DEFAULT_PASSWORD = "admin";
    public static final String DEFAULT_CLIENT_NAME = "Subtle";
    public static final String DEFAULT_API_VERSION = "1.10.2";
    
    public void loadDefaultPreferences() {
    	SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
    	// Base URL
    	if (!preferencesContain(BASE_URL_KEY)) {
    		editor.putString(BASE_URL_KEY, DEFAULT_BASE_URL);
    	}
    	// User
    	if (!preferencesContain(USER_KEY)) {
    		editor.putString(USER_KEY, DEFAULT_USER);
    	}
    	// Password
    	if (!preferencesContain(PASSWORD_KEY)) {
    		editor.putString(PASSWORD_KEY, DEFAULT_PASSWORD);
    	}
    	// Client Name
    	if (!preferencesContain(CLIENT_NAME_KEY)) {
    		editor.putString(CLIENT_NAME_KEY, DEFAULT_CLIENT_NAME);
    	}
    	// API Version
    	if (!preferencesContain(API_VERSION_KEY)) {
    		editor.putString(API_VERSION_KEY, DEFAULT_API_VERSION);
    	}
    	editor.apply();
    }
    public boolean preferencesContain(String key) {
    	return getPreferences(MODE_PRIVATE).contains(key);
    }
    public boolean preferencesGetBoolean(String key, boolean defValue) {
    	return getPreferences(MODE_PRIVATE).getBoolean(key, defValue);
    }
    public float preferencesGetFloat(String key, float defValue) {
    	return getPreferences(MODE_PRIVATE).getFloat(key, defValue);
    }
    public int preferencesGetInt(String key, int defValue) {
    	return getPreferences(MODE_PRIVATE).getInt(key, defValue);
    }
    public long preferencesGetLong(String key, long defValue) {
    	return getPreferences(MODE_PRIVATE).getLong(key, defValue);
    }
    public String preferencesGetString(String key, String defValue) {
    	return getPreferences(MODE_PRIVATE).getString(key, defValue);
    }
    public void preferencesSetBoolean(String key, boolean value) {
    	SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
    	editor.putBoolean(key, value);
    	editor.apply();
    }
    public void preferencesSetFloat(String key, float value) {
    	SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
    	editor.putFloat(key, value);
    	editor.apply();
    }
    public void preferencesSetInt(String key, int value) {
    	SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
    	editor.putInt(key, value);
    	editor.apply();
    }
    public void preferencesSetLong(String key, long value) {
    	SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
    	editor.putLong(key, value);
    	editor.apply();
    }
    public void preferencesSetString(String key, String value) {
    	SharedPreferences.Editor editor = getPreferences(MODE_PRIVATE).edit();
    	editor.putString(key, value);
    	editor.apply();
    }   
        
    /**
     * Keyboard Helpers
     */
    public void hideSoftKeyboard() {
        InputMethodManager inputMethodManager = (InputMethodManager)  this.getSystemService(Activity.INPUT_METHOD_SERVICE);
        View currentFocus = this.getCurrentFocus();
        if (currentFocus != null) {
        	inputMethodManager.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
    }

    /**
     * File System Helpers
     */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
            Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }
    
    /**
     * System Intent Receivers
     */
    private class SystemIntentReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
                int state = intent.getIntExtra("state", -1);
                switch (state) {
                case 0:
                	// Unplugged
                    if (SoundMachine.getInstance().isPlaying()) {
                    	pause(null);
                    }
                    break;
                case 1:
                	// Plugged
                    break;
                default:
                	// Huh?
                    Log.v(SUBTAG, "Unknown headphone state detected!");
                }
			}
		}
    }
      
    /**
     * Seek Bar Methods
     */
	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}
	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		// Set Seeking True
		this.seeking = true;
	}
	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		SoundMachine soundMachine = SoundMachine.getInstance();
		
		// Seek
		soundMachine.seek(soundMachine.percentToTime(seekBar.getProgress()) * 1000);
		
		// Set Seeking False
		this.seeking = false;
	}
	private void updateSeekBar(int percent) {
		if (!seeking) {
			this.seekBar.setProgress(percent);
		}
	}
	
	/**
	 * Queue Specific
	 */
    public static final int QUEUE_SELECTED_COLOR = android.R.color.background_light;
    public static final int QUEUE_DESELECTED_COLOR = android.R.drawable.list_selector_background;
	private void enqueueSong(ServerFileData song) {
		if (song.getResourceType() != ServerFileData.FILE_TYPE) {
			throw new RuntimeException("Enqueued a non-file type resourse!");
		}
		
		// Check if Cached
		boolean cached = Database.getInstance(null).getRow(song.getUid()).getCached();
		
		// Download if Needed
		if (!cached && !SubsonicServer.getInstance(null).isDownloading(song)) {
			SubsonicServer.getInstance(null).download(this, song);
		}	
		
		// Add to Queue
		((QueueFragment) fragmentViews[QUEUE_FRAGMENT]).enqueue(song);
		
		// Signal that we queued
		Context context = getApplicationContext();
		CharSequence text = "Added to queue";
		int duration = Toast.LENGTH_SHORT;
		Toast toast = Toast.makeText(context, text, duration);
		toast.setGravity(Gravity.BOTTOM | Gravity.CENTER, 0, 0);
		toast.show();
	}
	
	/**
	 * Browser Specific
	 */
	public static final int BROWSER_ROW_CACHED = android.R.color.holo_blue_dark;
	public static final int BROWSER_ROW_DECACHED = android.R.drawable.list_selector_background;
	@Override
	public void onBackPressed() {
		if (this.fragmentViews[BROWSER_FRAGMENT] != null) {
			((BrowserFragment) this.fragmentViews[BROWSER_FRAGMENT]).backButtonPressed();
		}
	}
	
    /**
     * Tab Listener Functions
     */
	private static final int SETTINGS_FRAGMENT = 0;
	private static final int QUEUE_FRAGMENT = 1;
	private static final int BROWSER_FRAGMENT = 2;

	private Fragment[] fragmentViews = new Fragment[3];
	private int currentFragment;
	@Override
	public void onTabSelected(Tab tab, FragmentTransaction ft) {
		int position = tab.getPosition();
		currentFragment = position;
		Fragment fragment = this.fragmentViews[position];
	    getFragmentManager().beginTransaction().replace(R.id.container, fragment).commit();
	}
	@Override
	public void onTabUnselected(Tab tab, FragmentTransaction ft) {
		// Remove keyboard if it was up leaving settings tab
		if (tab.getPosition() == SubtleActivity.SETTINGS_FRAGMENT) {
			hideSoftKeyboard();
		}
	}
	@Override
	public void onTabReselected(Tab tab, FragmentTransaction ft) {
		// nop
	}
	
	/**
	 * Tab Fragments
	 */
	public static class BrowserFragment extends Fragment {
		private ListView browser;
		private SwipeRefreshLayout swipeRefreshLayout;
		private BrowserAdapter browserAdapter;
		private ServerFileData currentDirectory;
		private OnItemClickListener onItemClickListener;
		private SwipeRefreshLayout.OnRefreshListener onRefreshListener;
        private FrameLayout loadingOverlay;
        private AlphaAnimation inAnimation;
        private AlphaAnimation outAnimation;
        private boolean loading;
        private boolean browserLoading;
        
		public BrowserFragment() {
			super();
			this.loading = false;
			this.browserLoading = false;
		}
		
	    @Override
	    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	        return inflater.inflate(R.layout.browser_fragment, container, false);
	    }
	    
	    @Override
	    public void onActivityCreated(Bundle savedInstanceState) {
	        super.onActivityCreated(savedInstanceState);
	        /**
	         * Setup Adapter
	         */
	        if (this.browserAdapter == null) {
		        /**
		         * Setup First Listing
		         */
	        	this.browserAdapter = new BrowserAdapter((SubtleActivity) getActivity(), R.layout.browser_row_view);
		        SubsonicServer server = SubsonicServer.getInstance(null);
		        Database database = Database.getInstance((SubtleActivity) getActivity());
		        ServerFileData rootDir = new ServerFileData();
		        rootDir.setResourceType(ServerFileData.ROOT_TYPE);
		        rootDir.setParent(ServerFileData.ROOT_UID);
		        
				// Check Cache (and unlock if cached)
				List<ServerFileData> children = database.getDirectoryChildren(rootDir.getParent());
				if (children != null && children.size() > 0) {
					// Sort
					Collections.sort(children, SERVER_FILE_DATA_TITLE_COMPARATOR);
					
					// Set Current Directory
					setCurrentDirectory(rootDir);
					
					// Setup Adapter Data
					swapCurrentList(children);
				} else {
			        server.getDirectoryListing(rootDir);
				}
	        }
	        
	        /**
	         * Setup Overlay
	         */
	        this.loadingOverlay = (FrameLayout) getView().findViewById(R.id.loadingOverlayContainer);
	        
	        /**
	         * Create Listeners
	         */
	    	if (this.onItemClickListener == null) {
		        this.onItemClickListener = new OnItemClickListener(){
					@Override
					public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
						ServerFileData selectedItem = browserAdapter.getItem(position);
						SubsonicServer server = SubsonicServer.getInstance(null);
						if (selectedItem.isDirectory()) { // Directory
							// Check Cache (and unlock if cached)
							List<ServerFileData> children = Database.getInstance((SubtleActivity) getActivity()).getDirectoryChildren(selectedItem.getUid());
							Collections.sort(children, SERVER_FILE_DATA_TITLE_COMPARATOR);
							if (children != null && children.size() > 0) {
								// Set Current Directory
								currentDirectory = selectedItem;
								
								// Setup Adapter Data
								browserAdapter.clear();
								browserAdapter.addAll(children);
								
								// Refresh Browser
								refreshBrowser();
							} else {
								setBrowserLoading(true, false);
								server.getDirectoryListing(selectedItem);
							}
						} else { // File
							// Queue
							((SubtleActivity) getActivity()).enqueueSong(browserAdapter.getItem(position));
						}
					}
				};
	    	}
	    	if (this.onRefreshListener == null) {
				this.onRefreshListener = new SwipeRefreshLayout.OnRefreshListener() {
			        @Override
			        public void onRefresh() {
			        	setBrowserLoading(true, true);
			        	if (currentDirectory == null) {
			                ServerFileData root = new ServerFileData();
			                root.setResourceType(ServerFileData.ROOT_TYPE);
			                SubsonicServer.getInstance(null).getDirectoryListing(root);
			        	} else {
				        	SubsonicServer.getInstance(null).getDirectoryListing(currentDirectory);
			        	}
			        }
			    };
	    	}
	        
			/**
			 * Setup Browser View
			 */
			// Get ListView
			this.browser = (ListView) getView().findViewById(R.id.browser);

			// Setup Click Listener
			this.browser.setOnItemClickListener(onItemClickListener);
			
			// Setup Adapter
			this.browser.setAdapter(this.browserAdapter);
			
			/**
			 * Setup Swipe Refresh Layout
			 */
			// Get Swipe Refresh layout
			this.swipeRefreshLayout = (SwipeRefreshLayout) getView().findViewById(R.id.swipe_container);
			// Setup Refresh Colors
			this.swipeRefreshLayout.setColorSchemeResources(
					android.R.color.holo_blue_dark, 
					android.R.color.holo_blue_light, 
					android.R.color.holo_green_light, 
					android.R.color.holo_green_light);
			// Setup Refresh Listener
			this.swipeRefreshLayout.setOnRefreshListener(onRefreshListener);
			
			// Set Loading if Need be
			if (this.loading) {
				if (this.browserLoading) {
					this.setBrowserLoading(true, true);
				}else {
					this.setBrowserLoading(true, false);
				}
			}
	    }
	    
	    public void backButtonPressed() {
			if (this.currentDirectory != null) {
				// Always grab from cache	
				Integer parent;
				try {
					parent = this.currentDirectory.getParent();
				} catch (RuntimeException e) {
					// This 
					Log.e(SUBTAG, e.getMessage());
					return;
				}
				Database database = Database.getInstance(null);
				this.currentDirectory = database.getRow(parent);
				List<ServerFileData> children = database.getDirectoryChildren(this.currentDirectory.getUid());
				Collections.sort(children, SERVER_FILE_DATA_TITLE_COMPARATOR);
				
				// Setup Adapter Data
				this.browserAdapter.clear();
				this.browserAdapter.addAll(children);
				
				// Refresh Browser
				refreshBrowser();
			}
	    }
	    
	    public void swapCurrentList(List<ServerFileData> newList) {
			browserAdapter.clear();
			browserAdapter.addAll(newList);
			refreshBrowser();
	    }
	    
	    public void setCachedInCurrentList(int resourceID) {
        	for (int i = 0; i < browserAdapter.getCount(); i++) {
        		if (browserAdapter.getItem(i).getUid().equals(resourceID)) {
        			browserAdapter.getItem(i).setCached(true);
        		}
        	}
	    }
	    
	    public boolean isSwipeRefreshing() {
	    	return this.swipeRefreshLayout.isRefreshing();
	    }
	    
	    public ServerFileData getCurrentDirectory() {
	    	return this.currentDirectory;
	    }
	    
	    public void setCurrentDirectory(ServerFileData currentDirectory) {
	    	this.currentDirectory = currentDirectory;
	    }
	    
		public void refreshBrowser() {
	    	if (this.browserAdapter != null) {	
	    		this.browserAdapter.notifyDataSetChanged();
	    	}
		}
		
		public boolean isLoading() {
			return this.loading;
		}
		
		public void setBrowserLoading(boolean loading, boolean wasPull) {
	    	if (loading) {   
		    	this.inAnimation = new AlphaAnimation(0f, 1f);
		    	this.inAnimation.setDuration(LOADING_FADE_TIME);
	    		this.loadingOverlay.setAnimation(this.inAnimation);
	    		this.loadingOverlay.setVisibility(View.VISIBLE);
	    		if (wasPull) {
	    			this.swipeRefreshLayout.setRefreshing(true);
	    			this.browserLoading = true;
	    		} else {
	    			this.browserLoading = false;
	    		}
	    		this.loading = true;
				this.browser.setEnabled(false);
				this.swipeRefreshLayout.setEnabled(false);
	    	} else {
		    	this.outAnimation = new AlphaAnimation(1f, 0f);
		    	this.outAnimation.setDuration(LOADING_FADE_TIME);
	    		this.loadingOverlay.setAnimation(this.outAnimation);
	    		this.loadingOverlay.setVisibility(View.GONE);
	    		if (this.swipeRefreshLayout.isRefreshing()) {
	    			this.swipeRefreshLayout.setRefreshing(false);
	    			this.browserLoading = false;
	    		}
	    		this.loading = false;
				this.browser.setEnabled(true);
				this.swipeRefreshLayout.setEnabled(true);
	    	}
		}  
	}
	
	public static class QueueFragment extends Fragment  {
		private QueueAdapter queueAdapter;
		private DynamicListView queueListView;
		private AdapterView.OnItemClickListener onItemClickListener;
		private OnDismissCallback onDismissCallback;
		
		public QueueFragment() {
			super();
		}
		
	    @Override
	    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	        return inflater.inflate(R.layout.queue_fragment, container, false);
	    }
	    
	    @Override
	    public void onActivityCreated(Bundle savedInstanceState) {
	        super.onActivityCreated(savedInstanceState);
			/**
			 * Setup Listeners
			 */
	        if (this.onItemClickListener == null) {
				this.onItemClickListener = new AdapterView.OnItemClickListener() {
					@Override
					public void onItemClick(AdapterView<?> parent, View view,
							int position, long id) {
						((SubtleActivity) getActivity()).selectTrack(position);
					}
				};
	        }
	        if (this.onDismissCallback == null) {
				this.onDismissCallback = new OnDismissCallback() {
					@Override
					public void onDismiss(final ViewGroup listView, final int[] reverseSortedPositions) {
						for (int position : reverseSortedPositions) {
							// If Current Track, Stop Playback
							if (queueAdapter.currentIndex() == position) {
								SoundMachine.getInstance().stop();
							}
	
							// Delete Row in View
							queueAdapter.remove(position);
							
							// Refresh Visible Rows
							invalidateIfCurrentIsVisible();
	    	            }
					}
		        };
	        }
	        
	        if (this.queueAdapter == null) {
	        	this.queueAdapter = new QueueAdapter((SubtleActivity) getActivity(), R.layout.queue_row_view);
	        }
	        
	        this.queueListView = (DynamicListView) getView().findViewById(R.id.queue);
	        this.queueListView.enableDragAndDrop();
	        this.queueListView.setOnItemLongClickListener(new OnItemLongClickListener() {
    	        @Override
    	        public boolean onItemLongClick(final AdapterView<?> viewAdapter, final View view,
    	                                       final int position, final long id) {
    	        	queueListView.startDragging(position);
    	            return true;
    	        }
    	    });
	        this.queueListView.setOnItemClickListener(onItemClickListener);
	        this.queueListView.enableSwipeToDismiss(onDismissCallback);
	        this.queueListView.setAdapter(queueAdapter);
	    }
	    
	    public void enqueue(ServerFileData toQueue) {
	    	this.queueAdapter.enqueue(toQueue);
	    }
	    
	    public ServerFileData getCurrent() {
	    	return this.queueAdapter.current();
	    }
	    
	    public boolean isNext() {
	    	return this.queueAdapter.isNext();
	    }
	    
	    public void retreat() {
	    	this.queueAdapter.retreat();
	    	invalidateIfCurrentIsVisible();
	    }
	    
	    public void advance() {
	    	this.queueAdapter.advance();
	    	invalidateIfCurrentIsVisible();
	    }
	    
	    public void setCurrent(int index) {
	    	this.queueAdapter.setCurrent(index);
	    	invalidateIfCurrentIsVisible();
	    }
	    
	    public void setItemCached(int resourceID) {
	    	// Datastore
        	for (int i = 0; i < queueAdapter.getCount(); i++) {
        		if (queueAdapter.getItem(i).getUid().equals(resourceID)) {
        			queueAdapter.getItem(i).setCached(true);
        		}
        	}
	    	
        	// View
        	if (queueListView != null) {
            	for (int i = 0; i < queueListView.getChildCount(); i++) {
            		if (queueListView.getChildAt(i).getId() == resourceID) {
            			ProgressBar progressBar = (ProgressBar) queueListView.getChildAt(i).findViewById(R.id.progress_bar);
            			if (progressBar != null) {
            				progressBar.setProgress(100);
            			}
            		}
            	}
        	}
	    }
	    
	    public void updateDownloadProgresses(Set<Map.Entry<Integer, Integer>> progresses) {
    		if (queueListView != null) {
    			int start = queueListView.getFirstVisiblePosition();
    			int end = queueListView.getLastVisiblePosition();
      			for (Map.Entry<Integer, Integer> entry : progresses) {
      				for (int i = start; i <= end; i++) {
                		View childView = queueListView.getChildAt(i - start);
                		if (childView != null) {
                			QueueAdapter.QueueAdapterViewHolder viewHolder = (QueueAdapter.QueueAdapterViewHolder) childView.getTag();
                			
	                		if (viewHolder.id == entry.getKey()) {
	                			ProgressBar progressBar = (ProgressBar) childView.findViewById(R.id.progress_bar);
	                			if (progressBar != null) {
	                				progressBar.setProgress(entry.getValue());
	                			}
	                		}
                		}
                	}
      			}
    		}
	    }
	    
	    public void invalidateIfCurrentIsVisible() {
	    	if (this.queueListView != null && this.queueAdapter != null) {	
	    		int start = this.queueListView.getFirstVisiblePosition();
	    		int end = this.queueListView.getLastVisiblePosition();
	    		if ((this.queueAdapter.currentIndex() >= start && this.queueAdapter.currentIndex() <= end) || this.queueAdapter.getCount() == 0) {
	    			this.queueListView.invalidateViews();
	    		}
	    	}
	    }
	}
	
	public static class SettingsFragment extends Fragment {
		private EditText username;
		private EditText password;
		private EditText serverUrl;
		private EditText clientName;
		
		public SettingsFragment() {
			super();
		}
		
	    @Override
	    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	        View rootView = inflater.inflate(R.layout.settings_fragment, container, false);
	        this.username = (EditText)  rootView.findViewById(R.id.username);	        
	        this.password = (EditText)  rootView.findViewById(R.id.password);	  
	        this.serverUrl = (EditText)  rootView.findViewById(R.id.url);	  
	        this.clientName = (EditText)  rootView.findViewById(R.id.clientName);	  
	        
	        // Load Defaults
	        this.username.setText(((SubtleActivity) getActivity()).preferencesGetString(SubtleActivity.USER_KEY, SubtleActivity.DEFAULT_USER));	        
	        this.password.setText(((SubtleActivity) getActivity()).preferencesGetString(SubtleActivity.PASSWORD_KEY, SubtleActivity.DEFAULT_PASSWORD));
	        this.serverUrl.setText(((SubtleActivity) getActivity()).preferencesGetString(SubtleActivity.BASE_URL_KEY, SubtleActivity.DEFAULT_BASE_URL));	  
	        this.clientName.setText(((SubtleActivity) getActivity()).preferencesGetString(SubtleActivity.CLIENT_NAME_KEY, SubtleActivity.DEFAULT_CLIENT_NAME));
	        
	        // Setup Listeners
			this.username.setOnFocusChangeListener(new View.OnFocusChangeListener() {
				@Override
				public void onFocusChange(View v, boolean hasFocus) {
					if (!hasFocus) {
						String input = ((EditText) v).getText().toString();
						((SubtleActivity) getActivity()).preferencesSetString(SubtleActivity.USER_KEY, input);
					}
				}
			});
			this.password.setOnFocusChangeListener(new View.OnFocusChangeListener() {
				@Override
				public void onFocusChange(View v, boolean hasFocus) {
					if (!hasFocus) {
						String input = ((EditText) v).getText().toString();
						((SubtleActivity) getActivity()).preferencesSetString(SubtleActivity.PASSWORD_KEY, input);
					}
				}
			});;
			this.serverUrl.setOnFocusChangeListener(new View.OnFocusChangeListener() {
				@Override
				public void onFocusChange(View v, boolean hasFocus) {
					if (!hasFocus) {
						String previousUrl = ((SubtleActivity) getActivity()).preferencesGetString(SubtleActivity.BASE_URL_KEY, SubtleActivity.DEFAULT_BASE_URL);
						String newUrl = ((EditText) v).getText().toString();
						((SubtleActivity) getActivity()).preferencesSetString(SubtleActivity.BASE_URL_KEY, newUrl);
						// If we change the library, we need to invalidate the db
						if (!previousUrl.equals(newUrl)) {
							Database.invalidate();
						}
					}
				}
			});
			this.clientName.setOnFocusChangeListener(new View.OnFocusChangeListener() {
				@Override
				public void onFocusChange(View v, boolean hasFocus) {
					if (!hasFocus) {
						String input = ((EditText) v).getText().toString();
						((SubtleActivity) getActivity()).preferencesSetString(SubtleActivity.CLIENT_NAME_KEY, input);
					}
				}
			});
	        return rootView;
	    }
	}
}
