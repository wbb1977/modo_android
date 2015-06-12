#include <string.h>
#include <jni.h>
#include "gme.h"
#include "dll.hpp"
#include "uncompress.h"
#include <zlib.h>
//#include <android/log.h>

static Music_Emu* emu = NULL;
static short localSamples[18000];
static double depth = 1.0;
int ay_stereo = 1; // 1 = pure AY channels (A,B,C), 0 = all into center (mono)

// RSN related
#define MAX_SPC_MODULE_SIZE 67000
#define RSN_ENTRIES 255
static HANDLE rarHandle;
static struct RAROpenArchiveData rarFileData;
static struct RARHeaderData rarFileHeader;
static char* rsnEntries[RSN_ENTRIES];
static unsigned char rsnEntry[MAX_SPC_MODULE_SIZE];
static int rsnSize[RSN_ENTRIES];
static int rsnCount;
static char *rsnInfo;

void Java_de_illogical_modo_SpcDecoder_spcSetStereoAY(JNIEnv* env, jclass clazz, jint stereo)
{
	ay_stereo = stereo;
	// Hacked Ay_Emu "setVoice" to take this into account
}

void Java_de_illogical_modo_SpcDecoder_spcSetStereoSeparation(JNIEnv* env, jclass clazz, jint d)
{
	depth = d/100.0;
	if (depth < 0.0)
		depth = 0.0;
	if (depth > 1.0)
		depth = 1.0;
	if (emu != NULL)
		gme_set_stereo_depth(emu, depth);
//	__android_log_print(ANDROID_LOG_VERBOSE, "gme", "%i %f", d, depth);
}
void Java_de_illogical_modo_SpcDecoder_spcReset(JNIEnv* env, jclass clazz)
{
	if (emu != NULL)
		gme_delete(emu);

	emu = NULL;
}

jlong Java_de_illogical_modo_SpcDecoder_spcSeek(JNIEnv* env, jclass clazz, jlong milli)
{
	if (emu == NULL)
	{
		return -1;
	}
	return gme_seek(emu, gme_tell(emu) + milli) == NULL;
}

jlong Java_de_illogical_modo_SpcDecoder_spcPlaytime(JNIEnv* env, jclass clazz)
{
	if (emu == NULL)
	{
		return -1;
	}

	return gme_tell(emu);
}

jint Java_de_illogical_modo_SpcDecoder_spcGetTrackInfoLength(JNIEnv* env, jclass clazz, jint track, jint what) {
	struct gme_info_t* trackinfo;
	int size = 0;
	if (emu == NULL)
		return;
	gme_err_t err = gme_track_info(emu, &trackinfo, track);
	if (err == NULL)
	{
		switch (what)
		{
			case 0: size = strnlen(trackinfo->system, 255); break;
			case 1: size = strnlen(trackinfo->game, 255); break;
			case 2: size = strnlen(trackinfo->song, 255); break;
			case 3: size = strnlen(trackinfo->author, 255); break;
			case 4: size = strnlen(trackinfo->copyright, 255); break;
			case 5: size = strnlen(trackinfo->comment, 255); break;
			case 6: size = strnlen(trackinfo->dumper, 255); break;
		}
	}
	gme_free_info(trackinfo);
	return size;
}

void Java_de_illogical_modo_SpcDecoder_spcGetTrackInfo(JNIEnv* env, jclass clazz, jint track, jint what, jbyteArray s) {
	struct gme_info_t* trackinfo;
	int size = (*env)->GetArrayLength(env, s);
	if (emu == NULL)
		return;
	if (size > 255)
		return;
	gme_err_t err = gme_track_info(emu, &trackinfo, track);
	if (err == NULL)
	{
		switch (what)
		{
			case 0: (*env)->SetByteArrayRegion(env, s, 0, size, trackinfo->system); break;
			case 1: (*env)->SetByteArrayRegion(env, s, 0, size, trackinfo->game); break;
			case 2: (*env)->SetByteArrayRegion(env, s, 0, size, trackinfo->song); break;
			case 3: (*env)->SetByteArrayRegion(env, s, 0, size, trackinfo->author); break;
			case 4: (*env)->SetByteArrayRegion(env, s, 0, size, trackinfo->copyright); break;
			case 5: (*env)->SetByteArrayRegion(env, s, 0, size, trackinfo->comment); break;
			case 6: (*env)->SetByteArrayRegion(env, s, 0, size, trackinfo->dumper); break;
		}
	}
	gme_free_info(trackinfo);
}

/*
jobjectArray Java_de_illogical_modo_SpcDecoder_spcGetTrackInfo(JNIEnv* env, jclass clazz, jint track)
{
	gme_err_t err = NULL;
	struct gme_info_t* trackinfo;
	
	if (emu == NULL)
	{
		return NULL;
	}

	err = gme_track_info(emu, &trackinfo, track);
	
	if (err == NULL)
	{
		jobjectArray info = (*env)->NewObjectArray(env, 7, (*env)->FindClass(env, "java/lang/String"), NULL);
		(*env)->SetObjectArrayElement(env, info, 0, ((*env)->NewStringUTF(env, trackinfo->system)));
		(*env)->SetObjectArrayElement(env, info, 1, ((*env)->NewStringUTF(env, trackinfo->game)));
		(*env)->SetObjectArrayElement(env, info, 2, ((*env)->NewStringUTF(env, trackinfo->song)));
		(*env)->SetObjectArrayElement(env, info, 3, ((*env)->NewStringUTF(env, trackinfo->author)));
		(*env)->SetObjectArrayElement(env, info, 4, ((*env)->NewStringUTF(env, trackinfo->copyright)));
		(*env)->SetObjectArrayElement(env, info, 5, ((*env)->NewStringUTF(env, trackinfo->comment)));
		(*env)->SetObjectArrayElement(env, info, 6, ((*env)->NewStringUTF(env, trackinfo->dumper)));
		gme_free_info(trackinfo);
		return info;
	}

	return NULL;
}
*/

jlong Java_de_illogical_modo_SpcDecoder_spcTrackLength(JNIEnv* env, jclass clazz, jint track)
{
	gme_err_t err = NULL;
	struct gme_info_t* trackinfo;
	int l = -1;
	
	if (emu == NULL)
	{
		return -1;
	}

	err = gme_track_info(emu, &trackinfo, track);
	
	if (err == NULL)
	{
		l = trackinfo->length;
		gme_free_info(trackinfo);
		return l;
	}
	
	return -1;
}

jint Java_de_illogical_modo_SpcDecoder_spcTracks(JNIEnv* env, jclass clazz)
{
	if (emu == NULL)
	{
		return -1;
	}
		
	return gme_track_count(emu);
}


jint Java_de_illogical_modo_SpcDecoder_spcSetTrack(JNIEnv* env, jclass clazz, jint track)
{
	if (emu == NULL)
	{
		return -1;
	}

	gme_start_track(emu, track);
	gme_set_stereo_depth(emu, depth);
	return 1;
}

jint Java_de_illogical_modo_SpcDecoder_spcLoadFile(JNIEnv* env, jclass clazz, jstring path)
{	
	char cpath[1024];
	memset(cpath, 0, 1024);

	int clen = (*env)->GetStringLength(env, path);
	if (clen > 1023)
		return 0;
	(*env)->GetStringUTFRegion(env, path, 0, clen, cpath);

	gme_err_t err = gme_open_file(cpath, &emu, 44100);
	
	if (err != NULL)
	{
		emu = NULL;
	}
	else
	{
		gme_start_track(emu, 0);
		gme_set_stereo_depth(emu, depth);
	}
	
	return emu != NULL;
}

jint Java_de_illogical_modo_SpcDecoder_spcLoadFromZip(JNIEnv* env, jclass clazz, jstring zipfile, jstring entry)
{
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

	// GME copies memory
	gme_err_t err = gme_open_data(data, size, &emu, 44100);
	free(data);

	if (err != NULL)
	{
		emu = NULL;
	}
	else
	{
		gme_start_track(emu, 0);
		gme_set_stereo_depth(emu, depth);
	}

	if (emu != NULL)
		return size;

	return 0;
}

jint Java_de_illogical_modo_SpcDecoder_spcGetSamples(JNIEnv* env, jclass clazz, jshortArray samples)
{
	if (emu == NULL)
	{
		return -1;
	}

	// How many samples are needed
	int sampleCount = (*env)->GetArrayLength(env, samples); 
	
	// Fill in local playback, no more than 50k, returns NULL on success
	gme_err_t err = gme_play(emu, sampleCount, localSamples);
	
	// copy local buffer to java array
	(*env)->SetShortArrayRegion(env, samples, 0, sampleCount, localSamples);

	return err == NULL;
}

/*
===============
= RSN related =
===============
*/
static char* strfind(char *str, char *tgt)
{
	int tlen = strlen(tgt);
	int max = strlen(str) - tlen;
	register int i;

	for (i = 0; i <= max; i++) {
	    if (strncasecmp(&str[i], tgt, tlen) == 0)
			return &str[i+tlen];
	}
	return NULL;
}

void Java_de_illogical_modo_SpcDecoder_spcInit(JNIEnv* env, jclass clazz)
{
	int i;
	for (i = 0; i < RSN_ENTRIES; i++)
	{
		rsnEntries[i] = NULL;
		rsnSize[i] = 0;
	}
	rsnInfo = NULL;
	rsnCount = 0;
}

void Java_de_illogical_modo_SpcDecoder_spcResetRsnEntries(JNIEnv* env, jclass clazz) {
	int i;
	for (i = 0; i < RSN_ENTRIES; i++)
	{
		if (rsnEntries[i] != NULL)
			free(rsnEntries [i]);

		rsnEntries[i] = NULL;
		rsnSize[i] = 0;
	}

	rsnCount = 0;

	if (rsnInfo != NULL)
		free(rsnInfo);
	rsnInfo = NULL;
}

static int rarExtractSpcBytes(UINT msg, LPARAM rsnSpcBuffer, LPARAM extractedData, LPARAM bytesProcessed)
{
	static offset = 0;
	if (msg == 0xFFFF)
		offset = 0;
	if (msg == UCM_PROCESSDATA)
	{
		memcpy((char*)rsnSpcBuffer + offset, (char*)extractedData, bytesProcessed);
		offset += bytesProcessed;
		return 1;
	}
	return -1;
}

static int rarExtractInfoBytes(UINT msg, LPARAM rsnInfoBuffer, LPARAM extractedData, LPARAM bytesProcessed)
{
	static offset = 0;
	if (msg == 0xFFFF)
		offset = 0;
	if (msg == UCM_PROCESSDATA)
	{
		memcpy((char*)rsnInfoBuffer + offset, (char*)extractedData, bytesProcessed);
		offset += bytesProcessed;
		return 1;
	}
	return -1;
}

jint Java_de_illogical_modo_SpcDecoder_spcLoadRSN(JNIEnv* env, jclass clazz, jstring path)
{
	char cpath[1024];
	memset(cpath, 0, 1024);

	int clen = (*env)->GetStringLength(env, path);
	(*env)->GetStringUTFRegion(env, path, 0, clen, cpath);
	
	rarFileData.ArcName = cpath;
	rarFileData.OpenMode = RAR_OM_EXTRACT;
	rarHandle = RAROpenArchive(&rarFileData);

	if (rarFileData.OpenResult == ERAR_SUCCESS)
	{
		int res = RARReadHeader(rarHandle, &rarFileHeader);
		while(res == ERAR_SUCCESS)
		{
			if (strfind(rarFileHeader.FileName, ".spc") != NULL && rsnCount < RSN_ENTRIES && rarFileHeader.UnpSize > 66000 && rarFileHeader.UnpSize < 67000)
			{
				rsnEntries[rsnCount] = (char*)malloc(rarFileHeader.UnpSize);
				if (rsnEntries[rsnCount] != NULL) {
					rarExtractSpcBytes(0xFFFF, 0, 0, 0);
					RARSetCallback(rarHandle, rarExtractSpcBytes, (long)rsnEntries[rsnCount]);
					RARProcessFile(rarHandle, RAR_EXTRACT, NULL, NULL);
					rsnSize[rsnCount] = rarFileHeader.UnpSize;
					rsnCount++;
				} 
			} else if (strfind(rarFileHeader.FileName, ".txt") != NULL && rsnInfo == NULL && rarFileHeader.UnpSize < 10000)
			{
				rsnInfo = (char*)calloc(rarFileHeader.UnpSize + 1, 1);
				if (rsnInfo != NULL) {
					rarExtractInfoBytes(0xFFFF, 0, 0, 0);
					RARSetCallback(rarHandle, rarExtractInfoBytes, (long)rsnInfo);
					RARProcessFile(rarHandle, RAR_EXTRACT, NULL, NULL);
				}
			} else
			{
				RARProcessFile(rarHandle, RAR_SKIP, NULL, NULL);
			}
			res = RARReadHeader(rarHandle, &rarFileHeader);
		}
	}
	RARCloseArchive(rarHandle);

	return rsnCount;	
}

jint Java_de_illogical_modo_SpcDecoder_spcPlayRSN(JNIEnv* env, jclass clazz, jint track)
{

	if (track < 0 || track >= rsnCount)
		return 0;

	if (rsnEntries[track] == NULL)
		return 0;

	if (rsnSize[track] <= 0)
		return 0;

	gme_err_t err = gme_open_data(rsnEntries[track], rsnSize[track], &emu, 44100);

	if (err != NULL)
	{
		emu = NULL;
	}
	else
	{
		gme_start_track(emu, 0);
		gme_set_stereo_depth(emu, depth);
	}

	return emu != NULL;
}


jint Java_de_illogical_modo_SpcDecoder_spcRSNInfoLength(JNIEnv* env, jclass clazz)
{
	return rsnInfo == NULL ? 0 : strnlen(rsnInfo, 10000);
}

void Java_de_illogical_modo_SpcDecoder_spcGetRSNInfo(JNIEnv* env, jclass clazz,  jbyteArray info)
{
	if (rsnInfo != NULL)
		(*env)->SetByteArrayRegion(env, info, 0, strlen(rsnInfo), rsnInfo);
}
