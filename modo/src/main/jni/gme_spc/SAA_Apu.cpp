// Game_Music_Emu 0.6.0. http://www.slack.net/~ant/

#include "SAA_Apu.h"
#include <stdio.h>

/* Copyright (C) 2006 Shay Green. This module is free software; you
can redistribute it and/or modify it under the terms of the GNU Lesser
General Public License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version. This
module is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
details. You should have received a copy of the GNU Lesser General Public
License along with this module; if not, write to the Free Software Foundation,
Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA */

#include "blargg_source.h"

SAA_Apu::SAA_Apu()
{
	saa = CreateCSAASound();
	saa->SetSoundParameters(SAAP_STEREO | SAAP_16BIT | SAAP_44100 | SAAP_NOFILTER);

	output( 0 );
	volume( 1.0 );
	reset();
}

SAA_Apu::~SAA_Apu()
{
	DestroyCSAASound(saa);
}

void SAA_Apu::reset()
{
	saa->Clear();
	buf_pos = 0;
}

void SAA_Apu::write_data_( int addr, int data )
{
	saa->WriteAddressData(addr, data);
}

void SAA_Apu::run_until( blip_time_t final_end_time )
{
	Blip_Buffer* const oscL = oscs [0];
	
	int sample_end_time = oscL->count_samples( final_end_time );
	
	require( sample_end_time >= buf_pos );
	require( sample_end_time <= SAMPLE_BUFFER_SIZE );
	
	int samples_to_play = sample_end_time - buf_pos;

	if ( samples_to_play > 0 )
	{
		saa->GenerateMany( (unsigned char*) &saa_buffer [buf_pos], samples_to_play ); // one sample in SAA_Emu is 4 bytes => 16bit signed for right + 16bit signed for left channel	
		buf_pos += samples_to_play;
	}
}

void SAA_Apu::end_frame( blip_time_t time )
{
	run_until( time );

	Blip_Buffer* const oscL = oscs [0];
	Blip_Buffer* const oscR = oscs [2];

	// Separate the stereo sample into two buffer
	for ( int i = 0; i < buf_pos; i++ )
	{
		bbuffL[i] = saa_buffer[i][0]; //0.30 * blip_sample_max * vol;
		bbuffR[i] = saa_buffer[i][1]; //0.30 * blip_sample_max * vol;
	}
	
	oscL->mix_samples(bbuffL, buf_pos);
	oscR->mix_samples(bbuffR, buf_pos);
	
	buf_pos = 0;
}

