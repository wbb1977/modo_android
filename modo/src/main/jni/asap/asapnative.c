#include <string.h>
#include <jni.h>
#include "asap.h"

static ASAP* asap = NULL;
static ASAPInfo *info = NULL;
static unsigned char module[ASAPInfo_MAX_MODULE_LENGTH];
static short localSamples[18000];

jint Java_de_illogical_modo_AsapDecoder_asapGetChannels(JNIEnv* env, jclass clazz) {
	if (info == NULL)
		return 2;
	return ASAPInfo_GetChannels(info);
}

void Java_de_illogical_modo_AsapDecoder_asapReset(JNIEnv* env, jclass clazz) {
	if (asap == NULL) {
		asap = ASAP_New();
	}

	if (info != NULL) {
		ASAPInfo_Delete(info);
		info = NULL;
	}
}

jint Java_de_illogical_modo_AsapDecoder_asapLoadFile(JNIEnv* env, jclass clazz, jstring path, jint module_length, jbyteArray modulebytes)
{	
	cibool ok = FALSE;

	char cpath[1024];
	memset(cpath, 0, 1024);

	int clen = (*env)->GetStringLength(env, path);
	if (clen > 1023)
		return 0;
	(*env)->GetStringUTFRegion(env, path, 0, clen, cpath);

	(*env)->GetByteArrayRegion(env, modulebytes, 0, module_length, module);

	if (asap == NULL)
		return 0;

	ok = ASAP_Load(asap, cpath, module, module_length);
	
	if (ok == FALSE)
		return 0;

	if (info == NULL) {
		info = ASAPInfo_New();
		ASAPInfo_Load(info, cpath, module, module_length);
	}

	return 1;
}

jint Java_de_illogical_modo_AsapDecoder_asapLoadFromZip(JNIEnv* env, jclass clazz, jstring zipfile, jstring entry)
{
	cibool ok = FALSE;
	char* data = NULL;
	int size = 0;
	char czipfile[1024];
	char centry[1024];

	if (asap == NULL)
		return 0;

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

	data = (char*)getUncompressedData(czipfile, centry, &size);

	if (data == NULL)
		return 0;

	// makes copy of data / module
	ok = ASAP_Load(asap, NULL, data, size);

	if (ok == TRUE && info == NULL) {
		info = ASAPInfo_New();
		ASAPInfo_Load(info, NULL, data, size);
	}

	free(data);

	if (ok == FALSE)
		return 0;

	return size;
}


jint Java_de_illogical_modo_AsapDecoder_asapTrackLength(JNIEnv* env, jclass clazz, jint track)
{
	int l = -1;

	if (info == NULL)
		return -1;

	l = ASAPInfo_GetDuration(info, track); // returns -1 if not possible to detect
	
	return l;
}

jint Java_de_illogical_modo_AsapDecoder_asapTracks(JNIEnv* env, jclass clazz)
{
	if (info == NULL)
		return -1;
		
	return ASAPInfo_GetSongs(info);
}


jint Java_de_illogical_modo_AsapDecoder_asapSetTrack(JNIEnv* env, jclass clazz, jint track)
{
	if (asap == NULL)
		return -1;

	ASAP_PlaySong(asap, track, -1);
	return 1;
}

void Java_de_illogical_modo_AsapDecoder_asapSeek(JNIEnv* env, jclass clazz, jint ms)
{
	if (asap == NULL)
		return;

	ASAP_Seek(asap, ms);
}



jint Java_de_illogical_modo_AsapDecoder_asapGetSamples(JNIEnv* env, jclass clazz, jshortArray samples)
{
	if (asap == NULL)
		return -1;

	// How many samples are needed
	int sampleCount = (*env)->GetArrayLength(env, samples); 
	
	// Fill in local playback, no more than 50k, 
	ASAP_Generate(asap, (unsigned char*)localSamples, sampleCount * 2, ASAPSampleFormat_S16_L_E);	
	
	// copy local buffer to java array
	(*env)->SetShortArrayRegion(env, samples, 0, sampleCount, localSamples);

	return 1;
}

void Java_de_illogical_modo_AsapDecoder_asapGetTitle(JNIEnv* env, jclass clazz, jbyteArray title)
{
	if (info == NULL)
		return;

	// Max 127 byte for title, java array is 256 bytes long and set to zero
	const char* t = ASAPInfo_GetTitleOrFilename(info);

	if (t == NULL)
		return;

	(*env)->SetByteArrayRegion(env, title, 0, strlen(t), t);
}

void Java_de_illogical_modo_AsapDecoder_asapGetAuthor(JNIEnv* env, jclass clazz, jbyteArray author)
{
	if (info == NULL)
		return;

	// Max 127 byte for title, java array is 256 bytes long and set to zero
	const char* a = ASAPInfo_GetAuthor(info);

	if (a == NULL)
		return;

	(*env)->SetByteArrayRegion(env, author, 0, strlen(a), a);
}

jint Java_de_illogical_modo_AsapDecoder_asapGetYear(JNIEnv* env, jclass clazz)
{
	if (info == NULL)
		return -1;
	return ASAPInfo_GetYear(info);
}

jint Java_de_illogical_modo_AsapDecoder_asapGetClock(JNIEnv* env, jclass clazz)
{
	if (info == NULL)
		return -1;
	return ASAPInfo_IsNtsc(info);
}

