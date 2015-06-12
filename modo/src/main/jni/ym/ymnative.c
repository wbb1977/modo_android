#include <string.h>
#include <jni.h>
#include "StSoundLibrary.h"
#include "Ymload.h"

static YMMUSIC *ym_emu;
static ymMusicInfo_t ym_info;
static int ym_eof;
static short localSamples[18000];

// YM Files does not have sub songs / sub tracks
// -> one file, one song

void Java_de_illogical_modo_YmDecoder_ymReset(JNIEnv* env, jclass clazz)
{

	if (ym_emu != NULL)
	{
		ymMusicStop(ym_emu);
		ymMusicDestroy(ym_emu);
	}
	ym_emu = NULL;
	ym_eof = 0;
	memset(&ym_info, 0, sizeof(ym_info));
}

jint Java_de_illogical_modo_YmDecoder_ymGetTrackInfo(JNIEnv* env, jclass clazz, jint track, jbyteArray javatrackinfo)
{
	return 0;
}

jint Java_de_illogical_modo_YmDecoder_ymTrackLength(JNIEnv* env, jclass clazz)
{
	if (ym_emu == NULL)
	{
		return -1;
	}

	return ym_info.musicTimeInMs;
}


jint Java_de_illogical_modo_YmDecoder_ymRestart(JNIEnv* env, jclass clazz)
{
	if (ym_emu == NULL)
	{
		return -1;
	}

	ymMusicRestart(ym_emu);
	return 1;
}

jint Java_de_illogical_modo_YmDecoder_ymLoadFile(JNIEnv* env, jclass clazz, jstring path)
{	
	char cpath[1024];
	memset(cpath, 0, 1024);

	int clen = (*env)->GetStringLength(env, path);
	(*env)->GetStringUTFRegion(env, path, 0, clen, cpath);

	memset(&ym_info, 0, sizeof(ym_info));
	ym_eof = 0;
	ym_emu = ymMusicCreate(); // defaults to 44100 hz
		
	if (ym_emu != NULL && ymMusicLoad(ym_emu, cpath))
	{
		ymMusicSetLoopMode(ym_emu, 0);
		ymMusicGetInfo(ym_emu, &ym_info);
		return 1;
	}
		
	return 0;
}

jint Java_de_illogical_modo_YmDecoder_ymLoadFromZip(JNIEnv* env, jclass clazz, jstring zipfile, jstring entry)
{
	int ok = 0;
	char* data = NULL;
	int size = 0;
	char czipfile[1024];
	char centry[1024];

	memset(czipfile, 0, 1024);
	int clen = (*env)->GetStringLength(env, zipfile);
	if (clen > 1023)
		return 0;
	(*env)->GetStringUTFRegion(env, zipfile, 0, clen, czipfile);

	memset(centry, 0, 1024);
	clen = (*env)->GetStringLength(env, entry);
	if (clen > 1023)
		return 0;
	(*env)->GetStringUTFRegion(env, entry, 0, clen, centry);

	data = getUncompressedData(czipfile, centry, &size);

	if (data == NULL)
		return 0;

	memset(&ym_info, 0, sizeof(ym_info));
	ym_eof = 0;
	ym_emu = ymMusicCreate(); // defaults to 44100 hz

	if (ym_emu != NULL && (ok = ymMusicLoadMemory(ym_emu, data, size)))
	{
		ymMusicSetLoopMode(ym_emu, 0);
		ymMusicGetInfo(ym_emu, &ym_info);
	}

	free(data);

	if (ym_emu != NULL && ok)
		return size;

	return 0;
}

jint Java_de_illogical_modo_YmDecoder_ymGetSamples(JNIEnv* env, jclass clazz, jshortArray samples)
{
	if (ym_emu == NULL)
	{
		return -1;
	}

	// How many samples are needed
	int sampleCount = (*env)->GetArrayLength(env, samples); 
	
	// Fill in local playback, no more than 50k, 
	ymMusicCompute(ym_emu, (ymsample*)localSamples, sampleCount);
	
	// copy local buffer to java array
	(*env)->SetShortArrayRegion(env, samples, 0, sampleCount, localSamples);

	return 1;
}

// INFO Functions -  a little stupid...I know, sorry
// 1 = song name
// 2 = author
// 3 = comment
// 4 = type
// 5 = player
jint Java_de_illogical_modo_YmDecoder_ymGetInfoLength(JNIEnv* env, jclass clazz, jint what)
{
	if (ym_emu == NULL)
	{
		return 0;
	}

	switch (what) {
		case 1:	return ym_info.pSongName != NULL ? strlen(ym_info.pSongName) : 0;
		case 2: return ym_info.pSongAuthor != NULL ? strlen(ym_info.pSongAuthor) : 0;
		case 3: return ym_info.pSongComment != NULL ? strlen(ym_info.pSongComment) : 0;
		case 4: return ym_info.pSongType != NULL ? strlen(ym_info.pSongType) : 0;
		case 5: return ym_info.pSongPlayer != NULL ? strlen(ym_info.pSongPlayer) : 0;
	}

	return 0;
}

jint Java_de_illogical_modo_YmDecoder_ymGetInfoBytes(JNIEnv* env, jclass clazz, jbyteArray bytes, jint length, jint what)
{
	if (ym_emu == NULL) {
		return 0;
	}
	switch (what) {
		case 1: (*env)->SetByteArrayRegion(env, bytes, 0, length, ym_info.pSongName);
			break;
		case 2: (*env)->SetByteArrayRegion(env, bytes, 0, length, ym_info.pSongAuthor);
			break;
		case 3: (*env)->SetByteArrayRegion(env, bytes, 0, length, ym_info.pSongComment);
			break;
		case 4: (*env)->SetByteArrayRegion(env, bytes, 0, length, ym_info.pSongType);
			break;
		case 5: (*env)->SetByteArrayRegion(env, bytes, 0, length, ym_info.pSongPlayer);
			break;
	}
	return 1;
}

// absolute time in ms
jint Java_de_illogical_modo_YmDecoder_ymSeek(JNIEnv* env, jclass clazz, jint time)
{
	if (ym_emu == NULL) {
		return 0;
	}

	if (ymMusicIsSeekable(ym_emu) == YMFALSE) {
		return 0;
	}
	
	ymMusicSeek(ym_emu, time);
	
	return 1;
}
