/*	MikMod sound library
	(c) 1998, 1999, 2000, 2001, 2002 Miodrag Vallat and others - see file
	AUTHORS for complete list.

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

  $Id: load_med.c,v 1.1.1.1 2004/01/21 01:36:35 raph Exp $

  Amiga MED module loader

==============================================================================*/

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#ifdef HAVE_UNISTD_H
#include <unistd.h>
#endif

#include <stdio.h>
#ifdef HAVE_MEMORY_H
#include <memory.h>
#endif
#include <string.h>

#include "mikmod_internals.h"

#ifdef SUNOS
extern int fprintf(FILE *, const char *, ...);
#endif

/*========== Module information */

typedef struct MEDHEADER {
	ULONG id;
	ULONG modlen;
	ULONG MEDSONGP;				/* struct MEDSONG *song; */
	UWORD psecnum;				/* for the player routine, MMD2 only */
	UWORD pseq;					/*  "   "   "   " */
	ULONG MEDBlockPP;			/* struct MEDBlock **blockarr; */
	ULONG reserved1;
	ULONG MEDINSTHEADERPP;		/* struct MEDINSTHEADER **smplarr; */
	ULONG reserved2;
	ULONG MEDEXPP;				/* struct MEDEXP *expdata; */
	ULONG reserved3;
	UWORD pstate;				/* some data for the player routine */
	UWORD pblock;
	UWORD pline;
	UWORD pseqnum;
	SWORD actplayline;
	UBYTE counter;
	UBYTE extra_songs;			/* number of songs - 1 */
} MEDHEADER;

typedef struct MEDSAMPLE {
	UWORD rep, replen;			/* offs: 0(s), 2(s) */
	UBYTE midich;				/* offs: 4(s) */
	UBYTE midipreset;			/* offs: 5(s) */
	UBYTE svol;					/* offs: 6(s) */
	SBYTE strans;				/* offs: 7(s) */
} MEDSAMPLE;

typedef struct MEDSONG {
	MEDSAMPLE sample[63];		/* 63 * 8 bytes = 504 bytes */
	UWORD numblocks;			/* offs: 504 */
	UWORD songlen;				/* offs: 506 */
	UBYTE playseq[256];			/* offs: 508 */
	UWORD deftempo;				/* offs: 764 */
	SBYTE playtransp;			/* offs: 766 */
	UBYTE flags;				/* offs: 767 */
	UBYTE flags2;				/* offs: 768 */
	UBYTE tempo2;				/* offs: 769 */
	UBYTE trkvol[16];			/* offs: 770 */
	UBYTE mastervol;			/* offs: 786 */
	UBYTE numsamples;			/* offs: 787 */
} MEDSONG;

typedef struct MEDSONG2 {
	MEDSAMPLE sample[63];		/* 63 * 8 bytes = 504 bytes */
	UWORD numblocks;			/* offs: 504 */
	UWORD songlen;				/* offs: 506 */
	ULONG PlaySeq;				/* offs: 508 */
	ULONG sectiontable;			/* offs: 516 */
	ULONG trackvols;			/* offs: 518 */
	UWORD numtracks;			/* offs: 520 */
	UWORD numpseqs;				/* offs: 522 */
	ULONG trackpans;			/* offs: 524 */
	ULONG flags3;				/* offs: 526 */
	UWORD voladj;				/* offs: 534 */
	UWORD channels;				/* offs: 536 */
	UBYTE mix_echotype;			/* offs: 538 */
	UBYTE mix_echodepth;		/* offs: 539 */
	UWORD mix_echolen;			/* offs: 540 */
	SBYTE mix_stereosep;		/* offs: 542 */
	UBYTE pad[223];				/* offs: 543 */
	
	UWORD deftempo;				/* offs: 764 */
	SBYTE playtransp;			/* offs: 766 */
	UBYTE flags;				/* offs: 767 */
	UBYTE flags2;				/* offs: 768 */
	UBYTE tempo2;				/* offs: 769 */
	UBYTE pad2[16];				/* offs: 770 */
	UBYTE mastervol;			/* offs: 786 */
	UBYTE numsamples;			/* offs: 787 */
} MEDSONG2;


typedef struct MEDEXP {
	ULONG nextmod;				/* pointer to next module */
	ULONG exp_smp;				/* pointer to MEDINSTEXT array */
	UWORD s_ext_entries;
	UWORD s_ext_entrsz;
	ULONG annotxt;				/* pointer to annotation text */
	ULONG annolen;
	ULONG iinfo;				/* pointer to MEDINSTINFO array */
	UWORD i_ext_entries;
	UWORD i_ext_entrsz;
	ULONG jumpmask;
	ULONG rgbtable;
	ULONG channelsplit;
	ULONG n_info;
	ULONG songname;				/* pointer to songname */
	ULONG songnamelen;
	ULONG dumps;
	ULONG mddinfo;				/* pointer to attached text file */
	ULONG reserved2[6];
} MEDEXP;

typedef struct MEDINFO
{
	ULONG next;					/* pointer to next attached text, unused? */
	UWORD reserved;
	UWORD type;					/* 1 = ascii */
	ULONG length;
} MEDINFO;

typedef struct MMD0NOTE {
	UBYTE a, b, c;
} MMD0NOTE;

typedef struct MMD1NOTE {
	UBYTE a, b, c, d;
} MMD1NOTE;

typedef struct MEDINSTHEADER {
	ULONG length;
	SWORD type;
	/* Followed by actual data */
} MEDINSTHEADER;

typedef struct MEDINSTEXT {
	UBYTE hold;
	UBYTE decay;
	UBYTE suppress_midi_off;
	SBYTE finetune;
	UBYTE default_pitch;
} MEDINSTEXT;

typedef struct MEDINSTINFO {
	UBYTE name[40];
} MEDINSTINFO;

/*========== Loader variables */

#define MMD0_string 0x4D4D4430
#define MMD1_string 0x4D4D4431
#define MMD2_string 0x4D4D4432

static MEDHEADER *mh = NULL;
static MEDSONG *ms = NULL;
//static MEDSONG2 *ms2 = NULL;
static MEDEXP *me = NULL;
static ULONG *ba = NULL;
static MMD0NOTE *mmd0pat = NULL;
static MMD1NOTE *mmd1pat = NULL;

static BOOL decimalvolumes;
static BOOL bpmtempos;

#define d0note(row,col) mmd0pat[((row)*(UWORD)of.numchn)+(col)]
#define d1note(row,col) mmd1pat[((row)*(UWORD)of.numchn)+(col)]

static CHAR MED_Version[] = "OctaMED (MMDx)";

/*========== Loader code */

BOOL MED_Test(void)
{
	UBYTE id[4];

	if (!_mm_read_UBYTES(id, 4, modreader))
		return 0;
//	if ((!memcmp(id, "MMD0", 4)) || (!memcmp(id, "MMD1", 4)) || (!memcmp(id, "MMD2", 4)))
	if ((!memcmp(id, "MMD0", 4)) || (!memcmp(id, "MMD1", 4)))
		return 1;
	return 0;
}

BOOL MED_Init(void)
{
	if (!(me = (MEDEXP *)_mm_malloc(sizeof(MEDEXP))))
		return 0;
	if (!(mh = (MEDHEADER *)_mm_malloc(sizeof(MEDHEADER))))
		return 0;
	if (!(ms = (MEDSONG *)_mm_malloc(sizeof(MEDSONG))))
		return 0;
//	if (!(ms2 = (MEDSONG2 *)_mm_malloc(sizeof(MEDSONG2))))
//		return 0;
	return 1;
}

void MED_Cleanup(void)
{
	_mm_free(me);
	_mm_free(mh);
	_mm_free(ms);
//	_mm_free(ms2);
	_mm_free(ba);
	_mm_free(mmd0pat);
	_mm_free(mmd1pat);
}

static void EffectCvt(UBYTE eff, UBYTE dat)
{
	switch (eff)
	{
		case 0x05: /* PT vibrato with speed/depth nibbles swapped */
			UniPTEffect(0x4, (dat >> 4) | ((dat & 0xf) << 4));
			break;

		case 0x00: /* Arpeggio */
		case 0x01: /* Slide pitch up */
		case 0x02: /* Slide pitch down */
		case 0x03: /* Portamento (slide to note) */
		case 0x04: /* Vibrato (pitch) */
	//	case 0x05: /* Continue Portamento + volume slide */
		case 0x06: /* Continue Vibrato + volumen slide */
		case 0x07: /* Tremolo (volume) */
 			UniPTEffect(eff, dat);
			break;
		case 0x09: /* Secondary Temo (number of ticks) [arg must be between 0 - 20, as stated in commands.info [on octamed v4 amiga disk]] */
			if (bpmtempos) {
				if (!dat)
					dat = of.initspeed;
				UniEffect(UNI_S3MEFFECTA, dat);
			} else {
				if (dat <= 0x20) {
					if (!dat)
						dat = of.initspeed;
					UniPTEffect(0xf, dat);
				} else
					UniEffect(UNI_MEDSPEED, ((UWORD)dat * 125) / (33 * 4));
			}
			break;
		case 0x0B: /* Position jump */
 			UniPTEffect(eff, dat);
			break;
		case 0x0C: /* Set Volume */
			if (decimalvolumes)
				dat = (dat >> 4) * 10 + (dat & 0xf);
			UniPTEffect(eff, dat);
			break;
		case 0x0D: /* Volume slide */
			UniPTEffect(0x0A, dat);
			break;
		case 0x0F: /* Misc cmds */
			switch (dat)
			{
				case 0x00:  /* After this note, goto next pattern (patternbreak) */
					UniPTEffect(0x0D, 0);
					break;
				case 0x01 ... 0xF0: /* Set Primary Tempo (0 - 10 sets ticks per line, 11 - 240 sets bpm) */
					if (dat <= 10)
						UniPTEffect(0xf, dat);
					else if (dat <= 240) {
						if (bpmtempos)
							UniPTEffect(0xf, (dat < 32) ? 32 : dat);
						else
							UniEffect(UNI_MEDSPEED, ((UWORD)dat * 125) / 33);
						}
					break;
				case 0xF1: /* Play note twice */
					UniWriteByte(UNI_MEDEFFECTF1);
					break;
				case 0xF2: /* Delay note */
					UniWriteByte(UNI_MEDEFFECTF2);
					break;
				case 0xF3: /* Play note three times */
					UniWriteByte(UNI_MEDEFFECTF3);
					break;
				case 0xFD: /* Set current note to given pitch */
					UniWriteByte(UNI_MEDEFFECTFD);
					break;
				case 0xFE: /* Stop playback (jump beyond song length) */
					UniPTEffect(0x0B, of.numpat);
					break;
				case 0xFF: /* Cut note */
					UniPTEffect(0x0C, -1);
					break;
			}
			break;
		case 0x11: /* Fine slide up (pitch) */
		  	UniPTEffect(0x0E, (0x01 << 4) | (dat & 0xf));
		  	break;
		case 0x12: /* Fine slide down (pitch) */
		  	UniPTEffect(0x0E, (0x02 << 4) | (dat & 0xf));
		  	break;
		case 0x14: /* Vibrate, depth is halfed */
			UniPTEffect(0x04, (dat << 4) | ((dat & 0xf) / 2));
			break;
		case 0x15: /* Set finetune */
		  	UniPTEffect(0x0E, (0x05 << 4) | (dat & 0xf));
		  	break;
		case 0x16: /* loop */
		  	UniPTEffect(0x0E, (0x06 << 4) | (dat & 0xf));
		  	break;
		case 0x18: /* Stop note */
		  	UniPTEffect(0x0E, (0x0C << 4) | (dat & 0xf));
		 	break;
		case 0x19: /* Set sample offset */
		  	UniPTEffect(0x09, dat);
		 	break;
		case 0x1A: /* Slide Volume up once [can vol here also be given in hex?] */
		  	UniPTEffect(0x0E, (0x0A << 4) | (dat & 0xf));
		 	break;
		case 0x1B: /* Slide Volume down once [can vol here also be given in hex?] */
		  	UniPTEffect(0x0E, (0x0B << 4) | (dat & 0xf));
		 	break;
		case 0x1D: /* Pattern break [?can in MED greater than 4 bits?] */
			UniPTEffect(0x0D, dat);
			break;
		case 0x1E: /* Play line x times (pattern delay) [?can in MED greater than 4 bits?] */
		  	UniPTEffect(0x0E, (0x0E << 4) | (dat & 0xf));
		 	break;
		case 0x1F: /* Delay and then retrigger note */
			UniWriteByte(UNI_MEDEFFECT1F);
			UniWriteByte(dat);
		 	break;
	}
}


static UBYTE *MED_Convert1(int count, int col)
{
	int t;
	UBYTE inst, note, eff, dat;
	MMD1NOTE *n;

	UniReset();
	for (t = 0; t < count; t++) {
		n = &d1note(t, col);

		note = n->a & 0x7f;
		inst = n->b & 0x3f;
		eff = n->c; /* MMD1 uses 8 bits for effect number */
		dat = n->d;

		int transpose = 0;
		
		if (inst)
		{
			UniInstrument(inst - 1);
			transpose = ms->sample[inst - 1].strans;
		}
		
					
		if (note)
		{		
			/* Restrict playback to octave 3 */		
			note += transpose;

			if (note > 36)
				while (note > 36)
					note -= OCTAVE;
					
//			UniNote(note + ((3 * OCTAVE) + transpose) - 1);
			UniNote(note + 3 * OCTAVE - 1);
		}
		EffectCvt(eff, dat);
		UniNewline();
	}
	return UniDup();
}

static UBYTE *MED_Convert0(int count, int col)
{
	int t;
	UBYTE a, b, inst, note, eff, dat;
	MMD0NOTE *n;

	UniReset();
	for (t = 0; t < count; t++) {
		n = &d0note(t, col);
		a = n->a;
		b = n->b;

		note = a & 0x3f;
		a >>= 6;
		a = ((a & 1) << 1) | (a >> 1);
		inst = (b >> 4) | (a << 4);
		eff = b & 0xf;
		dat = n->c;

		if (inst)
			UniInstrument(inst - 1);
		if (note)
		if (note)
		{	
			/* Restrict playback to octave 3 */	
			if (note > 36)
				while (note > 36)
					note -= OCTAVE;
			UniNote(note + 3 * OCTAVE - 1);
		}
		EffectCvt(eff, dat);
		UniNewline();
	}
	return UniDup();
}

static BOOL LoadMEDPatterns(void)
{
	int t, row, col;
	UWORD numtracks, numlines, maxlines = 0, track = 0;
	MMD0NOTE *mmdp;

	/* first, scan patterns to see how many channels are used */
	for (t = 0; t < of.numpat; t++) {
		_mm_fseek(modreader, ba[t], SEEK_SET);
		numtracks = _mm_read_UBYTE(modreader);
		numlines = _mm_read_UBYTE(modreader);

		if (numtracks > of.numchn)
			of.numchn = numtracks;
		if (numlines > maxlines)
			maxlines = numlines;
	}

	of.numtrk = of.numpat * of.numchn;
	if (!AllocTracks())
		return 0;
	if (!AllocPatterns())
		return 0;

	if (!
		(mmd0pat =
		 (MMD0NOTE *)_mm_calloc(of.numchn * (maxlines + 1),
								sizeof(MMD0NOTE)))) return 0;

	/* second read: read and convert patterns */
	for (t = 0; t < of.numpat; t++) {
		_mm_fseek(modreader, ba[t], SEEK_SET);
		numtracks = _mm_read_UBYTE(modreader);
		numlines = _mm_read_UBYTE(modreader);

		of.pattrows[t] = ++numlines;
		memset(mmdp = mmd0pat, 0, of.numchn * maxlines * sizeof(MMD0NOTE));
		for (row = numlines; row; row--) {
			for (col = numtracks; col; col--, mmdp++) {
				mmdp->a = _mm_read_UBYTE(modreader);
				mmdp->b = _mm_read_UBYTE(modreader);
				mmdp->c = _mm_read_UBYTE(modreader);
			}
		}

		for (col = 0; col < of.numchn; col++)
			of.tracks[track++] = MED_Convert0(numlines, col);
	}
	return 1;
}

static BOOL LoadMMD1Patterns(void)
{
	int t, row, col;
	UWORD numtracks, numlines, maxlines = 0, track = 0;
	MMD1NOTE *mmdp;

	/* first, scan patterns to see how many channels are used */
	for (t = 0; t < of.numpat; t++) {
		_mm_fseek(modreader, ba[t], SEEK_SET);
		numtracks = _mm_read_M_UWORD(modreader);
		numlines = _mm_read_M_UWORD(modreader);
		if (numtracks > of.numchn)
			of.numchn = numtracks;
		if (numlines > maxlines)
			maxlines = numlines;
	}

	of.numtrk = of.numpat * of.numchn;
	if (!AllocTracks())
		return 0;
	if (!AllocPatterns())
		return 0;

	if (!
		(mmd1pat =
		 (MMD1NOTE *)_mm_calloc(of.numchn * (maxlines + 1),
								sizeof(MMD1NOTE)))) return 0;

	/* second read: really read and convert patterns */
	for (t = 0; t < of.numpat; t++) {
		_mm_fseek(modreader, ba[t], SEEK_SET);
		numtracks = _mm_read_M_UWORD(modreader);
		numlines = _mm_read_M_UWORD(modreader);

		_mm_fseek(modreader, sizeof(ULONG), SEEK_CUR);
		of.pattrows[t] = ++numlines;
		memset(mmdp = mmd1pat, 0, of.numchn * maxlines * sizeof(MMD1NOTE));

		for (row = numlines; row; row--) {
			for (col = numtracks; col; col--, mmdp++) {
				mmdp->a = _mm_read_UBYTE(modreader);
				mmdp->b = _mm_read_UBYTE(modreader);
				mmdp->c = _mm_read_UBYTE(modreader);
				mmdp->d = _mm_read_UBYTE(modreader);
			}
		}

		for (col = 0; col < of.numchn; col++)
			of.tracks[track++] = MED_Convert1(numlines, col);
	}
	return 1;
}

BOOL MED_Load(BOOL curious)
{
	int t;
	ULONG sa[64];
	MEDINSTHEADER s;
	SAMPLE *q;
	MEDSAMPLE *mss;

	/* try to read module header */
	mh->id = _mm_read_M_ULONG(modreader);
	mh->modlen = _mm_read_M_ULONG(modreader);
	mh->MEDSONGP = _mm_read_M_ULONG(modreader);
	mh->psecnum = _mm_read_M_UWORD(modreader);
	mh->pseq = _mm_read_M_UWORD(modreader);
	mh->MEDBlockPP = _mm_read_M_ULONG(modreader);
	mh->reserved1 = _mm_read_M_ULONG(modreader);
	mh->MEDINSTHEADERPP = _mm_read_M_ULONG(modreader);
	mh->reserved2 = _mm_read_M_ULONG(modreader);
	mh->MEDEXPP = _mm_read_M_ULONG(modreader);
	mh->reserved3 = _mm_read_M_ULONG(modreader);
	mh->pstate = _mm_read_M_UWORD(modreader);
	mh->pblock = _mm_read_M_UWORD(modreader);
	mh->pline = _mm_read_M_UWORD(modreader);
	mh->pseqnum = _mm_read_M_UWORD(modreader);
	mh->actplayline = _mm_read_M_SWORD(modreader);
	mh->counter = _mm_read_UBYTE(modreader);
	mh->extra_songs = _mm_read_UBYTE(modreader);

	/* Seek to MEDSONG struct */
	_mm_fseek(modreader, mh->MEDSONGP, SEEK_SET);

	if (mh->id == MMD2_string)
	{
	/*
		mss = ms->sample;		
		for (t = 63; t; t--, mss++) {
			printf("t: %d, %x\n", t, _mm_ftell(modreader));
			mss->rep = _mm_read_M_UWORD(modreader);
			mss->replen = _mm_read_M_UWORD(modreader);
			mss->midich = _mm_read_UBYTE(modreader);
			mss->midipreset = _mm_read_UBYTE(modreader);
			mss->svol = _mm_read_UBYTE(modreader);
			mss->strans = _mm_read_SBYTE(modreader);
		}
	
		printf("mh_start: %x\n", mh->MEDSONGP);
		printf("song start: %x\n", _mm_ftell(modreader));
		ms->numblocks = _mm_read_M_UWORD(modreader);
		ms->songlen = _mm_read_M_UWORD(modreader);
		ms2->PlaySeq = _mm_read_M_ULONG(modreader);
		ms2->sectiontable = _mm_read_M_ULONG(modreader);
		ms2->trackvols = _mm_read_M_ULONG(modreader);
		ms2->numtracks = _mm_read_M_UWORD(modreader);
		ms2->numpseqs = _mm_read_M_UWORD(modreader);
		ms2->trackpans = _mm_read_M_ULONG(modreader);
		ms2->flags3 = _mm_read_M_ULONG(modreader);
		ms2->voladj = _mm_read_M_UWORD(modreader);
		ms2->channels = _mm_read_M_UWORD(modreader);
		ms2->mix_echotype = _mm_read_UBYTE(modreader);
		ms2->mix_echodepth = _mm_read_UBYTE(modreader);
		ms2->mix_echolen = _mm_read_M_UWORD(modreader);
		ms2->mix_stereosep = _mm_read_UBYTE(modreader);
		_mm_read_UBYTES(ms2->pad, 223, modreader);
		ms2->deftempo = _mm_read_M_UWORD(modreader);
		ms2->playtransp = _mm_read_SBYTE(modreader);
		ms2->flags = _mm_read_UBYTE(modreader);
		ms2->flags2 = _mm_read_UBYTE(modreader);
		ms2->tempo2 = _mm_read_UBYTE(modreader);
		_mm_read_UBYTES(ms2->pad2, 16, modreader);
		ms2->mastervol = _mm_read_UBYTE(modreader);
		ms2->numsamples = _mm_read_UBYTE(modreader);
				
		// Map data to medsong mmd0/1 header
		
		ms->deftempo = ms2->deftempo;
		ms->playtransp = ms2->playtransp;
		ms->flags = ms2->flags;
		ms->flags2 = ms2->flags;
		ms->tempo2 = ms2->tempo2;
		memset(ms->trkvol, 0, 16); // not used anyway
		ms->mastervol = ms2->mastervol;
		ms->numsamples = ms2->numsamples;
		
		
		_mm_fseek(modreader, ms2->sectiontable, SEEK_SET);
		
		int i = 0;
		for (i = 0; i < ms->songlen; ++i)
			printf("%i: %d\n", i, _mm_read_M_UWORD(modreader));
		
		printf("Playseq: %x\n", ms2->PlaySeq);
		printf("Num of blocks: %d\n", ms->numblocks);
		printf("songlen: %d\n", ms->songlen);
		printf("Num of tracks: %d\n", ms2->numtracks);
		printf("Num of seqs: %d\n", ms2->numpseqs);
		exit(1);
		*/
	} else
	{

	/* Load the MED Song Header */
	mss = ms->sample;			/* load the sample data first */
	for (t = 63; t; t--, mss++) {
		mss->rep = _mm_read_M_UWORD(modreader);
		mss->replen = _mm_read_M_UWORD(modreader);
		mss->midich = _mm_read_UBYTE(modreader);
		mss->midipreset = _mm_read_UBYTE(modreader);
		mss->svol = _mm_read_UBYTE(modreader);
		mss->strans = _mm_read_SBYTE(modreader);
//		mss->strans = -11;
		printf("Strans: %d %d\n",t, mss->strans);
	}
//	exit(1);
	ms->numblocks = _mm_read_M_UWORD(modreader);
	ms->songlen = _mm_read_M_UWORD(modreader);
	_mm_read_UBYTES(ms->playseq, 256, modreader);
	ms->deftempo = _mm_read_M_UWORD(modreader);
	ms->playtransp = _mm_read_SBYTE(modreader);
	ms->flags = _mm_read_UBYTE(modreader);
	ms->flags2 = _mm_read_UBYTE(modreader);
	ms->tempo2 = _mm_read_UBYTE(modreader);
	_mm_read_UBYTES(ms->trkvol, 16, modreader);
	ms->mastervol = _mm_read_UBYTE(modreader);
	ms->numsamples = _mm_read_UBYTE(modreader);
	
	}
	/* check for a bad header */
	if (_mm_eof(modreader)) {
		_mm_errno = MMERR_LOADING_HEADER;
		return 0;
	}

	/* load extension structure */
	if (mh->MEDEXPP) {
		_mm_fseek(modreader, mh->MEDEXPP, SEEK_SET);
		me->nextmod = _mm_read_M_ULONG(modreader);
		me->exp_smp = _mm_read_M_ULONG(modreader);
		me->s_ext_entries = _mm_read_M_UWORD(modreader);
		me->s_ext_entrsz = _mm_read_M_UWORD(modreader);
		me->annotxt = _mm_read_M_ULONG(modreader);
		me->annolen = _mm_read_M_ULONG(modreader);
		me->iinfo = _mm_read_M_ULONG(modreader);
		me->i_ext_entries = _mm_read_M_UWORD(modreader);
		me->i_ext_entrsz = _mm_read_M_UWORD(modreader);
		me->jumpmask = _mm_read_M_ULONG(modreader);
		me->rgbtable = _mm_read_M_ULONG(modreader);
		me->channelsplit = _mm_read_M_ULONG(modreader);
		me->n_info = _mm_read_M_ULONG(modreader);
		me->songname = _mm_read_M_ULONG(modreader);
		me->songnamelen = _mm_read_M_ULONG(modreader);
		me->dumps = _mm_read_M_ULONG(modreader);
		me->mddinfo = _mm_read_M_ULONG(modreader);
	}

	/* seek to and read the samplepointer array */
	_mm_fseek(modreader, mh->MEDINSTHEADERPP, SEEK_SET);
	if (!_mm_read_M_ULONGS(sa, ms->numsamples, modreader)) {
		_mm_errno = MMERR_LOADING_HEADER;
		return 0;
	}

	/* alloc and read the blockpointer array */
	if (!(ba = (ULONG *)_mm_calloc(ms->numblocks, sizeof(ULONG))))
		return 0;
	_mm_fseek(modreader, mh->MEDBlockPP, SEEK_SET);
	if (!_mm_read_M_ULONGS(ba, ms->numblocks, modreader)) {
		_mm_errno = MMERR_LOADING_HEADER;
		return 0;
	}

	/* copy song positions */
	if (!AllocPositions(ms->songlen))
		return 0;
	for (t = 0; t < ms->songlen; t++)
		of.positions[t] = ms->playseq[t];

	decimalvolumes = (ms->flags & 0x10) ? 0 : 1;
	bpmtempos = (ms->flags2 & 0x20) ? 1 : 0;

	if (bpmtempos) {
//		printf("BPM mode\n");
		int bpmlen = (ms->flags2 & 0x1f) + 1;
		
		of.initspeed = ms->tempo2;
		of.inittempo = ms->deftempo * bpmlen / 4;

//		printf("ms->tempo2: %d, bpmlen: %d, bpms: %d\n", ms->tempo2, bpmlen, ms->deftempo);
		if (bpmlen != 4) {
		
			/* Lets approach the problem the stupid way */
			/*
			int ticks_goal = ms->deftempo * bpmlen * ms->tempo2;
			
			int bpm_current = 40;
			int tempo_current = ms->tempo2;
			int found = 0;
			while (tempo_current > 0 && tempo_current < 11 && found == 0)
			{
				for (bpm_current = 32; found == 0 && bpm_current <= 240; ++bpm_current)
				{
					if ((bpm_current * 4 * tempo_current) > ticks_goal)
					{
						printf("Goal: %d  vs  %d\n", ticks_goal, (bpm_current) * 4 * tempo_current);
						found = 1;
					}
				}
				tempo_current += (bpmlen > 4) ? 1 : -1;
			}
			
			if (tempo_current <= 0)
				tempo_current = 1;
			if (tempo_current >= 11)
				tempo_current = 10;
			
			of.initspeed = (ms->tempo2 - tempo_current) + ms->tempo2;
			of.inittempo = bpm_current - 1;
			of.flags |= UF_HIGHBPM;

		*/
			/* Let's do some math : compute GCD of BPM beat length and speed */
			int a, b;

			a = bpmlen;
			b = ms->tempo2;

			if (a > b) {
				t = b;
				b = a;
				a = t;
			}
			while ((a != b) && (a)) {
				t = a;
				a = b - a;
				b = t;
				if (a > b) {
					t = b;
					b = a;
					a = t;
				}
			}

//			printf("B: %d\n", b);
			of.initspeed /= b;
			of.inittempo = ms->deftempo * bpmlen / (4 * b);

//			printf("Neue daten: tempo: %d, bpm: %d\n", of.initspeed, of.inittempo);
		}
	} else {
//		printf("non BPM mode\n");
	
		of.initspeed = ms->tempo2;
		of.inittempo = ms->deftempo ? ((UWORD)ms->deftempo * 125) / 33 : 128;
		if ((ms->deftempo <= 10) && (ms->deftempo))
			of.inittempo = (of.inittempo * 33) / 6;
		of.flags |= UF_HIGHBPM;
	}
	MED_Version[12] = mh->id;
	of.modtype = strdup(MED_Version);
	of.numchn = 0;				/* will be counted later */
	of.numpat = ms->numblocks;
	of.numpos = ms->songlen;
	of.numins = ms->numsamples;
	of.numsmp = of.numins;
	of.reppos = 0;
	if ((mh->MEDEXPP) && (me->songname) && (me->songnamelen)) {
		char *name;

		_mm_fseek(modreader, me->songname, SEEK_SET);
		name = _mm_malloc(me->songnamelen);
		_mm_read_UBYTES(name, me->songnamelen, modreader);
		of.songname = DupStr(name, me->songnamelen, 1);
		free(name);
	} else
		of.songname = DupStr(NULL, 0, 0);
	if ((mh->MEDEXPP) && (me->annotxt) && (me->annolen)) {
		_mm_fseek(modreader, me->annotxt, SEEK_SET);
		ReadComment(me->annolen);
	}
	
	/*
	if (mh->MEDEXPP && me->mddinfo)
	{
		MEDINFO mi;
		_mm_fseek(modreader, me->mddinfo, SEEK_SET);
		mi.next = _mm_read_M_ULONG(modreader);
		mi.reserved = _mm_read_M_UWORD(modreader);
		mi.type = _mm_read_M_UWORD(modreader);
		mi.length = _mm_read_M_ULONG(modreader);

		printf("MMDInfo: Type: %d, length: %d\n", mi.type, mi.length);
	} else
	{
		printf("No MDDINFO found!\n");
	}
*/

	if (!AllocSamples())
		return 0;
	q = of.samples;
	for (t = 0; t < of.numins; t++) {
		q->flags = SF_SIGNED;
		q->volume = 64;
		if (sa[t]) {
			_mm_fseek(modreader, sa[t], SEEK_SET);
			s.length = _mm_read_M_ULONG(modreader);
			s.type = _mm_read_M_SWORD(modreader);

			if (s.type) {
#ifdef MIKMOD_DEBUG
				fprintf(stderr, "\rNon-sample instruments not supported in MED loader yet\n");
#endif
				if (!curious) {
					_mm_errno = MMERR_MED_SYNTHSAMPLES;
					return 0;
				}
				s.length = 0;
			}

			if (_mm_eof(modreader)) {
				_mm_errno = MMERR_LOADING_SAMPLEINFO;
				return 0;
			}

			q->length = s.length;
			q->seekpos = _mm_ftell(modreader);
			q->loopstart = ms->sample[t].rep << 1;
			q->loopend = q->loopstart + (ms->sample[t].replen << 1);

			if (ms->sample[t].replen > 1)
				q->flags |= SF_LOOP;

			/* don't load sample if length>='MMD0'...
			   such kluges make libmikmod's code unique !!! */
			if (q->length >= MMD0_string)
				q->length = 0;
		} else
			q->length = 0;

		if ((mh->MEDEXPP) && (me->exp_smp) &&
			(t < me->s_ext_entries) && (me->s_ext_entrsz >= 4)) {
			MEDINSTEXT ie;

			_mm_fseek(modreader, me->exp_smp + t * me->s_ext_entrsz,
					  SEEK_SET);
			ie.hold = _mm_read_UBYTE(modreader);
			ie.decay = _mm_read_UBYTE(modreader);
			ie.suppress_midi_off = _mm_read_UBYTE(modreader);
			ie.finetune = _mm_read_SBYTE(modreader);
/*			if (me->s_ext_entrsz >= 5)
			{
				ie.default_pitch = _mm_read_UBYTE(modreader);
			}
*/
			q->speed = finetune[ie.finetune & 0xf];
		} else
			q->speed = 8363;

		if ((mh->MEDEXPP) && (me->iinfo) &&
			(t < me->i_ext_entries) && (me->i_ext_entrsz >= 40)) {
			MEDINSTINFO ii;

			_mm_fseek(modreader, me->iinfo + t * me->i_ext_entrsz, SEEK_SET);
			_mm_read_UBYTES(ii.name, 40, modreader);
			q->samplename = DupStr((char*)ii.name, 40, 1);
		} else
			q->samplename = NULL;

		q++;
	}

	if (mh->id == MMD0_string) {
		if (!LoadMEDPatterns()) {
			_mm_errno = MMERR_LOADING_PATTERN;
			return 0;
		}
	} else if (mh->id == MMD1_string) {
		if (!LoadMMD1Patterns()) {
			_mm_errno = MMERR_LOADING_PATTERN;
			return 0;
		}
	} else {
		_mm_errno = MMERR_NOT_A_MODULE;
		return 0;
	}
	
	return 1;
}

CHAR *MED_LoadTitle(void)
{
	ULONG posit, namelen;
	CHAR *name, *retvalue = NULL;
	
	_mm_fseek(modreader, 0x20, SEEK_SET);
	posit = _mm_read_M_ULONG(modreader);
	
	if (posit) {
		_mm_fseek(modreader, posit + 0x2C, SEEK_SET);
		posit = _mm_read_M_ULONG(modreader);
		namelen = _mm_read_M_ULONG(modreader);

		_mm_fseek(modreader, posit, SEEK_SET);
		name = _mm_malloc(namelen);
		_mm_read_UBYTES(name, namelen, modreader);
		retvalue = DupStr(name, namelen, 1);
		free(name);
	}

	return retvalue;
}

/*========== Loader information */

MIKMODAPI MLOADER load_med = {
	NULL,
	"MED",
	"MED (OctaMED)",
	MED_Init,
	MED_Test,
	MED_Load,
	MED_Cleanup,
	MED_LoadTitle
};

/* ex:set ts=4: */
