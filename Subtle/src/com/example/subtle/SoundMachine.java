package com.example.subtle;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class SoundMachine {	
	/**
	 * Global Settings
	 */
	private final static int TRACK_BUFFER_SIZE = 16384; // Bytes
	private final static int SAMPLE_RATE = 44100; // Samples/Second
	
	/**
	 * Native Methods
	 */
	private native void fillBuffer(String path, short[] buffer, int myID);
	private native void convertToWave(String fromPath, String toPath);
	
	/**
	 * Callbacks
	 */
	private static final int SIG_STOP = 0;
	private static final int SIG_PLAY = 1;
	private static final int SIG_PAUSE = 2;
	private static final int SEEK_STATUS_NOT_SEEKING = -1;
	private static final int SEEK_STATUS_FINISHED_SEEKING = -2;
	private int largestJNIPlaybackCommand = 2;
	private void fillBufferCallback(int currentPosition) {	
		// Reset Seek If Needed
		if (currentPosition == SEEK_STATUS_FINISHED_SEEKING) {
			currentPosition = this.seekTo;
			this.seekTo = SEEK_STATUS_NOT_SEEKING;
		}  
		
		// Control Command Callbacks
		if (this.JNIPlaybackCommand == SIG_PLAY) {
			if (currentPosition >= (this.currentTrack.getDuration()*1000)) { // Playback Complete, Restart and Pause
				// Load Last Buffer
				this.audioTrack.write(this.cBuffer, 0, this.cBuffer.length);
								
				// Notify Complete
				notifyPlaybackComplete();
			} else { // Keep Playing
				if (this.audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
					this.audioTrack.play();
				}
				this.audioTrack.write(this.cBuffer, 0, this.cBuffer.length);
			}
		} else if (this.JNIPlaybackCommand == SIG_PAUSE) {
			if (this.audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PAUSED) {
				this.audioTrack.pause();
			}
		} else if (this.JNIPlaybackCommand == SIG_STOP) {
			// Stop Audio Track (will play last buffer)
			if (this.audioTrack.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) {
				this.audioTrack.stop();
			}
			
			// Set Current Position to Beginning
			currentPosition = 0;
		}
		
		// Set Current Position
		this.audioTrackCurrentPostion = currentPosition;
	}
		
	/**
	 * Singleton
	 */
	private static SoundMachine instance = null;
	public synchronized static SoundMachine getInstance() {
		if(instance == null) {
			instance = new SoundMachine();
		}
		return instance;
	}

	private SoundMachine() {
		this.audioTrack = new AudioTrack(
				AudioManager.STREAM_MUSIC, 
				SAMPLE_RATE, 
				AudioFormat.CHANNEL_OUT_STEREO,
				AudioFormat.ENCODING_PCM_16BIT,
				TRACK_BUFFER_SIZE,
				AudioTrack.MODE_STREAM
		);
		this.currentPlayer = null;
		this.cBuffer = new short[ TRACK_BUFFER_SIZE ];
		this.mediaPlayer = new MediaPlayer();
		this.mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		this.mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
			@Override
			public void onCompletion(MediaPlayer mp) {
				// Notify Complete
				notifyPlaybackComplete();
			}
		});
		this.setStopped();
	}
	
	/**
	 * Sound Machine Resources
	 */
	private MediaPlayer mediaPlayer;
	private AudioTrack audioTrack;
	private short[] cBuffer;
	private Object currentPlayer;
	private ServerFileData currentTrack;
	private int audioTrackCurrentPostion;
	private Handler callbackHandler;
	/**
	 * 0 - stopped
	 * 1 - playing
	 * 2 - paused
	 */
	private Byte state;

	/**
	 * Main Control
	 * @throws IOException 
	 * @throws IllegalStateException 
	 * @throws SecurityException 
	 * @throws IllegalArgumentException 
	 */
	public boolean setTrack(ServerFileData track) throws IllegalArgumentException, SecurityException, IllegalStateException, IOException {
		if (!(new File(track.getAbsolutePath())).exists()){
			Log.v("SUBTAG", "Tried to play file that doesn't exist!");
			return false;
		}
		
		// Set Current Track
		this.currentTrack = track;
		
		// Set Current Player
		if (this.currentTrack.getSuffix().equals(ServerFileData.SPC_SUFFIX)) {
			// Stop Playback
			this.audioTrack.stop();
			
			// Increment Current Thread ID
			this.activeThreadID++;
			
			// Set Seek To
			this.seekTo = SEEK_STATUS_NOT_SEEKING;
			
			// Set Current Player
			this.currentPlayer = this.audioTrack;
			
			// Clear Sound Buffer
			Arrays.fill(this.cBuffer, (short) 0);
			this.audioTrack.write(this.cBuffer, 0, this.cBuffer.length);
			
			// Start Paused Buffering
			JNIPlaybackCommand = SIG_PAUSE;
			
			// Queue Track Buffering For Processing
			Seamstress.getInstance().execute(
				new BufferMachine(
					track.getAbsolutePath(), 
					this.cBuffer, 
					this.audioTrack,
					this.activeThreadID
				)
			);
		} else {
			this.currentPlayer = this.mediaPlayer;
			this.mediaPlayer.reset();
			this.mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			this.mediaPlayer.setDataSource(this.currentTrack.getAbsolutePath());
			this.mediaPlayer.prepare();
		}
		
		return true;
	}
	public void play() {
		if (!isPlaying()) {
			if (this.currentPlayer instanceof AudioTrack) {
				this.JNIPlaybackCommand = SIG_PLAY;
			} else {
				this.mediaPlayer.start();  
			}
			setPlaying();
		}
	}
	public void pause() {
		if (!isPaused()) {
			if (this.currentPlayer instanceof AudioTrack) {
				this.JNIPlaybackCommand = SIG_PAUSE;
			} else {
				this.mediaPlayer.pause(); 
			}
			setPaused();
		}
	}
	public void stop() {
		if (!isStopped()) {
			if (this.currentPlayer instanceof AudioTrack) {
				this.JNIPlaybackCommand = SIG_STOP;
				this.audioTrackCurrentPostion = 0;
			} else {
				this.mediaPlayer.stop();
			}
			setStopped();
		}
	}
	
	public void seek(int msec) {
		// If We Are Seekable
		if ((this.currentPlayer instanceof MediaPlayer || this.currentPlayer instanceof AudioTrack) &&
				this.currentTrack != null) {
			if (this.currentPlayer instanceof AudioTrack) {	
				// Seek
				this.seekTo = msec;
			} else if (this.currentPlayer instanceof MediaPlayer) {
				this.mediaPlayer.seekTo(msec);
			}
		}
	}
	public int getProgress() {
		int progress = 0;
		if (!isStopped()) {
			if (this.currentPlayer instanceof MediaPlayer && this.currentTrack != null) {
				progress = (int) (((double)(this.mediaPlayer.getCurrentPosition()/1000) / this.currentTrack.getDuration()) * 100);
			} else if (this.currentPlayer instanceof AudioTrack && this.currentTrack != null) {
				progress =  (int) (((double)(this.audioTrackCurrentPostion/1000) / this.currentTrack.getDuration()) * 100);
			}	
		}
		return progress;
	}
	public int percentToTime(int percent) {
		float decPercent = (float) (percent * .01);
		if (this.currentTrack != null) {
			return (int)(this.currentTrack.getDuration() * decPercent);
		} else {
			return 0;
		}
	}
	
	/**
	 * State Functions
	 */
	public static final int STOPPED = 0;
	public static final int PLAYING = 1;
	public static final int PAUSED = 2;
	public boolean isStopped() {
		synchronized (this.state){
			return this.state == STOPPED;
		}
	}
	public boolean isPlaying() {
		synchronized (this.state){
			return this.state == PLAYING;
		}
	}
	public boolean isPaused() {
		synchronized (this.state){
			return this.state == PAUSED;
		}
	}
	public void setStopped() {
		if (this.state == null) {
			this.state = STOPPED;
		}
		synchronized (this.state){
			this.state = STOPPED;
		}
	}
	public void setPlaying() {
		if (this.state == null) {
			this.state = PLAYING;
		}
		synchronized (this.state){
			this.state = PLAYING;
		}
	}
	public void setPaused() {
		if (this.state == null) {
			this.state = PAUSED;
		}
		synchronized (this.state){
			this.state = PAUSED;
		}
	}
	
	/**
	 * UI Handler
	 */
	public void setHandler(Handler handler) {
		this.callbackHandler = handler;
	}
	public void notifyPlaybackComplete() {
		stop();
		
		if (this.callbackHandler != null) {
			Message playbackComplete = Message.obtain();
			playbackComplete.what = SubtleActivity.PLAYBACK_COMPLETE;
			this.callbackHandler.sendMessage(playbackComplete);
		}
	}
	
	/**
	 * Runnables
	 */	
	class BufferMachine implements Runnable {
		/**
		 * Thread Resources
		 */
		private short[] buffer;
		private String filePath;
		private AudioTrack at;
		private int myID;
		
		/**
		 * Runnable Constructor
		 */
		BufferMachine(String path, short[] buffer, AudioTrack at, int myID) { 
			this.filePath = path;
			this.buffer = buffer;
			this.at = at;
			this.myID = myID;
		}
		
		public void run() {
			fillBuffer(this.filePath, this.buffer, this.myID);
			at.stop();
		}
	}
	
	/**
	 * Shutdown
	 */
	public void shutdown() {
		// Hard Stop
		this.stop();
		
		// Stop C Thread
		this.activeThreadID++;
		
		// Kill Media Player
		if (this.mediaPlayer != null) {
			this.mediaPlayer.release();
		}
		
		// Kill Audio Track
		if (this.audioTrack != null) {
			this.audioTrack.release();
		}
	}
	
	/**
	 * JNIPlaybackCommand:
	 * 0 - Stop
	 * 1 - Play
	 * 2 - Pause
	 * >2 - Seek to Millisecond
	 */
	private int JNIPlaybackCommand;
	private int activeThreadID;
	private int seekTo;
	
	/**
	 * JNI Setup
	 */
    static {
        System.loadLibrary("snes");
    }
}
