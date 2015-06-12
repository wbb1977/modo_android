   LOCAL_PATH := $(call my-dir)

   include $(CLEAR_VARS)
   LOCAL_CFLAGS += -DANDROID -O2  -DSTDC_HEADERS=1 -DHAVE_SYS_TYPES_H=1 -DHAVE_SYS_STAT_H=1 -DHAVE_STDLIB_H=1 -DHAVE_STRING_H=1 -DHAVE_MEMORY_H=1 -DHAVE_STRINGS_H=1 -DHAVE_INTTYPES_H=1 -DHAVE_STDINT_H=1 -DHAVE_UNISTD_H=1 -DHAVE_ALLOCA_H=1 -DHAVE_LIBM=1 -DHAVE_POPEN=1 -DHAVE_MKSTEMP=1 -DHAVE_FNMATCH=1 -D_REENTRANT
   LOCAL_LDLIBS := 
   LOCAL_C_INCLUDES += $(NDK_APP_PROJECT_PATH)/jni/unzip $(NDK_APP_PROJECT_PATH)/jni/xmp/include   
   LOCAL_SHARED_LIBRARIES := libunzip
   LOCAL_MODULE    := xmp
   LOCAL_SRC_FILES := xmp_native.c adlib.c control.c dataio.c effects.c envelope.c extras.c filter.c fmopl.c fnmatch.c format.c \
   hio.c hmn_extras.c lfo.c load_helpers.c load.c md5.c med_extras.c mix_all.c mixer.c mkstemp.c period.c player.c \
   read_event.c scan.c smix.c synth_null.c virtual.c \
   depackers/arcfs.c depackers/bunzip2.c depackers/crc32.c depackers/gunzip.c depackers/inflate.c depackers/mmcmp.c \
   depackers/muse.c depackers/oxm.c depackers/ppdepack.c depackers/readlzw.c depackers/readrle.c depackers/s404_dec.c \
   depackers/unarc.c depackers/uncompress.c depackers/unlha.c depackers/unlzx.c depackers/unsqsh.c depackers/unxz.c \
   depackers/unzip.c depackers/vorbis.c depackers/xfd.c depackers/xz_dec_lzma2.c depackers/xz_dec_stream.c \
   loaders/669_load.c loaders/amd_load.c loaders/amf_load.c loaders/arch_load.c loaders/asif.c loaders/asylum_load.c \
   loaders/chip_load.c loaders/common.c loaders/dbm_load.c loaders/digi_load.c loaders/dt_load.c loaders/emod_load.c \
   loaders/far_load.c loaders/flt_load.c loaders/fnk_load.c loaders/gal4_load.c loaders/gal5_load.c loaders/gdm_load.c \
   loaders/hmn_load.c loaders/hsc_load.c loaders/ice_load.c loaders/iff.c loaders/imf_load.c loaders/ims_load.c loaders/it_load.c \
   loaders/itsex.c loaders/liq_load.c loaders/masi_load.c loaders/mdl_load.c loaders/med2_load.c loaders/med3_load.c \
   loaders/med4_load.c loaders/mfp_load.c loaders/mgt_load.c loaders/mmd_common.c loaders/mmd1_load.c loaders/mmd3_load.c \
   loaders/mod_load.c loaders/mtm_load.c loaders/no_load.c loaders/okt_load.c loaders/psm_load.c loaders/pt3_load.c \
   loaders/ptm_load.c loaders/pw_load.c loaders/rad_load.c loaders/rtm_load.c loaders/s3m_load.c loaders/sample.c \
   loaders/sfx_load.c loaders/st_load.c loaders/stim_load.c loaders/stm_load.c loaders/stx_load.c loaders/sym_load.c loaders/ult_load.c \
   loaders/umx_load.c loaders/voltable.c loaders/xm_load.c \
   loaders/prowizard/ac1d.c loaders/prowizard/di.c loaders/prowizard/eureka.c loaders/prowizard/fc-m.c \
   loaders/prowizard/fuchs.c loaders/prowizard/fuzzac.c loaders/prowizard/gmc.c loaders/prowizard/heatseek.c \
   loaders/prowizard/hrt.c loaders/prowizard/ksm.c loaders/prowizard/mp.c loaders/prowizard/noiserun.c \
   loaders/prowizard/novotrade.c loaders/prowizard/np1.c loaders/prowizard/np2.c loaders/prowizard/np3.c \
   loaders/prowizard/p40.c loaders/prowizard/p61a.c loaders/prowizard/pha.c \
   loaders/prowizard/pm10c.c loaders/prowizard/pm18a.c \
   loaders/prowizard/pp21.c  \
   loaders/prowizard/prowiz.c loaders/prowizard/prun1.c loaders/prowizard/prun2.c loaders/prowizard/ptktable.c loaders/prowizard/skyt.c \
   loaders/prowizard/starpack.c loaders/prowizard/tdd.c loaders/prowizard/theplayer.c loaders/prowizard/titanics.c \
   loaders/prowizard/tp3.c loaders/prowizard/tuning.c \
   loaders/prowizard/unic.c loaders/prowizard/unic2.c loaders/prowizard/wn.c loaders/prowizard/xann.c loaders/prowizard/zen.c
   
#loaders/prowizard/pm.c loaders/prowizard/pm01.c loaders/prowizard/pm20.c loaders/prowizard/pm40.c loaders/prowizard/pp10.c
#loaders/prowizard/pp30.c loaders/prowizard/tp1.c loaders/prowizard/tp2.c 

   include $(BUILD_SHARED_LIBRARY)

