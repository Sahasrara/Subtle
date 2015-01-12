package com.example.subtle;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;


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
	public static final int DIRECTORY_LISTING_RETRIEVED = 5;
	public static final int ROOT_LISTING_RETRIEVED = 6;
	public static final int MUSIC_FOLDER_LISTING_RETRIEVED = 7;
	public static final int UNLOCK_BROWSER = 8;
	public static final int PLAYBACK_COMPLETE = 9;
	
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

	private ListView browser;
	private SwipeRefreshLayout swipeRefreshLayout;
	private ListView queueListView;
	private QueueAdapter queueAdapter;
	
	private Button playButton;
	private Button prevButton;
	private Button nextButton;
	private SeekBar seekBar;
	private boolean seeking;

	private BrowserAdapter browserAdapter;
	private XmlPullParserFactory xmlParserFactory;
	private Database database;
	private Tab browserTab;
	private Tab queueTab;
	private Tab settingsTab;
	private ServerFileData currentDirectory;
	private SystemIntentReceiver systemIntentReceiver;
	
	/**
	 * Lifecycle Methods
	 */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	/**
    	 * Setup Main Activity View
    	 */
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subtle);  
        
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
         * Setup Data Stores
         */
		this.browserAdapter = new BrowserAdapter(this, R.layout.browser_row_view);
		this.queueAdapter = new QueueAdapter(this, R.layout.queue_row_view);
		
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
         * Setup Fragments
         */
        this.fragmentViews[BROWSER_FRAGMENT] = new BrowserFragment(this);
        this.fragmentViews[QUEUE_FRAGMENT] = new QueueFragment(this);
        this.fragmentViews[SETTINGS_FRAGMENT] = new SettingsFragment(this);
        
        /**
         * Setup Tab Bar
         */
        this.actionBar = getActionBar();
        this.actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS); 
        this.actionBar.setDisplayShowHomeEnabled(false);
        this.actionBar.setDisplayShowTitleEnabled(false);
        //Browser Tab
        this.browserTab = this.actionBar.newTab();
        this.browserTab.setText("Browser");
        this.browserTab.setTabListener(this);
        this.actionBar.addTab(this.browserTab);
        //Queue Tab
        this.queueTab = this.actionBar.newTab();
        this.queueTab.setText("Queue");
        this.queueTab.setTabListener(this);
        this.actionBar.addTab(this.queueTab);
        //Settings Tab
        this.settingsTab = this.actionBar.newTab();
        this.settingsTab.setText("Settings");
        this.settingsTab.setTabListener(this);
        this.actionBar.addTab(this.settingsTab);
        
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
        		byte[] response;
        		List<ServerFileData> listing;
        		XmlPullParser xpp;
        		ServerFileData parent;
        		switch (inputMessage.what) {
	                case DIALOG_MESSAGE:
	                	// Present Dialog Box
	                	((Dialog) inputMessage.obj).show();
	                	
						// Stop Refresh Animation (if there)
						setBrowserLoading(false);
						
	                    break;
	                case SEEK_MESSAGE:
	                	// Update Seek Bar
	                	updateSeekBar((inputMessage.arg1 > 100) ? 100 : inputMessage.arg1);
	                	break;
	                case PLAYBACK_COMPLETE:
	                	// Playback Complete (carry on!)
                		if (queueAdapter.isNext()) { // If next, play
                			next(null);
                		}
                		break;
	                case DOWNLOAD_PROGRESS_MESSAGE:	                	
	                	// Update Progress Bar
	                	if (currentFragment == QUEUE_FRAGMENT) {
	                		if (queueListView != null) {
	                			@SuppressWarnings("unchecked")
	                			Set<Map.Entry<Integer, Integer>> progresses = (Set<Map.Entry<Integer, Integer>>) inputMessage.obj; // UID - Progress
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
	                	break;
	                case DOWNLOAD_COMPLETE:
	                	resourceID = inputMessage.arg1;
	                	
	                	// Update Queue Datastore
	                	for (int i = 0; i < queueAdapter.getCount(); i++) {
	                		if (queueAdapter.getItem(i).getUid().equals(resourceID)) {
	                			queueAdapter.getItem(i).setCached(true);
	                		}
	                	}
	                	
	                	// Update Browser Datastore
	                	for (int i = 0; i < browserAdapter.getCount(); i++) {
	                		if (browserAdapter.getItem(i).getUid().equals(resourceID)) {
	                			browserAdapter.getItem(i).setCached(true);
	                		}
	                	}
	                	
	                	// Update Database
	                	ServerFileData row = database.getRow(resourceID);
	                	row.setCached(true);
	                	database.addMusic(row);
	                	
	                	// Update Queue
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
	                	
	                	break;
	                case ROOT_LISTING_RETRIEVED:
	                	// Grab XML byte[]
	                	response = ((DirectoryResponse) inputMessage.obj).getResponse();
	                	parent = ((DirectoryResponse) inputMessage.obj).getParent();
	                	
	                	// Parse XML
	                	listing = new ArrayList<ServerFileData>();
	                	try {
							xpp = xmlParserFactory.newPullParser();
							xpp.setInput(new StringReader (new String(response)));
		                    int eventType = xpp.getEventType();
		                    while (eventType != XmlPullParser.END_DOCUMENT) {	 
		                    	if (eventType == XmlPullParser.START_TAG) {
		                    		if (xpp.getName().equals("musicFolder")) {
		                    			ServerFileData serverFileData = new ServerFileData();
		                    			serverFileData.setTitle(xpp.getAttributeValue(null, "name"));
		                    			serverFileData.setUid(Integer.parseInt(xpp.getAttributeValue(null, "id")));
		                    			serverFileData.setParent(parent.getUid());
		                    			serverFileData.setResourceType(ServerFileData.MUSIC_FOLDER_TYPE);
		                    			listing.add(serverFileData);
		                    		}
		                    		
		                    	}
								eventType = xpp.next();
		                    }
						} catch (Exception e) {
							throw new RuntimeException("The XML parsing mechanism has failed!", e);
						}
	                	
						// Clear out Old Data
						database.deleteChildren(parent.getUid());
						
						// Sort by Name
						Collections.sort(listing, SERVER_FILE_DATA_TITLE_COMPARATOR);
						
	                	// Update Database
						database.addMusic(listing.toArray(new ServerFileData[listing.size()]));
						
	                	// Update UI Element
						browserAdapter.clear();
						browserAdapter.addAll(listing);
						refreshBrowser();
						
						// Set Current Directory
						if (!swipeRefreshLayout.isRefreshing()) {
							// If this refresh was caused by a swipe, we didn't change directory
							currentDirectory = parent;
						}
						
						// Stop Refresh Animation (if there)
						setBrowserLoading(false);
						
						break;
						
	                case DIRECTORY_LISTING_RETRIEVED:
	                	// Grab XML byte[]
	                	response = ((DirectoryResponse) inputMessage.obj).getResponse();
	                	parent = ((DirectoryResponse) inputMessage.obj).getParent();
	                	
	                	// Parse XML
	                	listing = new ArrayList<ServerFileData>();
						try {
							xpp = xmlParserFactory.newPullParser();
							xpp.setInput(new StringReader (new String(response)));
		                    int eventType = xpp.getEventType();
		                    while (eventType != XmlPullParser.END_DOCUMENT) {
		                    	if (eventType == XmlPullParser.START_TAG) {
		                    		if (xpp.getName().equals("child")) {
		                    			if (Boolean.parseBoolean(xpp.getAttributeValue(null, "isDir"))) {
		                    				ServerFileData serverFileData = new ServerFileData();
		                    				
		                    				// Name or Title
		                    				String name = xpp.getAttributeValue(null, "name");
		                    				String title = xpp.getAttributeValue(null, "title");
		                    				if (name != null) {
		                    					serverFileData.setTitle(name);
		                    				} else if (title != null) {
		                    					serverFileData.setTitle(title);
		                    				} else {
		                    					serverFileData.setTitle("NO_NAME");
		                    				}
		                    				
			                    			serverFileData.setUid(Integer.parseInt(xpp.getAttributeValue(null, "id")));
			                    			serverFileData.setParent(Integer.parseInt(xpp.getAttributeValue(null, "parent")));
			                    			serverFileData.setResourceType(ServerFileData.DIRECTORY_TYPE);
			                    			serverFileData.setCreated(xpp.getAttributeValue(null, "created"));
			                    			listing.add(serverFileData);
		                    			} else {
		                    				ServerFileData serverFileData = new ServerFileData();
		                    				serverFileData.setUid(Integer.parseInt(xpp.getAttributeValue(null, "id")));
		                    				serverFileData.setParent(Integer.parseInt(xpp.getAttributeValue(null, "parent")));
		                    				serverFileData.setResourceType(ServerFileData.FILE_TYPE);
			                    			serverFileData.setTitle(xpp.getAttributeValue(null, "title"));
			                    			serverFileData.setAlbum(xpp.getAttributeValue(null, "album"));
			                    			serverFileData.setArtist(xpp.getAttributeValue(null, "artist"));
			                    			serverFileData.setGenre(xpp.getAttributeValue(null, "genre"));
			                    			serverFileData.setSize(Integer.parseInt(xpp.getAttributeValue(null, "size")));
			                    			serverFileData.setSuffix(xpp.getAttributeValue(null, "suffix"));
			                    			
			                    			String durationString = xpp.getAttributeValue(null, "duration");
			                    			Integer duration = 0;
			                    			try {
			                    				duration = Integer.parseInt(durationString);
			                    			} catch (NumberFormatException e) {
			                    				Log.e(SUBTAG, "Bad track duration encountered!");
			                    				break;
			                    			}
			                    			serverFileData.setDuration(duration);
			                    			
			                    			serverFileData.setBitRate(Integer.parseInt(xpp.getAttributeValue(null, "bitRate")));
			                    			serverFileData.setServerPath(xpp.getAttributeValue(null, "path"));
			                    			serverFileData.setCreated(xpp.getAttributeValue(null, "created"));
			                    			String trackNumber = xpp.getAttributeValue(null, "track");
			                    			serverFileData.setTrackNumber((trackNumber == null) ? -1 : Integer.parseInt(trackNumber));
			                    			listing.add(serverFileData);
		                    			}
		                    		}
		                    	}
								eventType = xpp.next();
		                    }
						} catch (Exception e) {
							throw new RuntimeException("The XML parsing mechanism has failed!", e);
						}
						// Clear out Old Data
						database.deleteChildren(parent.getUid());
						// Sort by Name
						Collections.sort(listing, SERVER_FILE_DATA_TITLE_COMPARATOR);
	                	// Update Database
						database.addMusic(listing.toArray(new ServerFileData[listing.size()]));
	                	// Update UI Element
						browserAdapter.clear();
						browserAdapter.addAll(listing);
						refreshBrowser();
						// Set Current Directory
						if (!swipeRefreshLayout.isRefreshing()) {
							// If this refresh was caused by a swipe, we didn't change directory
							currentDirectory = parent;
						}
						// Stop Refresh Animation (if there)
						setBrowserLoading(false);
						break;
	                case MUSIC_FOLDER_LISTING_RETRIEVED:
	                	// Grab XML byte[]
	                	response = ((DirectoryResponse) inputMessage.obj).getResponse();
	                	parent = ((DirectoryResponse) inputMessage.obj).getParent();
	                	
	                	// Parse XML
	                	listing = new ArrayList<ServerFileData>();
						try {
							xpp = xmlParserFactory.newPullParser();
							xpp.setInput(new StringReader (new String(response)));
		                    int eventType = xpp.getEventType();
		                    while (eventType != XmlPullParser.END_DOCUMENT) {	 
		                    	if (eventType == XmlPullParser.START_TAG) {
		                    		if (xpp.getName().equals("artist")) {
	                    				ServerFileData serverFileData = new ServerFileData();
		                    			serverFileData.setTitle(xpp.getAttributeValue(null, "name"));
		                    			serverFileData.setUid(Integer.parseInt(xpp.getAttributeValue(null, "id")));
		                    			serverFileData.setParent(parent.getUid());
		                    			serverFileData.setResourceType(ServerFileData.DIRECTORY_TYPE);
		                    			listing.add(serverFileData);
		                    		}
		                    	}
								eventType = xpp.next();
		                    }
						} catch (Exception e) {
							throw new RuntimeException("The XML parsing mechanism has failed!", e);
						}
						
						// Clear out Old Data
						database.deleteChildren(parent.getUid());
						
						// Sort by Name
						Collections.sort(listing, SERVER_FILE_DATA_TITLE_COMPARATOR);
						
	                	// Update Database
						database.addMusic(listing.toArray(new ServerFileData[listing.size()]));
						
	                	// Update UI Element
						browserAdapter.clear();
						browserAdapter.addAll(listing);
						refreshBrowser();
						
						// Set Current Directory
						if (!swipeRefreshLayout.isRefreshing()) {
							// If this refresh was caused by a swipe, we didn't change directory
							currentDirectory = parent;
						}
						
						// Stop Refresh Animation (if there)
						setBrowserLoading(false);
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
         * Other Initialization
         */
        try {
			this.xmlParserFactory = XmlPullParserFactory.newInstance();
		} catch (XmlPullParserException e) {
			throw new RuntimeException("The XML parsing mechanism has failed!", e);
		}
        // Setup Sound Machine with Handler for Callbacks
        SoundMachine.getInstance().setHandler(this.appRefreshHandler);
        
        // Seeking
        this.seeking = false;
        // Setup Database
        this.database = Database.getInstance(this);
        // Start UI Updater
        UIRefreshThread.start(this.appRefreshHandler, PROGRESS_REFRESH_RATE);
        
        /**
         * Download Root Folder
         */
        SubsonicServer server = SubsonicServer.getInstance(this);
        ServerFileData root = new ServerFileData();
        root.setResourceType(ServerFileData.ROOT_TYPE);
        root.setParent(ServerFileData.ROOT_UID);
        
		// Check Cache (and unlock if cached)
		List<ServerFileData> children = database.getDirectoryChildren(ServerFileData.ROOT_UID);
		Collections.sort(children, SERVER_FILE_DATA_TITLE_COMPARATOR);
		if (children != null && children.size() > 0) {
			// Set Current Directory
			currentDirectory = root;
			
			// Setup Adapter Data
			browserAdapter.clear();
			browserAdapter.addAll(children);
			
			// Refresh Browser
			refreshBrowser();
		} else {
	        server.getDirectoryListing(this, root);
		}
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
    	this.queueAdapter.advance();
    	SoundMachine.getInstance().stop();
		play(null);
		UIRefreshThread.setProgressPaused(false);
		invalidateIfCurrentIsVisible();
    }
    public void previous(View view) {
    	UIRefreshThread.setProgressPaused(true);
    	this.queueAdapter.retreat();
		SoundMachine.getInstance().stop();
		play(null);
		UIRefreshThread.setProgressPaused(false);
		invalidateIfCurrentIsVisible();
    }
    public void play(View view) {
    	ServerFileData current = this.queueAdapter.current();
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
    	this.queueAdapter.setCurrent(index);
    	SoundMachine.getInstance().stop();
    	play(null);
    	UIRefreshThread.setProgressPaused(false);
    	invalidateIfCurrentIsVisible();
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
    public static String BASE_URL_KEY = "base_url";
    public static String USER_KEY = "user";
    public static String PASSWORD_KEY = "password";
    public static String CLIENT_NAME_KEY = "client_name";
    public static String API_VERSION_KEY = "api_version";
    
    public static String DEFAULT_BASE_URL = "http://10.0.2.2:8080/subsonic/";
    public static String DEFAULT_USER = "admin";
    public static String DEFAULT_PASSWORD = "admin";
    public static String DEFAULT_CLIENT_NAME = "Subtle";
    public static String DEFAULT_API_VERSION = "1.10.2";
    
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
        inputMethodManager.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(), 0);
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
    public static final int QUEUE_DESELECTED_COLOR = android.R.color.background_dark;
    public void invalidateIfCurrentIsVisible() {
    	if (this.queueListView != null && this.queueAdapter != null) {	
    		int start = this.queueListView.getFirstVisiblePosition();
    		int end = this.queueListView.getLastVisiblePosition();
    		if ((this.queueAdapter.currentIndex() >= start && this.queueAdapter.currentIndex() <= end) || this.queueAdapter.getCount() == 0) {
    			this.queueListView.invalidateViews();
    		}
    	}
    }
	private void enqueueSong(ServerFileData song) {
		if (song.getResourceType() != ServerFileData.FILE_TYPE) {
			throw new RuntimeException("Enqueued a non-file type resourse!");
		}
		
		// Check if Cached
		boolean cached = this.database.getRow(song.getUid()).getCached();
		
		// Download if Needed
		if (!cached && !SubsonicServer.getInstance(this).isDownloading(song)) {
			SubsonicServer.getInstance(this).download(this, song);
		}	
		
		// Add to Queue
		this.queueAdapter.enqueue(song);
	}
	public void setQueueListView(ListView queueListView) {
		this.queueListView = queueListView;
	}
	
	/**
	 * Browser Specific
	 */
	public static final int BROWSER_ROW_CACHED = android.R.color.holo_blue_dark;
	public static final int BROWSER_ROW_DECACHED = android.R.color.background_dark;
	public void refreshBrowser() {
    	if (this.browserAdapter != null) {	
    		this.browserAdapter.notifyDataSetChanged();
    	}
	}
	public void setBrowserLoading(boolean loading) {
		if (loading) {
			this.swipeRefreshLayout.setRefreshing(true);
		} else {
			this.swipeRefreshLayout.setRefreshing(false);
		}
	}  
	@Override
	public void onBackPressed() {
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
			this.currentDirectory = this.database.getRow(parent);
			List<ServerFileData> children = this.database.getDirectoryChildren(this.currentDirectory.getUid());
			Collections.sort(children, SERVER_FILE_DATA_TITLE_COMPARATOR);
			
			// Setup Adapter Data
			this.browserAdapter.clear();
			this.browserAdapter.addAll(children);
			
			// Refresh Browser
			refreshBrowser();
		}
	}
	public void setBrowser(final ListView browser, final SwipeRefreshLayout swipeRefreshLayout) {
		this.browser = browser;
		this.swipeRefreshLayout = swipeRefreshLayout;
		// Setup Adapter
		this.browser.setAdapter(this.browserAdapter);
		// Setup Refresh Listener
		this.swipeRefreshLayout.setColorSchemeResources(
				android.R.color.holo_blue_dark, 
				android.R.color.holo_blue_light, 
				android.R.color.holo_green_light, 
				android.R.color.holo_green_light);
		this.swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
	        @Override
	        public void onRefresh() {
	        	swipeRefreshLayout.setRefreshing(true);
	        	if (currentDirectory == null) {
	                ServerFileData root = new ServerFileData();
	                root.setResourceType(ServerFileData.ROOT_TYPE);
	                SubsonicServer.getInstance(SubtleActivity.this).getDirectoryListing(SubtleActivity.this, root);
	        	} else {
		        	SubsonicServer.getInstance(SubtleActivity.this).getDirectoryListing(SubtleActivity.this, currentDirectory);
	        	}
	        }
	    });
		// Setup Click Listener
		this.browser.setOnItemClickListener(new OnItemClickListener(){
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				ServerFileData selectedItem = browserAdapter.getItem(position);
				SubsonicServer server = SubsonicServer.getInstance(SubtleActivity.this);
				if (selectedItem.isDirectory()) { // Directory
					// Check Cache (and unlock if cached)
					List<ServerFileData> children = database.getDirectoryChildren(selectedItem.getUid());
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
						server.getDirectoryListing(SubtleActivity.this, selectedItem);
					}
				} else { // File
					// Queue
					enqueueSong(browserAdapter.getItem(position));
				}
			}
		});
	}
	
    /**
     * Tab Listener Functions
     */
	private static final int BROWSER_FRAGMENT = 0;
	private static final int QUEUE_FRAGMENT = 1;
	private static final int SETTINGS_FRAGMENT = 2;
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
		private SubtleActivity parent;
		public BrowserFragment(SubtleActivity parent) {
			super();
			this.parent = parent;
		}
		
	    @Override
	    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	    	if (this.parent == null) {
	    		throw new RuntimeException("Parent never set on fragment!");
	    	}
	        View rootView = inflater.inflate(R.layout.browser_fragment, container, false);
	        ((SubtleActivity) this.parent).setBrowser(
	        		(ListView) rootView.findViewById(R.id.browser), 
	        		(SwipeRefreshLayout) rootView.findViewById(R.id.swipe_container));
	        return rootView;
	    }
	}
	public static class QueueFragment extends Fragment  {
		private SubtleActivity parent;
		private QueueAdapter adapter;
		
		public QueueFragment(SubtleActivity parent) {
			super();
			this.parent = parent;
			this.adapter = this.parent.queueAdapter;
		}
		
	    @Override
	    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	    	if (this.parent == null) {
	    		throw new RuntimeException("Parent never set on fragment!");
	    	}
	        View rootView = inflater.inflate(R.layout.queue_fragment, container, false);
	        final DynamicListView queueList = (DynamicListView) rootView.findViewById(R.id.queue);
	        queueList.enableDragAndDrop();
	        queueList.setOnItemLongClickListener(new OnItemLongClickListener() {
    	        @Override
    	        public boolean onItemLongClick(final AdapterView<?> viewAdapter, final View view,
    	                                       final int position, final long id) {
    	            queueList.startDragging(position);
    	            return true;
    	        }
    	    });
	        queueList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				@Override
				public void onItemClick(AdapterView<?> parent, View view,
						int position, long id) {
					((SubtleActivity) QueueFragment.this.parent).selectTrack(position);
				}
			});
	        queueList.enableSwipeToDismiss(new OnDismissCallback() {
				@Override
				public void onDismiss(final ViewGroup listView, final int[] reverseSortedPositions) {
					for (int position : reverseSortedPositions) {
						// If Current Track, Stop Playback
						if (adapter.currentIndex() == position) {
							SoundMachine.getInstance().stop();
						}

						// Delete Row in View
						adapter.remove(position);
						
						// Refresh Visible Rows
						parent.invalidateIfCurrentIsVisible();
    	            }
				}
	        });
	        queueList.setAdapter(adapter);
	        ((SubtleActivity) this.parent).setQueueListView(queueList);
	        return rootView;
	    }
	}
	public static class SettingsFragment extends Fragment {
		private SubtleActivity parent;
		private EditText username;
		private EditText password;
		private EditText serverUrl;
		private EditText clientName;
		
		public SettingsFragment(SubtleActivity parent) {
			super();
			this.parent = parent;
		}
		
	    @Override
	    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	    	if (this.parent == null) {
	    		throw new RuntimeException("Parent never set on fragment!");
	    	}
	        View rootView = inflater.inflate(R.layout.settings_fragment, container, false);
	        this.username = (EditText)  rootView.findViewById(R.id.username);	        
	        this.password = (EditText)  rootView.findViewById(R.id.password);	  
	        this.serverUrl = (EditText)  rootView.findViewById(R.id.url);	  
	        this.clientName = (EditText)  rootView.findViewById(R.id.clientName);	  
	        
	        // Load Defaults
	        this.username.setText(this.parent.preferencesGetString(SubtleActivity.USER_KEY, SubtleActivity.DEFAULT_USER));	        
	        this.password.setText(this.parent.preferencesGetString(SubtleActivity.PASSWORD_KEY, SubtleActivity.DEFAULT_PASSWORD));
	        this.serverUrl.setText(this.parent.preferencesGetString(SubtleActivity.BASE_URL_KEY, SubtleActivity.DEFAULT_BASE_URL));	  
	        this.clientName.setText(this.parent.preferencesGetString(SubtleActivity.CLIENT_NAME_KEY, SubtleActivity.DEFAULT_CLIENT_NAME));
	        
	        // Setup Listeners
			this.username.setOnFocusChangeListener(new View.OnFocusChangeListener() {
				@Override
				public void onFocusChange(View v, boolean hasFocus) {
					if (!hasFocus) {
						String input = ((EditText) v).getText().toString();
						parent.preferencesSetString(SubtleActivity.USER_KEY, input);
					}
				}
			});
			this.password.setOnFocusChangeListener(new View.OnFocusChangeListener() {
				@Override
				public void onFocusChange(View v, boolean hasFocus) {
					if (!hasFocus) {
						String input = ((EditText) v).getText().toString();
						parent.preferencesSetString(SubtleActivity.PASSWORD_KEY, input);
					}
				}
			});;
			this.serverUrl.setOnFocusChangeListener(new View.OnFocusChangeListener() {
				@Override
				public void onFocusChange(View v, boolean hasFocus) {
					if (!hasFocus) {
						String previousUrl = parent.preferencesGetString(SubtleActivity.BASE_URL_KEY, SubtleActivity.DEFAULT_BASE_URL);
						String newUrl = ((EditText) v).getText().toString();
						parent.preferencesSetString(SubtleActivity.BASE_URL_KEY, newUrl);
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
						parent.preferencesSetString(SubtleActivity.CLIENT_NAME_KEY, input);
					}
				}
			});
	        return rootView;
	    }
	}
}
