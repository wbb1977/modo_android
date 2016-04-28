   LOCAL_PATH := $(call my-dir)

   include $(CLEAR_VARS)

   LOCAL_C_INCLUDES += $(NDK_APP_PROJECT_PATH)/jni/unzip   
   LOCAL_SHARED_LIBRARIES := libunzip
   LOCAL_MODULE    := asap
   LOCAL_SRC_FILES := asap.c asapnative.c

   include $(BUILD_SHARED_LIBRARY)

