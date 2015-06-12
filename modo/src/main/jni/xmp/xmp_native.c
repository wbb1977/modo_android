#include <string.h>
#include <jni.h>
#include <zlib.h>
#include "common.h"
#include <android/log.h>

static xmp_context c = NULL;
static struct xmp_module_info module_info;
static struct xmp_frame_info frame_info;
static short localSamples[18000];
static int stereo_separation = 70;
static int positions = 0;
static int pos = 0;
static int loop_count = 0;

static void reset() {
	if (c != NULL) {
		xmp_end_player(c);
		xmp_release_module(c);
		xmp_free_context(c);
	}
	c = NULL;
	pos = 0;
	positions = 0;
	loop_count = 0;
}

static void getTracks() {
	if (c == NULL)
		return;
/*
	__android_log_print(ANDROID_LOG_VERBOSE, "xmp", "1Get tracks: %d", (module_info.mod)->len );
	for (i = 0; i < XMP_MAX_MOD_LENGTH; ++i) {
		__android_log_print(ANDROID_LOG_VERBOSE, "xmp", "-> %d: %d", i, (module_info.mod)->xxo[i] );
	}
*/
	int i = 0;
	positions = (module_info.mod)->len;
	for (i = 0; i < XMP_MAX_MOD_LENGTH; ++i) {
		if ((module_info.mod)->xxo[i] == 0xff || (module_info.mod)->xxo[i] == 0xFE) {
//			__android_log_print(ANDROID_LOG_VERBOSE, "xmp", "->found real end %d", i);
			positions = i;
			break;
		}
	}
}

void Java_de_illogical_modo_XmpDecoder_xmpReset(JNIEnv* env, jclass clazz) {
	reset();
}

void Java_de_illogical_modo_XmpDecoder_xmpSetStereoSeparation(JNIEnv* env, jclass clazz, int sep) {
	stereo_separation = sep;
	if (c != NULL)
		xmp_set_player(c, XMP_PLAYER_MIX, stereo_separation);
}

void Java_de_illogical_modo_XmpDecoder_xmpSetTrack(JNIEnv* env, jclass clazz, jint track) {
	if (c == NULL)
		return;
	xmp_set_position(c, track);
}

jint Java_de_illogical_modo_XmpDecoder_xmpGetTrack(JNIEnv* env, jclass clazz) {
	if (c == NULL)
		return 0;
	return pos;
}

jint Java_de_illogical_modo_XmpDecoder_xmpTracks(JNIEnv* env, jclass clazz) {
	if (c == NULL)
		return 0;
	return positions;
}

jint Java_de_illogical_modo_XmpDecoder_xmpLoadFromZip(JNIEnv* env, jclass clazz, jstring zipfile, jstring entry) {
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

	reset();
	c = xmp_create_context();
	if (c == NULL)
		return 0;

	data = getUncompressedData(czipfile, centry, &size);

	if (data == NULL)
		return 0;

	// copies memory
	int ok = xmp_load_module_from_memory(c, data, size); // return 0 on success, negative for errors
	free(data);

	if (ok == 0) {
		xmp_start_player(c, 44100, 0);
		xmp_set_player(c, XMP_PLAYER_MIX, stereo_separation);
		xmp_set_player(c, XMP_PLAYER_INTERP, XMP_INTERP_SPLINE);
		//xmp_set_player(c, XMP_PLAYER_INTERP, XMP_INTERP_NEAREST);
		xmp_get_module_info(c, &module_info);
		xmp_set_position(c, 0);
		getTracks();
	} else {
		reset();
	}

	return ok == 0;
}

jint Java_de_illogical_modo_XmpDecoder_xmpLoadFile(JNIEnv* env, jclass clazz, jstring path) {
	char cpath[1024];
	memset(cpath, 0, 1024);

	int clen = (*env)->GetStringLength(env, path);
	if (clen > 1023)
		return 0;
	(*env)->GetStringUTFRegion(env, path, 0, clen, cpath);

	reset();
	c = xmp_create_context();

	if (c == NULL)
		return 0;

	int ok = xmp_load_module(c, cpath); // return 0 on success, negative for errors

	if (ok == 0) {
		xmp_start_player(c, 44100, 0);
		xmp_set_player(c, XMP_PLAYER_MIX, stereo_separation);
		xmp_get_module_info(c, &module_info);
		xmp_set_position(c, 0);
		getTracks();
	} else {
		reset();
	}

	return ok == 0;
}

jint Java_de_illogical_modo_XmpDecoder_xmpLoopCount(JNIEnv* env, jclass clazz) {
	return loop_count;
}

jint Java_de_illogical_modo_XmpDecoder_xmpGetSamples(JNIEnv* env, jclass clazz, jshortArray samples)
{
	if (c == NULL)
		return -1;

	// How many samples are needed
	int sampleCount = (*env)->GetArrayLength(env, samples);

	// Fill in local playback, returns 0 on success
	int ok = xmp_play_buffer(c, localSamples, sampleCount * 2, 3);

	if (ok == 0) {
		xmp_get_frame_info(c, &frame_info);
		//__android_log_print(ANDROID_LOG_VERBOSE, "xmp", "Loop count %d", frame_info.loop_count);
		loop_count = frame_info.loop_count;
		pos = frame_info.pos;
	}

	// copy local buffer to java array
	(*env)->SetShortArrayRegion(env, samples, 0, sampleCount, localSamples);

	return ok;
}

jint Java_de_illogical_modo_XmpDecoder_xmpGetInstrumentsCount(JNIEnv* env, jclass clazz) {
	if (c == NULL)
		return 0;
	return (module_info.mod)->ins;
}
jint Java_de_illogical_modo_XmpDecoder_xmpGetInstrumentLength(JNIEnv* env, jclass clazz, jint nr) {
	if (c == NULL)
		return 0;
	return (module_info.mod)->xxi[nr].name != NULL ? strnlen((module_info.mod)->xxi[nr].name, 32) : 0;
}
void Java_de_illogical_modo_XmpDecoder_xmpGetInstrumentName(JNIEnv* env, jclass clazz, jint nr, jbyteArray s) {
	if (c == NULL)
		return;
	(*env)->SetByteArrayRegion(env, s, 0, (*env)->GetArrayLength(env, s), (module_info.mod)->xxi[nr].name);
}

jint Java_de_illogical_modo_XmpDecoder_xmpGetSamplesCount(JNIEnv* env, jclass clazz) {
	if (c == NULL)
		return 0;
	return (module_info.mod)->smp;
}
jint Java_de_illogical_modo_XmpDecoder_xmpGetSamplesLength(JNIEnv* env, jclass clazz, jint nr) {
	if (c == NULL)
		return 0;
	return (module_info.mod)->xxs[nr].name != NULL ? strnlen((module_info.mod)->xxs[nr].name, 32) : 0;
}
void Java_de_illogical_modo_XmpDecoder_xmpGetSamplesName(JNIEnv* env, jclass clazz, jint nr, jbyteArray s) {
	if (c == NULL)
		return;
	(*env)->SetByteArrayRegion(env, s, 0, (*env)->GetArrayLength(env, s), (module_info.mod)->xxs[nr].name);
}

jint Java_de_illogical_modo_XmpDecoder_xmpGetInfoLength(JNIEnv* env, jclass clazz, jint what) {
	if (c == NULL)
		return 0;

	switch (what) {
		case 0: return (module_info.mod)->name != NULL ? strnlen((module_info.mod)->name, XMP_NAME_SIZE) : 0;
		case 1: return (module_info.mod)->type != NULL ? strnlen((module_info.mod)->type, XMP_NAME_SIZE) : 0;
		case 2: return module_info.comment != NULL ? strnlen(module_info.comment, 10000) : 0;
	}

	return 0;
}

void Java_de_illogical_modo_XmpDecoder_xmpGetInfo(JNIEnv* env, jclass clazz, jint what, jbyteArray s) {
	if (c == NULL)
		return;

	int size = (*env)->GetArrayLength(env, s);

	switch (what) {
		case 0: (*env)->SetByteArrayRegion(env, s, 0, size, (module_info.mod)->name); break;
		case 1: (*env)->SetByteArrayRegion(env, s, 0, size, (module_info.mod)->type); break;
		case 2: (*env)->SetByteArrayRegion(env, s, 0, size, module_info.comment); break;
	}
}
