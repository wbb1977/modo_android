   LOCAL_PATH := $(call my-dir)

   include $(CLEAR_VARS)
   LOCAL_LDLIBS :=
   LOCAL_C_INCLUDES += $(NDK_APP_PROJECT_PATH)/jni/unzip   
   LOCAL_SHARED_LIBRARIES := libunzip
   LOCAL_MODULE    := mikmod
   LOCAL_SRC_FILES := mikmod_native.c mmio.c mmalloc.c mmerror.c \
	mdreg.c mdriver.c mdulaw.c mloader.c mlreg.c mlutil.c mplayer.c munitrk.c mwav.c \
	npertab.c sloader.c	virtch.c virtch2.c virtch_common.c \
	load_669.c load_it.c load_m15.c load_med.c load_mod.c load_okt.c load_mtm.c  load_s3m.c load_stm.c	load_xm.c  \
	drv_nos.c drv_android.c

   include $(BUILD_SHARED_LIBRARY)

