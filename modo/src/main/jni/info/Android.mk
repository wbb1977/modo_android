   
   LOCAL_PATH := $(call my-dir)
   include $(CLEAR_VARS)

   #RSN support
   LOCAL_CFLAGS += -D_UNIX -DRARDLL -DBLARGG_LITTLE_ENDIAN -DHAVE_STDINT_H -fno-rtti -fno-exceptions
   LOCAL_C_INCLUDES +=  $(NDK_APP_PROJECT_PATH)/jni/unrar $(NDK_APP_PROJECT_PATH)/jni/unzip $(NDK_APP_PROJECT_PATH)/jni/ym $(NDK_APP_PROJECT_PATH)/jni/gme
   LOCAL_SHARED_LIBRARIES := libunrar libunzip libym libgme_kode54
   LOCAL_LDLIBS := -lz
   LOCAL_MODULE    := info
   LOCAL_SRC_FILES := info.cpp 

   include $(BUILD_SHARED_LIBRARY)

