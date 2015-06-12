   LOCAL_PATH := $(call my-dir)

   include $(CLEAR_VARS)
   
   LOCAL_C_INCLUDES += $(NDK_APP_PROJECT_PATH)/jni/unzip   
   LOCAL_SHARED_LIBRARIES := libunzip
   LOCAL_MODULE    := sidplay2
   LOCAL_SRC_FILES := sid.cpp voice.cpp wave.cpp envelope.cpp filter.cpp extfilt.cpp pot.cpp version.cpp \
	wave6581_PST.cpp wave6581_PS_.cpp wave6581_P_T.cpp \
	wave6581__ST.cpp wave8580_PST.cpp wave8580_PS_.cpp wave8580_P_T.cpp \
	wave8580__ST.cpp \
	config.cpp event.cpp player.cpp psiddrv.cpp mixer.cpp reloc65.c sidplay2.cpp \
	mos656x.cpp mos6510.cpp mos6526.cpp sid6526.cpp \
	IconInfo.cpp InfoFile.cpp MUS.cpp p00.cpp \
	PP20.cpp prg.cpp PSID.cpp SidTune.cpp \
	SidTuneTools.cpp xsid.cpp \
	SidTuneMod.cpp resid-builder.cpp resid.cpp MD5.cpp \
	sid_native.cpp

   include $(BUILD_SHARED_LIBRARY)

