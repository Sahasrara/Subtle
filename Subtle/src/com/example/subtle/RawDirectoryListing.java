package com.example.subtle;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * This has raw response data, unparsed. 
 */
public class RawDirectoryListing implements DirectoryListing {
	private final ServerFileData parent;
	private final byte[] response;
	private final ResponseType type;
	
	public RawDirectoryListing(ServerFileData parent, byte[] response, ResponseType type) {
		this.parent = parent;
		this.response = response;
		this.type = type;
	}

	public ServerFileData getParent() {
		return this.parent;
	}
	
	public void parseListing(final SubtleActivity subtleActivity) {        
		switch (type) {
	        case ROOT_LISTING:
	        	// Parse XML
	        	Seamstress.getInstance().execute(new Thread() {
	        		public void run() {
	        	    	XmlPullParserFactory xmlParserFactory;
	        	    	XmlPullParser xpp;
	        	    	List<ServerFileData> listing = new ArrayList<ServerFileData>();
	        	        try {
	        				xmlParserFactory = XmlPullParserFactory.newInstance();
	        			} catch (XmlPullParserException e) {
	        				throw new RuntimeException("The XML parsing mechanism has failed!", e);
	        			}
	        			
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
	    	        	
	    	        	// Send parsed listing
	    				sendParsedListing(listing, subtleActivity);
	        		}
	        	});
	        	
	            break;
	                
	        case DIRECTORY_LISTING:
            	// Parse XML
	        	Seamstress.getInstance().execute(new Thread() {
	        		public void run() {
	        	    	XmlPullParserFactory xmlParserFactory;
	        	    	XmlPullParser xpp;
	        	    	List<ServerFileData> listing = new ArrayList<ServerFileData>();
	        	        try {
	        				xmlParserFactory = XmlPullParserFactory.newInstance();
	        			} catch (XmlPullParserException e) {
	        				throw new RuntimeException("The XML parsing mechanism has failed!", e);
	        			}
	        			
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
	    	                    				Log.e("Subtle", "Bad track duration encountered!");
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

	    	        	// Send parsed listing
	    				sendParsedListing(listing, subtleActivity);
	        		}
	        	});

				
	            break;
	                     
	        case MUSIC_FOLDER:
	        	// Parse XML
	        	Seamstress.getInstance().execute(new Thread() {
	        		public void run() {
	        	    	XmlPullParserFactory xmlParserFactory;
	        	    	XmlPullParser xpp;
	        	    	List<ServerFileData> listing = new ArrayList<ServerFileData>();
	        	        try {
	        				xmlParserFactory = XmlPullParserFactory.newInstance();
	        			} catch (XmlPullParserException e) {
	        				throw new RuntimeException("The XML parsing mechanism has failed!", e);
	        			}
	        			
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
	    				
	    	        	// Send parsed listing
	    				sendParsedListing(listing, subtleActivity);
	        		}
	        	});
	        	
	            break;
	                    
	        default:
	            throw new RuntimeException("Unknown response type!");
		}
	}
	
	private void sendParsedListing(List<ServerFileData> listing, SubtleActivity subtleActivity) {
		Handler handler = subtleActivity.appRefreshHandler;
		Message message = Message.obtain();
		message.what = SubtleActivity.PARSED_LISTING;
		message.obj = new ParsedDirectoryListing(parent, type, listing);
		handler.sendMessage(message);
	}

	public byte[] getResponse() {
		return this.response;
	}
}
