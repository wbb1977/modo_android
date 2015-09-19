#ifndef SAA_APU_H
#define SAA_APU_H

#include "blargg_common.h"
#include "Blip_Buffer.h"
#include "SAASound.h"

class SAA_Apu {
public:
	// Set buffer to generate all sound into, or disable sound if NULL
	void output( Blip_Buffer* );
	
	// Reset sound chip
	void reset();
	
	// Write to register at specified time
	void write( blip_time_t time, int addr, int data );
	
	// Run sound to specified time, end current time frame, then start a new
	// time frame at time 0. Time frames have no effect on emulation and each
	// can be whatever length is convenient.
	void end_frame( blip_time_t length );
	
// Additional features
	
	// Set sound output of specific oscillator to buffer, where index is
	// 0, 1, or 2. If buffer is NULL, the specified oscillator is muted.
	enum { osc_count = 3 };
	void osc_output( int index, Blip_Buffer* );
	
	// Set overall volume (default is 1.0)
	void volume( double );
	
	// Set treble equalization (see documentation)
	void treble_eq( blip_eq_t const& );
	
	SAA_Apu();
	~SAA_Apu();

private:
	
	LPCSAASOUND saa;
	double vol;
	int buf_pos;

	enum { SAMPLE_BUFFER_SIZE = 25000 };  // very high, not fine tuned

	short saa_buffer[SAMPLE_BUFFER_SIZE] [2]; // stereo
	blip_sample_t bbuffL[SAMPLE_BUFFER_SIZE];
	blip_sample_t bbuffR[SAMPLE_BUFFER_SIZE];

	Blip_Buffer* oscs [osc_count];
	
	void run_until( blip_time_t );
	void write_data_( int addr, int data );
};

inline void SAA_Apu::volume( double v ) { vol = v; }

inline void SAA_Apu::treble_eq( blip_eq_t const& eq )
{
	// not sure what to do here at the moment
}

inline void SAA_Apu::write( blip_time_t time, int addr, int data )
{
	run_until( time );
	write_data_( addr, data );
}
inline void SAA_Apu::osc_output( int i, Blip_Buffer* buf )
{
	assert( (unsigned) i < osc_count );
	oscs [i] = buf;
}

inline void SAA_Apu::output( Blip_Buffer* buf )
{
	osc_output( 0, buf );
	osc_output( 1, buf );
	osc_output( 2, buf );
}

#endif
