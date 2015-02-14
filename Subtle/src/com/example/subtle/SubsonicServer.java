package com.example.subtle;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.validator.routines.UrlValidator;
import org.apache.http.Header;
import org.apache.http.conn.ssl.SSLSocketFactory;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.FileAsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

public class SubsonicServer {
	/**
	 * Subsonic Server Resources and Settings
	 */	
	private DummySSLSocketFactory socketFactory;
	private AsyncHttpClient client;
	private AsyncHttpClient downloadClient;
	private SubtleActivity parentContext;
	private ConcurrentHashMap<Integer, Integer> downloading; // UID - Progress
	private static final String[] validSchemes = {"http", "https"}; // DEFAULT schemes = "http", "https"
	/**
	 * Singleton
	 */
	private static SubsonicServer instance = null;
	public synchronized static SubsonicServer getInstance(SubtleActivity context) {
		if (instance == null) {
			if (context == null) {
				throw new RuntimeException("SubsonicServer failed to initialize!");
			}
			instance = new SubsonicServer(context);
		}
		
		// Make sure Thread Pool is Active
		if (instance.client.getThreadPool().isShutdown()) {
			instance.client.setThreadPool(Seamstress.getInstance().getPool());
		}
		if (instance.downloadClient.getThreadPool().isShutdown()) {
			instance.downloadClient.setThreadPool(Seamstress.getInstance().getDownloadPool());
		}
		
		return instance;
	}
	private SubsonicServer(SubtleActivity context) {
		// Initialize Async Http Client
		this.client = new AsyncHttpClient();
		this.downloadClient = new AsyncHttpClient();
	    try {
	    	// Get Truststore
	    	KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
			trustStore.load(null, null);
			
			// Create Socket Factory
			this.socketFactory = new DummySSLSocketFactory(trustStore);
			this.socketFactory.setHostnameVerifier(DummySSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
			
			// Setup Client
			client.setSSLSocketFactory(this.socketFactory);
			client.setThreadPool(Seamstress.getInstance().getPool());
			
			// Setup Download Client
			downloadClient.setSSLSocketFactory(this.socketFactory);
			downloadClient.setThreadPool(Seamstress.getInstance().getDownloadPool());
		} catch (Exception ex) {
			throw new RuntimeException("Unable to setup SSL ignorer!");
		}
		
		// Download Manager Settings
		this.downloading = new ConcurrentHashMap<Integer, Integer>();
		this.parentContext = context;
	}
	
	/**
	 * Helpers
	 */
	private RequestParams getBasicParameters() {
		RequestParams params = new RequestParams();
		params.put("u", this.parentContext.preferencesGetString(SubtleActivity.USER_KEY, SubtleActivity.DEFAULT_USER));
		params.put("p", this.parentContext.preferencesGetString(SubtleActivity.PASSWORD_KEY, SubtleActivity.DEFAULT_PASSWORD));
		params.put("c", this.parentContext.preferencesGetString(SubtleActivity.CLIENT_NAME_KEY, SubtleActivity.DEFAULT_CLIENT_NAME));
		params.put("v", this.parentContext.preferencesGetString(SubtleActivity.API_VERSION_KEY, SubtleActivity.DEFAULT_API_VERSION));
		return params;
	}
	private String getAbsoluteURL(String relativeURL) {
		String base = this.parentContext.preferencesGetString(SubtleActivity.BASE_URL_KEY, SubtleActivity.DEFAULT_BASE_URL);
		// Find Ending Slash
		if (base.endsWith("/")) {
			base += "rest/";
		} else {
			base += "/rest/";
		}
		base += relativeURL;
		
		// Validate or Null
		UrlValidator urlValidator = new UrlValidator(validSchemes);
		if (urlValidator.isValid(base)) {
		   return base;
		} else {
		   return null;
		}		
	}
	private void showDialog(String message, Throwable e) {
		// Build A Failure Dialog
		AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this.parentContext);
		alertDialogBuilder
			.setTitle(message)
			.setMessage(e.getMessage())
			.setCancelable(false)
			.setPositiveButton("OK", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int id) {
					dialog.dismiss();
				}
			});

		// Add Dialog to Queue
		Message dialogMessage = Message.obtain();
		Handler handler = this.parentContext.appRefreshHandler;
		dialogMessage.what = SubtleActivity.DIALOG_MESSAGE;
		dialogMessage.obj = alertDialogBuilder.create();
		handler.sendMessage(dialogMessage);
	}
	
	/**
	 * Public Interface
	 */
	public void getDirectoryListing(final ServerFileData fileData) {
		int resourceType = fileData.getResourceType().intValue();
		if (resourceType == ServerFileData.ROOT_TYPE) {
			getMusicFolders();
		} else if (resourceType == ServerFileData.DIRECTORY_TYPE) {
			getMusicDirectory(fileData);
		} else if (resourceType == ServerFileData.MUSIC_FOLDER_TYPE) {
			getIndexes(fileData);
		} else {
			throw new RuntimeException("Invalid resource type encountered during attempt to retrieve directory listing!");
		}
	}
	
	private void sendRawListing(Context context, ServerFileData parent, byte[] response, DirectoryListing.ResponseType type) {
		Handler handler = ((SubtleActivity) context).appRefreshHandler;
		Message message = Message.obtain();
		message.what = SubtleActivity.LISTING_RETRIEVED;
		RawDirectoryListing directoryResponse = new RawDirectoryListing(parent, response, type);
		message.obj = directoryResponse;
		handler.sendMessage(message);
	}
	
	private void getIndexes(final ServerFileData fileData) {
		// Build URL
		String url;
		try {
			url = getAbsoluteURL("getIndexes.view");
		} catch (Exception e) {
			return;
		}
		
		// Validate
		if (url == null) {
			showDialog("Bad URL!", new Throwable("Please check the server url for issues."));
		}
		
		// Build Parameters
		RequestParams params = getBasicParameters();
		params.put("id", fileData.getUid());
		
		// GET
		client.get(url,  params, new AsyncHttpResponseHandler() {

			@Override
			public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
				// Show Dialog Box
				showDialog("Failed to retrieve music folder listing!", e);
			}

			@Override
			public void onSuccess(int statusCode, Header[] headers, byte[] response) {
				// Send Back Listing
				sendRawListing(parentContext, fileData, response, DirectoryListing.ResponseType.MUSIC_FOLDER);
			}
		});	
	}
	
	private void getMusicFolders () {
		// Build URL
		String url;
		try {
			url = getAbsoluteURL("getMusicFolders.view");
		} catch (Exception e) {
			return;
		}
		
		// Validate
		if (url == null) {
			showDialog("Bad URL!", new Throwable("Please check the server url for issues."));
			return;
		}
		
		// Build Parameters
		RequestParams params = getBasicParameters();
		
		// GET
		client.get(url, params, new AsyncHttpResponseHandler() {
			
			@Override
			public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
				// Show Dialog Box
				showDialog("Failed to retrieve directory listing!", e);
			}
			
			@Override
			public void onSuccess(int statusCode, Header[] headers, byte[] response) {
				// Send Back Listing
				ServerFileData parent = new ServerFileData();
				parent.setUid(ServerFileData.ROOT_UID);
				parent.setParent(ServerFileData.ROOT_UID);
				parent.setResourceType(ServerFileData.ROOT_TYPE);
				sendRawListing(parentContext, parent, response, DirectoryListing.ResponseType.ROOT_LISTING);
			}
			
		});
	}
	
	private void getMusicDirectory(final ServerFileData fileData) {
		// Build URL
		String url;
		try {
			url = getAbsoluteURL("getMusicDirectory.view");
		} catch (Exception e) {
			return;
		}
		
		// Validate
		if (url == null) {
			showDialog("Bad URL!", new Throwable("Please check the server url for issues."));
			return;
		}
		
		// Build Parameters
		RequestParams params = getBasicParameters();
		params.put("id", fileData.getUid());
		
		// GET
		client.get(url,  params, new AsyncHttpResponseHandler() {

			@Override
			public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
				// Show Dialog Box
				showDialog("Failed to retrieve directory listing!", e);
			}

			@Override
			public void onSuccess(int statusCode, Header[] headers, byte[] response) {
				// Send Back Listing
				sendRawListing(parentContext, fileData, response, DirectoryListing.ResponseType.DIRECTORY_LISTING);
			}
		});
	}
	
	
	public void download(final Context context, final ServerFileData fileData) {
		// Build URL
		String url;
		try {
			url = getAbsoluteURL("download.view");
		} catch (Exception e) {
			return;
		}
		final int uid = fileData.getUid();
		
		// Validate URL
		if (url == null) {
			showDialog("Bad URL!", new Throwable("Please check the server url for issues."));
			return;
		}
		
		// Validate File
		File outputFile = new File(fileData.getAbsolutePath());
		if (fileExists(outputFile.getAbsolutePath())) {
			deleteFile(outputFile.getAbsolutePath());
		}
		Log.v("SUBTAG", "Downloading to "+outputFile);
		
		// Build Parameters
		RequestParams params = getBasicParameters();
		params.put("id", uid);
		
		// GET
		downloadClient.get(url,  params, new FileAsyncHttpResponseHandler(outputFile) {
			@Override
			public void onStart() {
				downloading.put(uid, 0);
			}
			
			@Override
			public void onFailure(int statusCode, Header[] headers, Throwable e, File outputFile) {
				// Show Dialog Box
				showDialog("Failed to download file!", e);
			}

			@Override
			public void onSuccess(int statusCode, Header[] headers, File outputFile) {
				Handler handler = ((SubtleActivity) context).appRefreshHandler;
				Message message = Message.obtain();
				message.what = SubtleActivity.DOWNLOAD_COMPLETE;
				message.arg1 = uid;
				handler.sendMessage(message);
			}
			
			@Override
			public void onProgress(int bytesWritten, int totalSize) {
				downloading.put(uid, (int)(((double)bytesWritten / totalSize) * 100));
			}
			
			@Override
			public void onFinish() {
				downloading.remove(uid);
			}
		});		
	}	
	
	/**
	 * Accessors
	 */
	public Set<Entry<Integer, Integer>> getDownloadEntrySet() {
		return this.downloading.entrySet();
	}
	public boolean isDownloading(ServerFileData song) {
		return this.downloading.keySet().contains(song.getUid());
	}
	
	/**
	 * Helpers
	 */
	private boolean fileExists(String filename){
	    File file = new File(filename);
	    return file.exists();
	}
	private boolean deleteFile(String filename){
	    File file = new File(filename);
	    return file.delete();
	}
	
	/**
	 * Dummy SSL Handler
	 */
	public static class DummySSLSocketFactory extends SSLSocketFactory {
	    SSLContext sslContext = SSLContext.getInstance("TLS");
	    public DummySSLSocketFactory(KeyStore truststore) throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException, UnrecoverableKeyException {
	        super(truststore);
	        TrustManager tm = new X509TrustManager() {
	            public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
	            }

	            public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
	            }

	            public X509Certificate[] getAcceptedIssuers() {
	                return null;
	            }
	        };
	        sslContext.init(null, new TrustManager[] { tm }, null);
	    }
	    @Override
	    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException, UnknownHostException {
	        return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
	    }
	    @Override
	    public Socket createSocket() throws IOException {
	        return sslContext.getSocketFactory().createSocket();
	    }
	}
}
