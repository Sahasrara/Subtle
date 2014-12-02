Subtle
======

Android App for Subsonic (that can play .spc files)


Installation:
1) Clone this repo

2) Download the android SDK

3) Download android NDK

4) Run ndk-build from the Subtle folder.  This will build the C library and populate the obj directory.

5) I used eclipse.  Download eclipse, set it up for Android development (lots of tutorials online).
   This will at some point include pointing eclipse to the android SDK folder.  This will also include
   creating a new emulator.

6) Start a new workspace, and project.  File->import(android project) and import the Subtle folder.
   You will need to create a new run configuration for this project too.
   
7) Should be able to run the app in the emulator.  


Now, if you want to play .spc files, you'll need to download my fork of subsonic available at:

https://github.com/Sahasrara/subsonic

This will be able to parse the metadata and send files to the client.  I also made some changes to the 
response code handling.  Before it would not return useful response codes in the event of an error.