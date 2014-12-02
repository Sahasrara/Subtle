package com.example.subtle;

import java.io.File;

public class ServerFileData {
	// Root UID
	public static final int ROOT_UID = -1;
	// File Types
	public static final int ROOT_TYPE = -1;
	public static final int FILE_TYPE = 0;
	public static final int DIRECTORY_TYPE = 1;
	public static final int MUSIC_FOLDER_TYPE = 2;
	
	// Suffixes
	public final static String SPC_SUFFIX = "spc";
	
	private Integer resourceType; // 0 = file, 1 = directory 2 = music folder
	private Integer parent;
	private Integer uid;
	private String title; // 0 = track title, 1 = directory name, 2 = music folder name
	private String album;
	private String artist;
	private String genre;
	private Integer size;
	private String suffix;
	private Integer duration;
	private Integer bitRate;
	private String serverPath;
	private String created;
	private Boolean cached;
	private Integer trackNumber;

	public Integer getUid() {
		if (uid == null) {
			throw new RuntimeException("UID is null!");
		}
		return Integer.valueOf(uid);
	}
	public void setUid(Integer uid) {
		this.uid = Integer.valueOf(uid);
	}
	public String getTitle() {
		if (title == null) {
			return "";
		}
		return new String(title);
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public String getAlbum() {
		if (album == null) {
			return "";
		}
		return new String(album);
	}
	public void setAlbum(String album) {
		this.album = album;
	}
	public String getArtist() {
		if (artist == null) {
			return "";
		}
		return new String(artist);
	}
	public void setArtist(String artist) {
		this.artist = artist;
	}
	public String getGenre() {
		if (genre == null) {
			return "";
		}
		return new String(genre);
	}
	public void setGenre(String genre) {
		this.genre = genre;
	}
	public Integer getSize() {
		if (size == null) {
			return 0;
		}
		return Integer.valueOf(size);
	}
	public void setSize(Integer size) {
		this.size = Integer.valueOf(size);
	}
	public String getSuffix() {
		if (suffix == null) {
			return "";
		}
		return new String(suffix);
	}
	public void setSuffix(String suffix) {
		this.suffix = suffix;
	}
	public Integer getDuration() {
		if (duration == null) {
			return 0;
		}
		return Integer.valueOf(duration);
	}
	public void setDuration(Integer duration) {
		this.duration = Integer.valueOf(duration);
	}
	public Integer getBitRate() {
		if (bitRate == null) {
			return 0;
		}
		return Integer.valueOf(bitRate);
	}
	public void setBitRate(Integer bitRate) {
		this.bitRate = Integer.valueOf(bitRate);
	}
	public String getServerPath() {
		if (serverPath == null) {
			return "";
		}
		return new String(serverPath);
	}
	public void setServerPath(String serverPath) {
		this.serverPath = serverPath;
	}
	public String getCreated() {
		if (created == null) {
			return "";
		}
		return new String(created);
	}
	public void setCreated(String created) {
		this.created = created;
	}
	public Integer getParent() {
		if (parent == null) {
			throw new RuntimeException("Parent is not set!");
		}
		return Integer.valueOf(parent);
	}
	public void setParent(Integer parent) {
		this.parent = Integer.valueOf(parent);
	}
	public Boolean getCached() {
		if (cached == null) {
			return false;
		}
		return cached;
	}
	public void setCached(Boolean cached) {
		this.cached = cached;
	}
	public Integer getResourceType() {
		if (resourceType == null) {
			throw new RuntimeException("Resource type was never set!");
		}
		return Integer.valueOf(resourceType);
	}
	public void setResourceType(Integer resourceType) {
		if (resourceType.intValue() > 2 || resourceType.intValue() < -1) {
			throw new RuntimeException(String.format("Unknown resource type %d!", resourceType));
		}
		this.resourceType = Integer.valueOf(resourceType);
	}
	public Integer getTrackNumber() {
		if (trackNumber == null) {
			return -1;
		}
		return Integer.valueOf(trackNumber);
	}
	public void setTrackNumber(Integer trackNumber) {
		this.trackNumber = Integer.valueOf(trackNumber);
	}
	public ServerFileData copy() {
		ServerFileData copy = new ServerFileData();
		copy.resourceType = this.resourceType;
		copy.parent = this.parent;
		copy.uid = this.uid;
		copy.title = this.title;
		copy.album = this.album;
		copy.artist = this.artist;
		copy.genre = this.genre;
		copy.size = this.size;
		copy.suffix = this.suffix;
		copy.duration = this.duration;
		copy.bitRate = this.bitRate;
		copy.serverPath = this.serverPath;
		copy.created = this.created;
		copy.cached = this.cached;
		copy.trackNumber = this.trackNumber;
		return copy;
	}
	
	/**
	 * Helpers
	 */
	public boolean isDirectory() {
		if (this.resourceType == DIRECTORY_TYPE || 
				this.resourceType == MUSIC_FOLDER_TYPE) {
			return true;
		}
		return false;
	}
	public String getAbsolutePath() {
		return (new File(SubtleActivity.CURRENT_CACHE_LOCATION.getAbsolutePath(), this.getUid().toString())).getAbsolutePath();	
	}
}
