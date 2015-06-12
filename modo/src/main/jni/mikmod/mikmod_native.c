#include <string.h>
#include <jni.h>
#include "mikmod_internals.h"
#include "uncompress.h"

#define DEFAULT_MIXER DMODE_STEREO | DMODE_16BITS | DMODE_SOFT_MUSIC | DMODE_SOFT_SNDFX

static MODULE *module = NULL;		// global mikmod MOD informations
static int mikmod_ready = 0;
static int mixer_mode = DEFAULT_MIXER;
static short localSamples[50000];
static MREADER* memreader = NULL;
static char* module_memory = NULL;


static init()
{
	md_mode = DEFAULT_MIXER;
	MikMod_RegisterAllDrivers();
	MikMod_RegisterAllLoaders();
	MikMod_Init("");
}

void Java_de_illogical_modo_MikModDecoder_mikmodSetMixerMode(JNIEnv* env, jclass clazz, jint mode)
{
	md_mode = mode;
	mixer_mode = mode;
}


void Java_de_illogical_modo_MikModDecoder_mikmodSetStereoSeparation(JNIEnv* env, jclass clazz, jint separation)
{
	md_pansep = separation;
}

void Java_de_illogical_modo_MikModDecoder_mikmodReset(JNIEnv* env, jclass clazz)
{
	// Init library if this is first call
	if (mikmod_ready == 0)
		init();

	mikmod_ready = 1;
	
	// Free memory from last file
	if (module != NULL)
	{
		Player_Stop();
		Player_Free(module);
	}

	md_mode = mixer_mode;
	MikMod_Reset(NULL);
	md_mode = mixer_mode;

	module = NULL;

	if (module_memory != NULL)
	{
		free(module_memory);
		module_memory = NULL;
	}

	if (memreader != NULL)
	{
		_mm_delete_mem_reader(memreader);
		memreader = NULL;
	}

}

// Get Track Info methods
jint Java_de_illogical_modo_MikModDecoder_mikmodGetTrackerLength(JNIEnv* env, jclass clazz)
{
	if (module == NULL)
		return 0;
	
	return module->modtype ? strlen(module->modtype) : 0;
}

jint Java_de_illogical_modo_MikModDecoder_mikmodGetTrackerBytes(JNIEnv* env, jclass clazz, jbyteArray bytes, jint length)
{
	if (module == NULL)
		return 0;
		
	(*env)->SetByteArrayRegion(env, bytes, 0, length, module->modtype);
	
	return 1;
}


jint Java_de_illogical_modo_MikModDecoder_mikmodGetTitleLength(JNIEnv* env, jclass clazz)
{
	if (module == NULL)
		return 0;
	
	return module->songname ? strlen(module->songname) : 0;
}

jint Java_de_illogical_modo_MikModDecoder_mikmodGetTitleBytes(JNIEnv* env, jclass clazz, jbyteArray bytes, jint length)
{
	if (module == NULL)
		return 0;
		
	(*env)->SetByteArrayRegion(env, bytes, 0, length, module->songname);
	
	return 1;
}


jint Java_de_illogical_modo_MikModDecoder_mikmodGetCommentLength(JNIEnv* env, jclass clazz)
{
	if (module == NULL)
		return 0;
	
	return module->comment ? strlen(module->comment) : 0;
}

jint Java_de_illogical_modo_MikModDecoder_mikmodGetCommentBytes(JNIEnv* env, jclass clazz, jbyteArray bytes, jint length)
{
	if (module == NULL)
		return 0;
		
	(*env)->SetByteArrayRegion(env, bytes, 0, length, module->comment);
	
	return 1;
}




jint Java_de_illogical_modo_MikModDecoder_mikmodGetInstrumentLength(JNIEnv* env, jclass clazz, jint number)
{
	if (module == NULL)
		return 0;

	if (module->instruments && number < module->numins && module->instruments[number].insname != NULL)
		return strlen(module->instruments[number].insname);
		
	return 0;
}
jint Java_de_illogical_modo_MikModDecoder_mikmodGetInstrumentBytes(JNIEnv* env, jclass clazz, jint number, jbyteArray bytes, jint length)
{
	if (module == NULL)
		return 0;

	(*env)->SetByteArrayRegion(env, bytes, 0, length, module->instruments[number].insname);
		
	return 1;
}

jint Java_de_illogical_modo_MikModDecoder_mikmodGetSampleLength(JNIEnv* env, jclass clazz, jint number)
{
	if (module == NULL)
		return 0;

	if (number < module->numsmp && module->samples[number].samplename != NULL)
		return strlen(module->samples[number].samplename);
		
	return 0;
}
jint Java_de_illogical_modo_MikModDecoder_mikmodGetSampleBytes(JNIEnv* env, jclass clazz, jint number, jbyteArray bytes, jint length)
{
	if (module == NULL)
		return 0;

	(*env)->SetByteArrayRegion(env, bytes, 0, length, module->samples[number].samplename);
		
	return 1;
}

jint Java_de_illogical_modo_MikModDecoder_mikmodGetInstrumentsCount(JNIEnv* env, jclass clazz)
{
	if (module == NULL)
		return 0;
		
	if (module->instruments)
		return module->numins;
	
	return 0;
}

jint Java_de_illogical_modo_MikModDecoder_mikmodGetSamplesCount(JNIEnv* env, jclass clazz)
{
	if (module == NULL)
		return 0;
		
	return module->numsmp;
}



// control methods
jint Java_de_illogical_modo_MikModDecoder_mikmodLoadFile(JNIEnv* env, jclass clazz, jstring path)
{
	char cpath[1024];
	memset(cpath, 0, 1024);

	int clen = (*env)->GetStringLength(env, path);
	if (clen > 1023)
		return 0;
	(*env)->GetStringUTFRegion(env, path, 0, clen, cpath);
	
	Player_Stop();

	module = Player_Load(cpath, 128, 0);
	
	if (module != NULL)
		Player_Start(module);	
	
	return module != NULL;
}

jint Java_de_illogical_modo_MikModDecoder_mikmodLoadFromZip(JNIEnv* env, jclass clazz, jstring zipfile, jstring entry)
{
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

	// Avoid memory leaks, Java part should call MikModReset first
	if (module_memory != NULL)
		return 0;
	if (memreader != NULL)
		return 0;

	module_memory = getUncompressedData(czipfile, centry, &size);
	if (module_memory == NULL)
		return 0;

	memreader = _mm_new_mem_reader(module_memory, size);
	if (memreader == NULL)
		return 0;

	Player_Stop();

	module = Player_LoadGeneric(memreader, 128, 0);

	if (module != NULL)
		Player_Start(module);

	if (module != NULL)
		return size;

	return 0;
}


jint Java_de_illogical_modo_MikModDecoder_mikmodTracks(JNIEnv* env, jclass clazz)
{
	if (module == NULL)
		return -1;
		
	return module->numpos;
}

jint Java_de_illogical_modo_MikModDecoder_mikmodSetTrack(JNIEnv* env, jclass clazz, jint track)
{
	if (module == NULL || track < 0 || track >= module->numpos)
		return -1;
	
	Player_SetPosition(track);
	return 1;
}

jint Java_de_illogical_modo_MikModDecoder_mikmodGetTrack(JNIEnv* env, jclass clazz)
{
	if (module == NULL)
		return -1;
	
	return module->sngpos;
}

jint Java_de_illogical_modo_MikModDecoder_mikmodIsPlayerActive(JNIEnv* env, jclass clazz)
{
	if (module == NULL)
		return 0;
	
	return Player_Active();
}


jint Java_de_illogical_modo_MikModDecoder_mikmodGetSamples(JNIEnv* env, jclass clazz, jshortArray samples)
{
	// samples array has to be 1024 bytes long!!
	
	if (module == NULL)
		return -1;

	// How many samples are needed
	int sampleCount = (*env)->GetArrayLength(env, samples); 
	
	// Fill in local playback
	MikMod_Update();
	
	// copy
	mikmod_android_howmany(sampleCount);
	(*env)->SetShortArrayRegion(env, samples, 0, sampleCount, mikmod_android_getSamples2());

	//__android_log_print(ANDROID_LOG_VERBOSE, "1MIKMOD", "%d %d", module->sngpos, module->patpos);

	return 1;
}



