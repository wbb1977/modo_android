/*	MikMod sound library
	(c) 1998, 1999, 2000 Miodrag Vallat and others - see file AUTHORS for
	complete list.

	This library is free software; you can redistribute it and/or modify
	it under the terms of the GNU Library General Public License as
	published by the Free Software Foundation; either version 2 of
	the License, or (at your option) any later version.

	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU Library General Public License for more details.

	You should have received a copy of the GNU Library General Public
	License along with this library; if not, write to the Free Software
	Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
	02111-1307, USA.
*/

/*==============================================================================

  $Id: drv_win.c,v 1.1.1.1 2004/01/21 01:36:35 raph Exp $

  Output data to PSP audio device

==============================================================================*/

/*

By sweetlilmre 12 November 2005, (mikmod 3.1.11 port), original by Jim Shaw.
Public Domain
*/

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "mikmod_internals.h"
#include <string.h>

#define BUFFERSIZE 32000

static int audio_ready = 0;
static volatile int playing = 0;
SBYTE mikmod_sndbuf[36000];
static int soundboost = 0;
static int calls = 0;
static int count = 0;

void mikmod_android_howmany(int c)
{
	count = c;
}
short* mikmod_android_getSamples2()
{
	return mikmod_sndbuf;
}
/*
void mikmod_android_getSamples(SBYTE* local)
{
	int i = 0;
	for ( i = 0; i < BUFFERSIZE; ++i)
		local[i] = mikmod_sndbuf[i];
	
	calls = 0;
}
*/
void mikmod_android_setBoostLevel(int level)
{
	soundboost = level;
}

static void boostSound(signed short *stream, int len)
{
	if (soundboost == 0)
		return;
		
	int boost = soundboost + 1;
	
	int i = 0;
	for (i = 0; i < len / 2; ++i)
	{
		int ssample = stream[i];
		ssample = ssample * boost;
		
		if (ssample > 32767)
		{
			ssample = 32767;
		} else if (ssample < -32768)
		{
			ssample = -32768;
		}
		stream[i] = ssample;
	}
}


static void ANDROID_Update(void)
{
	if (playing)
	{
		VC_WriteBytes(mikmod_sndbuf, count * 2);
	}
    else
   	{
     // memset(mikmod_sndbuf, 0, BUFFERSIZE * 4);
    }
    
	    
   // boostSound(mikmod_sndbuf, BUFFERSIZE);
}


static BOOL ANDROID_IsThere(void)
{
	return 1;
}


static BOOL ANDROID_Init(void)
{
	audio_ready = 1;
	
	if (VC_Init())
		return 1;
    
	audio_ready = 0;
	
	return 0;
}

static void ANDROID_Exit(void)
{
	audio_ready = 0;
	playing = 0;
	VC_Exit();
}


static BOOL ANDROID_Reset(void)
{
	VC_Exit();
	return VC_Init();
}

static BOOL ANDROID_PlayStart(void)
{
	VC_PlayStart();
	playing = 1;
	return 0;
}

static void ANDROID_PlayStop(void)
{
	playing = 0;
	VC_PlayStop();
}

MIKMODAPI MDRIVER drv_android =
{
	NULL,
	"ANDROID Audio",
	"ANDROID Audio Driver by wb@illogical.de",
	0,255,
	"android",

	NULL,
	ANDROID_IsThere,
	VC_SampleLoad,
	VC_SampleUnload,
	VC_SampleSpace,
	VC_SampleLength,
	ANDROID_Init,
	ANDROID_Exit,
	ANDROID_Reset,
  
	VC_SetNumVoices,
	ANDROID_PlayStart,
	ANDROID_PlayStop,
	ANDROID_Update,
	NULL,
  
	VC_VoiceSetVolume,
	VC_VoiceGetVolume,
	VC_VoiceSetFrequency,
	VC_VoiceGetFrequency,
	VC_VoiceSetPanning,
	VC_VoiceGetPanning,
	VC_VoicePlay,
	VC_VoiceStop,
	VC_VoiceStopped,
	VC_VoiceGetPosition,
	VC_VoiceRealVolume
};
