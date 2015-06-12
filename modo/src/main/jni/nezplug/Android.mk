   LOCAL_PATH := $(call my-dir)

   include $(CLEAR_VARS)

   LOCAL_LDLIBS := -lz
   LOCAL_C_INCLUDES +=  $(NDK_APP_PROJECT_PATH)/jni/nezplug/cpu \
	$(NDK_APP_PROJECT_PATH)/jni/nezplug/device \
	$(NDK_APP_PROJECT_PATH)/jni/nezplug/format \
	$(NDK_APP_PROJECT_PATH)/jni/nezplug/common\
	$(NDK_APP_PROJECT_PATH)/jni/unzip
   LOCAL_SHARED_LIBRARIES := libunzip	
   LOCAL_MODULE    := nezplug
#device/nes/s_fdse.c nez.c memzip.c
   LOCAL_SRC_FILES :=  nez.c memzip.c format/songinfo.c format/nsf6502.c format/nezplug.c format/m_zxay.c format/m_sgc.c format/m_nsf.c \
	format/m_nsd.c format/m_kss.c format/m_hes.c format/m_gbr.c format/handler.c format/audiosys.c \
	device/s_sng.c device/s_scc.c device/s_psg.c device/s_logtbl.c device/s_hesad.c device/s_hes.c device/s_dmg.c \
	device/nes/s_vrc7.c device/nes/s_vrc6.c device/nes/s_n106.c device/nes/s_mmc5.c device/nes/s_fme7.c  \
	device/nes/s_fds3.c device/nes/s_fds2.c device/nes/s_fds1.c device/nes/s_fds.c device/nes/s_apu.c device/nes/logtable.c \
	device/opl/s_opltbl.c device/opl/s_opl.c device/opl/s_deltat.c \
	cpu/kmz80/kmz80t.c cpu/kmz80/kmz80c.c cpu/kmz80/kmz80.c cpu/kmz80/kmr800.c cpu/kmz80/kmevent.c cpu/kmz80/kmdmg.c \
	common/unix/csounix.c \
	nezplugnative.c

   include $(BUILD_SHARED_LIBRARY)

