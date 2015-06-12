#include <string.h>
#include <jni.h>
#include <limits.h>
#include "gme.h"
#include "uncompress.h"
#include <zlib.h>
//#include <android/log.h>

static Music_Emu* emu = NULL;
static Music_Emu* emu2 = NULL;
static short localSamples[18000];
static short localSamples2[18000];
static double depth = 1.0;

void Java_de_illogical_modo_AytDecoder_aytSetStereoSeparation(JNIEnv* env, jclass clazz, jint d)
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
void Java_de_illogical_modo_AytDecoder_aytReset(JNIEnv* env, jclass clazz)
{
	if (emu != NULL)
		gme_delete(emu);

	if (emu2 != NULL);
		gme_delete(emu2);

	emu = NULL;
	emu2 = NULL;
}

jlong Java_de_illogical_modo_AytDecoder_aytSeek(JNIEnv* env, jclass clazz, jlong milli)
{
	if (emu == NULL)
	{
		return -1;
	}
	gme_seek(emu, gme_tell(emu) + milli);
	gme_seek(emu2, gme_tell(emu2) + milli);

	return 1;
}

jlong Java_de_illogical_modo_AytDecoder_aytPlaytime(JNIEnv* env, jclass clazz)
{
	if (emu == NULL)
	{
		return -1;
	}

	return gme_tell(emu);
}

jint Java_de_illogical_modo_AytDecoder_aytGetTrackInfoLength(JNIEnv* env, jclass clazz, jint track, jint what) {
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

void Java_de_illogical_modo_AytDecoder_aytGetTrackInfo(JNIEnv* env, jclass clazz, jint track, jint what, jbyteArray s) {
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


jlong Java_de_illogical_modo_AytDecoder_aytTrackLength(JNIEnv* env, jclass clazz, jint track)
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

jint Java_de_illogical_modo_AytDecoder_aytTracks(JNIEnv* env, jclass clazz)
{
	if (emu == NULL)
	{
		return -1;
	}

	return gme_track_count(emu);
}


jint Java_de_illogical_modo_AytDecoder_aytSetTrack(JNIEnv* env, jclass clazz, jint track)
{
	if (emu == NULL)
	{
		return -1;
	}
	if (emu2 == NULL)
	{
		return -1;
	}

	gme_start_track(emu, track);
	gme_set_stereo_depth(emu, depth);
	gme_start_track(emu2, track + 1);
	gme_set_stereo_depth(emu2, depth);

	return 1;
}

jint Java_de_illogical_modo_AytDecoder_aytLoadFile(JNIEnv* env, jclass clazz, jstring path)
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

	err = gme_open_file(cpath, &emu2, 44100);

	if (err != NULL)
	{
		emu2 = NULL;
	}
	else
	{
		gme_start_track(emu2, 1);
		gme_set_stereo_depth(emu2, depth);
	}

	return emu != NULL && emu2 != NULL;
}

jint Java_de_illogical_modo_AytDecoder_aytLoadFromZip(JNIEnv* env, jclass clazz, jstring zipfile, jstring entry)
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

	if (err != NULL)
	{
		emu = NULL;
	}
	else
	{
		gme_start_track(emu, 0);
		gme_set_stereo_depth(emu, depth);
	}

	err = gme_open_data(data, size, &emu2, 44100);

	if (err != NULL)
	{
		emu2 = NULL;
	}
	else
	{
		gme_start_track(emu2, 1);
		gme_set_stereo_depth(emu2, depth);
	}

	free(data);

	if (emu != NULL && emu2 != NULL)
		return size;

	return 0;
}

jint Java_de_illogical_modo_AytDecoder_aytGetSamples(JNIEnv* env, jclass clazz, jshortArray samples)
{
	if (emu == NULL)
		return -1;
	if (emu2 == NULL)
		return -1;

	// How many samples are needed
	int sampleCount = (*env)->GetArrayLength(env, samples);

	// Fill in local playback, no more than 50k, returns NULL on success
	gme_play(emu, sampleCount, localSamples);
	gme_play(emu2, sampleCount, localSamples2);

	int i;
	long clip_sample = 0;
	for (i = 0; i < sampleCount; ++i) {
		clip_sample = localSamples[i] + localSamples2[i];
		if (clip_sample >= SHRT_MAX)
			clip_sample = SHRT_MAX;
		if (clip_sample <= SHRT_MIN)
			clip_sample = SHRT_MIN;
		localSamples[i] = (short)clip_sample;
	}

	// copy local buffer to java array
	(*env)->SetShortArrayRegion(env, samples, 0, sampleCount, localSamples);

	return 1;
}
