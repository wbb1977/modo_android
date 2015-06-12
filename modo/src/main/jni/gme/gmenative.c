#include <string.h>
#include <jni.h>
#include <zlib.h>
#include "gme.h"
#include "uncompress.h"
//#include <android/log.h>

static double depth = 0.0;
static Music_Emu* emu = NULL;
static short localSamples[18000];

void Java_de_illogical_modo_GmeDecoder_gmeSetStereoSeparation(JNIEnv* env, jclass clazz, jint d)
{
	depth = d/100.0;
	if (depth < 0.0)
		depth = 0.0;
	if (depth > 1.0)
		depth = 1.0;
	if (emu != NULL)
		gme_set_stereo_depth(emu, depth);
}

void Java_de_illogical_modo_GmeDecoder_gmeReset(JNIEnv* env, jclass clazz)
{
	if (emu != NULL)
		gme_delete(emu);

	emu = NULL;
}

jlong Java_de_illogical_modo_GmeDecoder_gmeSeek(JNIEnv* env, jclass clazz, jlong milli)
{
	if (emu == NULL)
	{
		return -1;
	}
	return gme_seek(emu, gme_tell(emu) + milli) == NULL;
}

jlong Java_de_illogical_modo_GmeDecoder_gmePlaytime(JNIEnv* env, jclass clazz)
{
	if (emu == NULL)
	{
		return -1;
	}

	return gme_tell(emu);
}

jint Java_de_illogical_modo_GmeDecoder_gmeGetTrackInfoLength(JNIEnv* env, jclass clazz, jint track, jint what) {
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

void Java_de_illogical_modo_GmeDecoder_gmeGetTrackInfo(JNIEnv* env, jclass clazz, jint track, jint what, jbyteArray s) {
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
jobjectArray Java_de_illogical_modo_GmeDecoder_gmeGetTrackInfo(JNIEnv* env, jclass clazz, jint track)
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
		//(*env)->SetByteArrayRegion(env, javatrackinfo, 0, 256*7, trackinfo->system);
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

jlong Java_de_illogical_modo_GmeDecoder_gmeTrackLength(JNIEnv* env, jclass clazz, jint track)
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

jint Java_de_illogical_modo_GmeDecoder_gmeTracks(JNIEnv* env, jclass clazz)
{
	if (emu == NULL)
	{
		return -1;
	}
		
	return gme_track_count(emu);
}


jint Java_de_illogical_modo_GmeDecoder_gmeSetTrack(JNIEnv* env, jclass clazz, jint track)
{
	if (emu == NULL)
	{
		return -1;
	}

	gme_ignore_silence(emu, 1);
	gme_start_track(emu, track);
	gme_set_stereo_depth(emu, depth);
	return 1;
}

jint Java_de_illogical_modo_GmeDecoder_gmeLoadFile(JNIEnv* env, jclass clazz, jstring path)
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

jint Java_de_illogical_modo_GmeDecoder_gmeLoadM3u(JNIEnv* env, jclass clazz, jstring path)
{
	if (emu == NULL)
		return 0;

	char cpath[1024];
	memset(cpath, 0, 1024);

	int clen = (*env)->GetStringLength(env, path);
	if (clen > 1023)
		return 0;
	(*env)->GetStringUTFRegion(env, path, 0, clen, cpath);

	gme_err_t err = gme_load_m3u(emu, cpath);
	//__android_log_print(ANDROID_LOG_VERBOSE, "gme", "LoadM3u: %s", err == NULL ? "null" : err);

	return err == NULL;
}

jint Java_de_illogical_modo_GmeDecoder_gmeLoadM3uFromZip(JNIEnv* env, jclass clazz, jstring zipfile, jstring entry)
{
	char* m3udata;
	int size;
	char czipfile[1024];
	char centry[1024];

	if (emu == NULL)
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

	m3udata = getUncompressedData(czipfile, centry, &size);

	if (m3udata == NULL)
		return 0;

	//__android_log_print(ANDROID_LOG_VERBOSE, "gme", "LoadM3uZip: %d %s ", size, centry);

	gme_err_t err = gme_load_m3u_data(emu, m3udata, size);
	free(m3udata);

	//__android_log_print(ANDROID_LOG_VERBOSE, "gme", "LoadM3uZip: %s", err == NULL ? "null" : err);

	return err == NULL;
}

jint Java_de_illogical_modo_GmeDecoder_gmeLoadFromZip(JNIEnv* env, jclass clazz, jstring zipfile, jstring entry, int vgzfile)
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

	// If its a .vgz file inflate it. Problem is, can go up to 10MB. GME copies data = 20MB.
	// kss can also be gzip
	if (size > 10 && data[0] == 0x1F && data[1] == 0x8B) {
		unsigned long uncompressed_size = data[size-1] << 24 | data[size-2] << 16 | data[size-3] << 8 | data[size-4];
		char* inflatebuffer = malloc(uncompressed_size);
		if (inflatebuffer != NULL) {
			int err;
			z_stream zs;
			zs.zalloc = Z_NULL;
			zs.zfree = Z_NULL;
			zs.opaque = Z_NULL;
			zs.next_in = data;
			zs.avail_in = size;
			zs.next_out = inflatebuffer;
			zs.avail_out = uncompressed_size;

			inflateInit2(&zs, 16 + MAX_WBITS);
			err = inflate(&zs, Z_FINISH);
			if (err == Z_STREAM_END) {
				//__android_log_print(ANDROID_LOG_VERBOSE, "gme", "-> ZLIB STREAM END - extracted Bytes %ld", zs.total_out);
				size = zs.total_out;
				free(data);
				data = inflatebuffer;
			} else {
				free(inflatebuffer);
			}
			inflateEnd(&zs);
		}
	}

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

jint Java_de_illogical_modo_GmeDecoder_gmeGetSamples(JNIEnv* env, jclass clazz, jshortArray samples)
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
