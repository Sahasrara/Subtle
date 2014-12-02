#include "gme/gme.h"

#include "Wave_Writer.h" /* wave_ functions for writing sound file */
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <jni.h>
#include <unistd.h>

void handle_error( const char* str );
void convert(const char*, const char *);

/**
 * Globals
 */
long sample_rate = 44100; /* number of samples per second */
int track = 0; /* index of track to play (0 = first) */


void
Java_com_example_subtle_SoundMachine_fillBuffer( JNIEnv* env, jobject callingObject, jstring filePath, jshortArray bufferArray, jint myID )
{
	/* Grab File Path */
	const char *file_path = (*env)->GetStringUTFChars(env, filePath, 0);

	/* Get Class Information */
	jclass thisClass = (*env)->GetObjectClass(env, callingObject);

	/* Open music file in new emulator */
	Music_Emu* emu;
	handle_error( gme_open_file( file_path, &emu, sample_rate ) );

	/* Get Track Info */
	gme_info_t* track_info = NULL;
	handle_error( gme_track_info( emu, &track_info, track ) );

	/* Start track */
	handle_error( gme_start_track( emu, track ) );

	/* Start Filling Buffer */
	jshort current_state = 0;
	jfieldID fid_playback_command = (*env)->GetFieldID(env, thisClass, "JNIPlaybackCommand", "I");
	jfieldID fid_active_thread = (*env)->GetFieldID(env, thisClass, "activeThreadID", "I");
	jfieldID fid_seek_to = (*env)->GetFieldID(env, thisClass, "seekTo", "I");
	jmethodID mid_write = (*env)->GetMethodID(env, thisClass, "fillBufferCallback", "(I)V");
	jsize buffer_size = (*env)->GetArrayLength( env, bufferArray );
	jshort *buffer_pointer;
	jint command;
	jint currentPosition;
	jint activeID;
	jint seekTo;
	while ( 1 )
	{
		/**
		 * Read Control Commands
		 */
		/* Get Control Command */
		command = (*env)->GetIntField(env, callingObject, fid_playback_command);

		/* Get Active Thread ID */
		activeID = (*env)->GetIntField(env, callingObject, fid_active_thread);

		/* Get Seek To */
		seekTo = (*env)->GetIntField(env, callingObject, fid_seek_to);

		/**
		 * Respond to Control Commands
		 */
		if ( activeID != myID ) { // Stop
			/* Exit */
			break;
		} else if (seekTo != -1) {
			/* Seek */
			handle_error( gme_seek ( emu, seekTo ) );

			/* Report Seek */
			(*env)->CallVoidMethod(env, callingObject, mid_write, -2); // -2 == Done Seeking
		} else if ( command == 1 ) { // Play
			/* If We Finish, Kill Thread */
			if (gme_tell( emu ) >= track_info->play_length) {
				/* Write Buffer */
				(*env)->CallVoidMethod(env, callingObject, mid_write, track_info->play_length); // Finished Track

				/* Exit */
				break;
			}

			/* Get Java Buffer */
			buffer_pointer = (*env)->GetShortArrayElements( env, bufferArray, NULL );

			/* Fill sample buffer */
			handle_error( gme_play( emu, buffer_size, buffer_pointer ) );

			/* Release Java Buffer */
			(*env)->ReleaseShortArrayElements( env, bufferArray, buffer_pointer, 0 );

			/* Get Current Position */
			currentPosition = gme_tell( emu );

			/* Write Buffer */
			(*env)->CallVoidMethod(env, callingObject, mid_write, currentPosition);
		} else if ( command == 2 ) { // Pause
			/* Get Current Position */
			currentPosition = gme_tell( emu );

			/* Write Buffer */
			(*env)->CallVoidMethod(env, callingObject, mid_write, currentPosition);

			/* Sleep */
			sleep(.1);
		}
	}

	/* Cleanup */
	(*env)->ReleaseStringUTFChars(env, filePath, file_path);
	gme_free_info( track_info );
	gme_delete( emu );
}

/**
 * NOT USED
 */
void
Java_com_example_subtle_SoundMachine_convertToWave( JNIEnv* env, jobject callingObject, jstring fromPath, jstring toPath )
{
	/* Buffer Size */
	int buf_size = 1024; /* can be any multiple of 2 */

	/* Grab Paths */
	const char *from_path = (*env)->GetStringUTFChars(env, fromPath, 0);
	const char *to_path = (*env)->GetStringUTFChars(env, toPath, 0);

	/* Open music file in new emulator */
	Music_Emu* emu;
	handle_error( gme_open_file( from_path, &emu, sample_rate ) );

	/* Get Track Info */
	gme_info_t* track_info = NULL;
	handle_error( gme_track_info( emu, &track_info, track ) );

	/* Start track */
	handle_error( gme_start_track( emu, track ) );

	/* Begin writing to wave file */
	wave_open( sample_rate, to_path );
	wave_enable_stereo();

	/* Output to Wave */
	while ( gme_tell( emu ) < track_info->play_length )
	{
		/* Sample buffer */
		short buf [buf_size];

		/* Fill sample buffer */
		handle_error( gme_play( emu, buf_size, buf ) );

		/* Write samples to wave file */
		wave_write( buf, buf_size );
	}

	/* Cleanup */
	gme_free_info( track_info );
	(*env)->ReleaseStringUTFChars(env, fromPath, from_path);
	(*env)->ReleaseStringUTFChars(env, toPath, to_path);
	gme_delete( emu );
	wave_close();
}

void
Java_com_example_subtle_SNESTrack_loadTrackInfo( JNIEnv* env, jobject callingObject, jstring filePath )
{
	/* Grab File Path */
	const char *file_path = (*env)->GetStringUTFChars(env, filePath, 0);

	/* Load Emulator */
	Music_Emu* emu;
	handle_error( gme_open_file( file_path, &emu, sample_rate ) );

	/* Get Track Info */
	gme_info_t* track_info = NULL;
	handle_error( gme_track_info( emu, &track_info, track ) );

	/* Get Class Information */
	jclass thisClass = (*env)->GetObjectClass(env, callingObject);

	/* Return Track Play Length */
	jfieldID fid_track_play_length = (*env)->GetFieldID(env, thisClass, "playLength", "I");
	(*env)->SetIntField(env, callingObject, fid_track_play_length, track_info->play_length);

	/* Return Track Length */
	jfieldID fid_track_length = (*env)->GetFieldID(env, thisClass, "length", "I");
	(*env)->SetIntField(env, callingObject, fid_track_length, track_info->length);

	/* Return Track Name */
	jfieldID fid_track_name = (*env)->GetFieldID(env, thisClass, "trackName", "Ljava/lang/String;");
	jstring track_name = (*env)->NewStringUTF(env, track_info->song);
	(*env)->SetObjectField(env, callingObject, fid_track_name, track_name);

	/* Return Game Name */
	jfieldID fid_game_name = (*env)->GetFieldID(env, thisClass, "gameName", "Ljava/lang/String;");
	jstring game_name = (*env)->NewStringUTF(env, track_info->game);
	(*env)->SetObjectField(env, callingObject, fid_game_name, game_name);

	/* Return System Name */
	jfieldID fid_system_name = (*env)->GetFieldID(env, thisClass, "systemName", "Ljava/lang/String;");
	jstring system_name = (*env)->NewStringUTF(env, track_info->system);
	(*env)->SetObjectField(env, callingObject, fid_system_name, system_name);

	/* Return Author Name */
	jfieldID fid_author_name = (*env)->GetFieldID(env, thisClass, "authorName", "Ljava/lang/String;");
	jstring author_name = (*env)->NewStringUTF(env, track_info->author);
	(*env)->SetObjectField(env, callingObject, fid_author_name, author_name);

	/* Return Copyright */
	jfieldID fid_copyright = (*env)->GetFieldID(env, thisClass, "copyright", "Ljava/lang/String;");
	jstring copyright = (*env)->NewStringUTF(env, track_info->copyright);
	(*env)->SetObjectField(env, callingObject, fid_copyright, copyright);

	/* Return Comment */
	jfieldID fid_comment = (*env)->GetFieldID(env, thisClass, "comment", "Ljava/lang/String;");
	jstring comment = (*env)->NewStringUTF(env, track_info->comment);
	(*env)->SetObjectField(env, callingObject, fid_comment, comment);

	/* Return Dumper */
	jfieldID fid_dumper = (*env)->GetFieldID(env, thisClass, "dumper", "Ljava/lang/String;");
	jstring dumper = (*env)->NewStringUTF(env, track_info->dumper);
	(*env)->SetObjectField(env, callingObject, fid_dumper, dumper);

	/* Cleanup */
	(*env)->ReleaseStringUTFChars(env, filePath, file_path);
	gme_free_info( track_info );
	gme_delete( emu );
}

/**
 * Error Handling
 */

void handle_error( const char* str )
{
  if ( str )
  {
    printf( "Error: %s\n", str ); getchar();
    exit( EXIT_FAILURE );
  }
}
