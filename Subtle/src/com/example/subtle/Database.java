package com.example.subtle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class Database extends SQLiteOpenHelper {
	
	// Globals
	private static int DATABASE_VERSION = 19;
	private static final String DATABASE_NAME = "SubsonicDB";
	private static final String MUSIC_TABLE = "music";
	private static final String UID = "uid";
	private static final String TITLE = "title";
	private static final String ALBUM = "album";
	private static final String ARTIST = "artist";
	private static final String GENRE = "genre";
	private static final String SIZE = "size";
	private static final String SUFFIX = "suffix";
	private static final String DURATION = "duration";
	private static final String BIT_RATE = "bit_rate";
	private static final String SERVER_PATH = "server_path";
	private static final String CREATED = "created";
	private static final String RESOURCE_TYPE = "resource_type";
	private static final String PARENT = "parent";
	private static final String CACHED = "cached";
	private static final String TRACK_NUMBER = "track_number";
	private static final String[] COLUMNS = {
		UID, TITLE, ALBUM, ARTIST, GENRE, SIZE, 
		SUFFIX, DURATION, BIT_RATE, SERVER_PATH, 
		CREATED, RESOURCE_TYPE, PARENT, 
		CACHED, TRACK_NUMBER};
	
	
	/**
	 * Singleton
	 */
	private static Database instance = null;
	public synchronized static Database getInstance(Context context) {
		if(instance == null) {
			instance = new Database(context);
		}
		return instance;
	}
	private Database(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		// Create Music Table Command
		String CREATE_MUSIC_TABLE = String.format(""
				+ "CREATE TABLE %s ("
				+ "%s INTEGER PRIMARY KEY AUTOINCREMENT, "
				+ "%s TEXT, %s TEXT, %s TEXT, %s TEXT, "
				+ "%s INTEGER, %s TEXT, %s INTEGER, "
				+ "%s INTEGER, %s TEXT, %s TEXT, "
				+ "%s INTEGER, %s TEXT, %s INTEGER, %s INTEGER )", 
				MUSIC_TABLE,
				UID,
				TITLE,
				ALBUM,
				ARTIST,
				GENRE,
				SIZE,
				SUFFIX,
				DURATION,
				BIT_RATE,
				SERVER_PATH,
				CREATED,
				RESOURCE_TYPE,
				PARENT,
				CACHED,
				TRACK_NUMBER);
		
		// Create Table
		db.execSQL(CREATE_MUSIC_TABLE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop Older Database
        db.execSQL("DROP TABLE IF EXISTS music");
 
        // Create Fresh Database
        this.onCreate(db);
        
        // Clear Cache
		File cache = new File(SubtleActivity.CURRENT_CACHE_LOCATION.getAbsolutePath());    
		if(cache.isDirectory()){  
			String[] cachedSongs = cache.list();  
		    for (int i=0; i < cachedSongs.length; i++) {  
		        File song = new File(cache, cachedSongs[i]);   
		        song.delete();  
		    }  
        } 
	}
	
	/**
	 * Public Interface
	 */
	public static void invalidate() {
		Database.DATABASE_VERSION++;
	}
	
	public ServerFileData getRow(Integer id) {
		// Root is Special Case
		if (id == ServerFileData.ROOT_UID) {
			ServerFileData root = new ServerFileData();
			root.setUid(ServerFileData.ROOT_UID);
			root.setParent(ServerFileData.ROOT_UID);
			root.setResourceType(ServerFileData.ROOT_TYPE);
			return root;
		}
		
		// Get Reference to DB
		SQLiteDatabase db = this.getReadableDatabase();	
		
		// Build Query
		Cursor cursor = db.query(
				MUSIC_TABLE, // Table
				COLUMNS,
				UID + " = ?", 
				new String[] { id.toString() }, 
				null, 
				null, 
				null, 
				null
		);
		
		// Generate Result
		ServerFileData serverFileData = null;
		if (cursor.moveToFirst()) {
			serverFileData = new ServerFileData();
			serverFileData.setUid(cursor.getInt(0));
			serverFileData.setTitle(cursor.getString(1));
			serverFileData.setAlbum(cursor.getString(2));
			serverFileData.setArtist(cursor.getString(3));
			serverFileData.setGenre(cursor.getString(4));
			serverFileData.setSize(cursor.getInt(5));
			serverFileData.setSuffix(cursor.getString(6));
			serverFileData.setDuration(cursor.getInt(7));
			serverFileData.setBitRate(cursor.getInt(8));
			serverFileData.setServerPath(cursor.getString(9));
			serverFileData.setCreated(cursor.getString(10));
			serverFileData.setResourceType(cursor.getInt(11));
			serverFileData.setParent(cursor.getInt(12));
			serverFileData.setCached(cursor.getInt(13) == 1 ? true : false);
			serverFileData.setTrackNumber(cursor.getInt(14));
		} else {
			throw new RuntimeException(String.format("No parent found for a child(%d), how sad!", id));
		}
		
		return serverFileData;
	}
	
	public List<ServerFileData> getDirectoryChildren(Integer id) {
		// Get Reference to DB
		SQLiteDatabase db = this.getReadableDatabase();	
		
		// Build Query
		Cursor cursor = db.query(
				MUSIC_TABLE, // Table
				COLUMNS,
				PARENT + " = ?", 
				new String[] { id.toString() }, 
				null, 
				null, 
				UID + " ASC", 
				null
		);
		
		// Generate Results
		ArrayList<ServerFileData> serverFileDatas = new ArrayList<ServerFileData>();
		ServerFileData serverFileData = null;
		if (cursor.moveToFirst()) {
			do {
				serverFileData = new ServerFileData();
				serverFileData.setUid(cursor.getInt(0));
				serverFileData.setTitle(cursor.getString(1));
				serverFileData.setAlbum(cursor.getString(2));
				serverFileData.setArtist(cursor.getString(3));
				serverFileData.setGenre(cursor.getString(4));
				serverFileData.setSize(cursor.getInt(5));
				serverFileData.setSuffix(cursor.getString(6));
				serverFileData.setDuration(cursor.getInt(7));
				serverFileData.setBitRate(cursor.getInt(8));
				serverFileData.setServerPath(cursor.getString(9));
				serverFileData.setCreated(cursor.getString(10));
				serverFileData.setResourceType(cursor.getInt(11));
				serverFileData.setParent(cursor.getInt(12));
				serverFileData.setCached(cursor.getInt(13) == 1 ? true : false);
				serverFileData.setTrackNumber(cursor.getInt(14));
				serverFileDatas.add(serverFileData);				
			} while (cursor.moveToNext());
		}
		
		return serverFileDatas;
	}
	
	public void addMusic(ServerFileData... files) {
		// Get Reference to DB
		SQLiteDatabase db = this.getWritableDatabase();
		
		// Insert All Values
		for (ServerFileData file : files) {
			// Create Values
			ContentValues values = populateContent(file);
			
			// Insert
			db.insertWithOnConflict(MUSIC_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE);
		}

		// Close
		db.close();
	}
	public List<ServerFileData> getMusic(Integer... ids) {
		// Sanity Check
		if (ids.length <= 0) {
			throw new RuntimeException("Cannot load, no ids!");
		}
		
		// Get Reference to DB
		SQLiteDatabase db = this.getReadableDatabase();
		
		// Build In List
		String[] stringIds = new String[ids.length];
		for (int i = 0; i < ids.length; i++) {
			stringIds[i] = ids[i].toString();
		}
		
		// Build Query
		Cursor cursor = db.query(
				MUSIC_TABLE, // Table
				COLUMNS,
				UID + " = ?", 
				stringIds, 
				null, 
				null, 
				UID + " DESC", 
				null
		);
		
		// Generate Results
		ArrayList<ServerFileData> serverFileDatas = new ArrayList<ServerFileData>();
		ServerFileData serverFileData = null;
		if (cursor.moveToFirst()) {
			do {
				serverFileData = new ServerFileData();
				serverFileData.setUid(cursor.getInt(0));
				serverFileData.setTitle(cursor.getString(1));
				serverFileData.setAlbum(cursor.getString(2));
				serverFileData.setArtist(cursor.getString(3));
				serverFileData.setGenre(cursor.getString(4));
				serverFileData.setSize(cursor.getInt(5));
				serverFileData.setSuffix(cursor.getString(6));
				serverFileData.setDuration(cursor.getInt(7));
				serverFileData.setBitRate(cursor.getInt(8));
				serverFileData.setServerPath(cursor.getString(9));
				serverFileData.setCreated(cursor.getString(10));
				serverFileData.setResourceType(cursor.getInt(11));
				serverFileData.setParent(cursor.getInt(12));
				serverFileData.setCached(cursor.getInt(13) == 1 ? true : false);
				serverFileData.setTrackNumber(cursor.getInt(14));
				serverFileDatas.add(serverFileData);				
			} while (cursor.moveToNext());
		}
		
		return serverFileDatas;
	}
	public void deleteMusic(Integer... ids) {
		// Sanity Check
		if (ids.length <= 0) {
			throw new RuntimeException("Cannot load negative or no ids!");
		}
		
		// Get Reference to DB
		SQLiteDatabase db = this.getReadableDatabase();
		
		// Build In List
		String[] stringIds = new String[ids.length];
		for (int i = 0; i < ids.length; i++) {
			stringIds[i] = ids[i].toString();
		}
		
		// Insert All Values
		db.delete(
				MUSIC_TABLE, 
				UID + " in ?",
				stringIds
		);

		// Close
		db.close();
	}
	public void deleteChildren(Integer id) {
		// Get Reference to DB
		SQLiteDatabase db = this.getReadableDatabase();
		
		// Build Query
		db.delete(
				MUSIC_TABLE, // Table
				PARENT + " = ?", 
				new String[] { id.toString() }
		);
		
		// Close
		db.close();
	}
	
	/**
	 * Helpers
	 */
	private ContentValues populateContent(ServerFileData file) {
		ContentValues values = new ContentValues();
		values.put(UID, file.getUid());
		values.put(TITLE, file.getTitle());
		values.put(ALBUM, file.getAlbum());
		values.put(ARTIST, file.getArtist());
		values.put(GENRE, file.getGenre());
		values.put(SIZE, file.getSize());
		values.put(SUFFIX, file.getSuffix());
		values.put(DURATION, file.getDuration());
		values.put(BIT_RATE, file.getBitRate());
		values.put(SERVER_PATH, file.getServerPath());
		values.put(CREATED, file.getCreated());
		values.put(RESOURCE_TYPE, file.getResourceType());
		values.put(PARENT, file.getParent());
		values.put(CACHED, file.getCached());
		values.put(TRACK_NUMBER, file.getTrackNumber());
		return values;
	}
}
