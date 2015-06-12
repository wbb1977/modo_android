#include <string.h>
#include <jni.h>
#include "neserr.h"
#include "nezplug.h"
#include "uncompress.h"

NEZ_PLAY* nez = NULL;
static short localSamples[18000];

void Java_de_illogical_modo_NezplugDecoder_nezReset(JNIEnv* env, jclass clazz)
{
	if (nez != NULL)
		NEZDelete(nez);

	nez = NULL;
}

int Java_de_illogical_modo_NezplugDecoder_nezLoadFile(JNIEnv* env, jclass clazz, jstring path)
{
	char cpath[1024];
	memset(cpath, 0, 1024);

	int clen = (*env)->GetStringLength(env, path);
	if (clen > 1023)
		return 0;
	(*env)->GetStringUTFRegion(env, path, 0, clen, cpath);

	if (nez != NULL)
		NEZDelete(nez);

	nez = NEZNew();

	if (nez == NULL)
		return 0;

	void* nezbuf;
	int nezsize = NEZ_extract(cpath, &nezbuf);
	if (nezsize == 0)
		return 0;

	NESERR_CODE err = NEZLoad(nez, nezbuf, nezsize);
	free(nezbuf);

	if (err != NESERR_NOERROR)
		return 0;

	NEZSetSongNo(nez, 1);
	NEZReset(nez);
	NEZSetFrequency(nez, 44100);
	NEZSetChannel(nez,2);
	NEZVolume(nez, 0);

	return 1;
}

int Java_de_illogical_modo_NezplugDecoder_nezLoadFromZip(JNIEnv* env, jclass clazz, jstring zipfile, jstring entry, jbyteArray first80bytes)
{
	char* data = NULL;
	int size = 0;
	char czipfile[1024];
	char centry[1024];

	if (nez != NULL)
		NEZDelete(nez);

	nez = NEZNew();

	if (nez == NULL)
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

	data = getUncompressedData(czipfile, centry, &size);

	if (data == NULL)
		return 0;

	clen = (*env)->GetArrayLength(env, first80bytes);
	if (size > clen)
		(*env)->SetByteArrayRegion(env, first80bytes, 0, clen, data);

	NESERR_CODE err = NEZLoad(nez, data, size);
	free(data);

	if (err != NESERR_NOERROR)
		return 0;

	NEZSetSongNo(nez, 1);
	NEZReset(nez);
	NEZSetFrequency(nez, 44100);
	NEZSetChannel(nez,2);
	NEZVolume(nez, 0);

	return size;
}

int Java_de_illogical_modo_NezplugDecoder_nezSetTrack(JNIEnv* env, jclass clazz, jint track)
{
	if (nez == NULL)
		return -1;
	NEZSetSongNo(nez, track);
	NEZReset(nez);
	NEZSetFrequency(nez, 44100);
	NEZSetChannel(nez,2);
	NEZVolume(nez, 0);

	return 1;
}

int Java_de_illogical_modo_NezplugDecoder_nezTracks(JNIEnv* env, jclass clazz)
{
	if (nez == NULL)
		return -1;

	return NEZGetSongMax(nez);
}


jint Java_de_illogical_modo_NezplugDecoder_nezGetSamples(JNIEnv* env, jclass clazz, jshortArray samples)
{
	if (nez == NULL)
		return -1;

	// How many samples are needed
	int sampleCount = (*env)->GetArrayLength(env, samples);

	// Fill in local playback, no more than 50k
	// Sorry, but WTF??? buffer len is in samples,ok. got it.
	// but if in stereo, buffer len is just for one channel..Thank you that I finally got it!!!
	NEZRender(nez, localSamples, sampleCount/2);

	// copy local buffer to java array
	(*env)->SetShortArrayRegion(env, samples, 0, sampleCount, localSamples);

	return 1;
}

void Java_de_illogical_modo_NezplugDecoder_nezForward(JNIEnv* env, jclass clazz, jlong pos)
{
	if (nez == NULL)
		return;
	NEZRender(nez, localSamples, 17640 / 2);
	//NEZRender(nez, NULL, pos);
}
