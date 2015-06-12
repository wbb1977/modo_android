   
   LOCAL_PATH := $(call my-dir)

   include $(CLEAR_VARS)
   LOCAL_CFLAGS += 

   LOCAL_LDLIBS := -lz
   LOCAL_MODULE    := unzip
   LOCAL_SRC_FILES := unzip.c ioapi.c uncompress.c

   include $(BUILD_SHARED_LIBRARY)

