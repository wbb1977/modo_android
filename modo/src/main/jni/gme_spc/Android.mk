   
   LOCAL_PATH := $(call my-dir)
   include $(CLEAR_VARS)

   #RSN support
   LOCAL_CFLAGS += -D_UNIX -DRARDLL  -DBLARGG_LITTLE_ENDIAN -DHAVE_STDINT_H -fno-rtti -fno-exceptions
   LOCAL_C_INCLUDES +=  $(NDK_APP_PROJECT_PATH)/jni/unrar $(NDK_APP_PROJECT_PATH)/jni/unzip
   LOCAL_SHARED_LIBRARIES := libunrar libunzip
   LOCAL_LDLIBS := -lz
   LOCAL_MODULE    := gme_spc
   LOCAL_SRC_FILES := gme_ayturbo.c gmenative.c Blip_Buffer.cpp \
   	Ay_Apu.cpp Ay_Cpu.cpp Ay_Emu.cpp \
	Classic_Emu.cpp Data_Reader.cpp Dual_Resampler.cpp Effects_Buffer.cpp \
	Fir_Resampler.cpp gme.cpp \
	Gme_File.cpp Multi_Buffer.cpp Music_Emu.cpp \
	Snes_Spc.cpp Spc_Cpu.cpp Spc_Dsp.cpp Spc_Emu.cpp Spc_Filter.cpp

   include $(BUILD_SHARED_LIBRARY)

