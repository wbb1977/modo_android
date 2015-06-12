   
   LOCAL_PATH := $(call my-dir)

   include $(CLEAR_VARS)
   LOCAL_CFLAGS += -fexceptions  -D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE -DRAR_SMP -DRARDLL
	
   LOCAL_LDLIBS := 
   LOCAL_MODULE    := unrar
   LOCAL_SRC_FILES := rar.cpp strlist.cpp strfn.cpp pathfn.cpp smallfn.cpp global.cpp file.cpp filefn.cpp filcreat.cpp \
	archive.cpp arcread.cpp unicode.cpp system.cpp isnt.cpp crypt.cpp crc.cpp rawread.cpp encname.cpp \
	resource.cpp match.cpp timefn.cpp rdwrfn.cpp consio.cpp options.cpp errhnd.cpp rarvm.cpp secpassword.cpp \
	rijndael.cpp getbits.cpp sha1.cpp sha256.cpp blake2s.cpp hash.cpp extinfo.cpp extract.cpp volume.cpp \
	list.cpp find.cpp unpack.cpp headers.cpp threadpool.cpp rs16.cpp cmddata.cpp filestr.cpp scantree.cpp dll.cpp qopen.cpp

   include $(BUILD_SHARED_LIBRARY)

