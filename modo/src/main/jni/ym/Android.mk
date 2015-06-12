   LOCAL_PATH := $(call my-dir)

   include $(CLEAR_VARS)

   LOCAL_C_INCLUDES += $(NDK_APP_PROJECT_PATH)/jni/unzip   
   LOCAL_SHARED_LIBRARIES := libunzip
   LOCAL_MODULE    := ym
   LOCAL_SRC_FILES := ymnative.c LzhLib.cpp digidrum.cpp Ym2149Ex.cpp Ymload.cpp YmMusic.cpp YmUserInterface.cpp

   include $(BUILD_SHARED_LIBRARY)

