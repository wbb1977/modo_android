
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_CFLAGS += -D_UNIX -DRARDLL -DBLARGG_LITTLE_ENDIAN -DHAVE_STDINT_H
LOCAL_C_INCLUDES +=  $(NDK_APP_PROJECT_PATH)/jni/unrar $(NDK_APP_PROJECT_PATH)/jni/unzip
LOCAL_CPPFLAGS += -std=gnu++0x -fno-rtti -fno-exceptions
LOCAL_LDLIBS := -lz
LOCAL_SHARED_LIBRARIES := libunzip
LOCAL_MODULE := gme_kode54

# Gbs_Core.cpp Gbs_Cpu.cpp Gbs_Emu.cpp Gb_Apu.cpp Gb_Cpu.cpp Gb_Oscs.cpp
# Kss_Core.cpp Kss_Cpu.cpp Kss_Emu.cpp Kss_Scc_Apu.cpp Hes_Apu.cpp Hes_Apu_Adpcm.cpp Hes_Core.cpp Hes_Cpu.cpp Hes_Emu.cpp
# Sap_Apu.cpp Sap_Core.cpp Sap_Cpu.cpp Sap_Emu.cpp
# Sgc_Core.cpp Sgc_Cpu.cpp Sgc_Emu.cpp Sgc_Impl.cpp
# Ay_Apu.cpp Ay_Core.cpp Ay_Cpu.cpp Ay_Emu.cpp
# Spc_Emu.cpp Spc_Filter.cpp Spc_Sfm.cpp
# higan/smp/smp.cpp higan/processor/spc700/spc700.cpp higan/dsp/SPC_DSP.cpp higan/dsp/dsp.cpp	
# Hes_Apu.cpp Hes_Apu_Adpcm.cpp

# HES support for VGM, so this a backdoor how to play HES
LOCAL_SRC_FILES := gmenative.c Data_Reader.cpp \
	Kss_Core.cpp Kss_Cpu.cpp Kss_Emu.cpp Kss_Scc_Apu.cpp Hes_Apu.cpp Hes_Apu_Adpcm.cpp Hes_Core.cpp Hes_Cpu.cpp Hes_Emu.cpp Ay_Apu.cpp \
	blargg_common.cpp blargg_errors.cpp Blip_Buffer.cpp Bml_Parser.cpp C140_Emu.cpp Classic_Emu.cpp dbopl.cpp \
	Downsampler.cpp	Dual_Resampler.cpp Effects_Buffer.cpp Fir_Resampler.cpp	fmopl.cpp gme.cpp Gme_File.cpp \
	Gme_Loader.cpp	Gym_Emu.cpp K051649_Emu.cpp K053260_Emu.cpp K054539_Emu.cpp M3u_Playlist.cpp Multi_Buffer.cpp \
	Music_Emu.cpp Nes_Apu.cpp Nes_Cpu.cpp Nes_Fds_Apu.cpp Nes_Fme7_Apu.cpp Nes_Namco_Apu.cpp Nes_Oscs.cpp \
	Nes_Vrc6_Apu.cpp Nes_Vrc7_Apu.cpp Nsfe_Emu.cpp Nsf_Core.cpp Nsf_Cpu.cpp	Nsf_Emu.cpp Nsf_Impl.cpp \
	Okim6258_Emu.cpp Okim6295_Emu.cpp Opl_Apu.cpp Pwm_Emu.cpp Qsound_Apu.cpp Resampler.cpp Rf5C164_Emu.cpp \
	Rf5C68_Emu.cpp Rom_Data.cpp SegaPcm_Emu.cpp Sms_Apu.cpp Sms_Fm_Apu.cpp \
	Track_Filter.cpp Upsampler.cpp Vgm_Core.cpp Vgm_Emu.cpp Ym2151_Emu.cpp Ym2203_Emu.cpp \
	Ym2413_Emu.cpp Ym2608_Emu.cpp Ym2610b_Emu.cpp Ym2612_Emu.cpp Ym3812_Emu.cpp ymdeltat.cpp \
	Ymf262_Emu.cpp Ymz280b_Emu.cpp Z80_Cpu.cpp  c140.c dac_control.c fm.c fm2612.c k051649.c k053260.c \
	k054539.c okim6258.c okim6295.c pwm.c qmix.c rf5c68.c scd_pcm.c	segapcm.c s_deltat.c s_logtbl.c \
	s_opl.c	s_opltbl.c ym2151.c ym2413.c ymz280b.c
 
include $(BUILD_SHARED_LIBRARY)
