#include <string.h>
#include <malloc.h>
#include <zlib.h>
#include <jni.h>
#include <sys/stat.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <ctype.h>
#include <android/log.h>
#include "dll.hpp"
#include "uncompress.h"
#include "LZH/LZH.H"
#include "YmTypes.h"
#include "Ymload.h"
#include "M3u_Playlist.h"

#define CHECK_HEADER_3(v, b0, b1, b2) (v[0]==b0 && v[1]==b1 && v[2]==b2)
#define CHECK_HEADER_4(v, b0, b1, b2, b3) (v[0]==b0 && v[1]==b1 && v[2]==b2 && v[3]==b3)
#define CHECK_HEADER_5(v, b0, b1, b2, b3, b4) (v[0]==b0 && v[1]==b1 && v[2]==b2 && v[3]==b3 && v[4]==b4)

// Converter functions for reading Amiga formats
static unsigned int conv_le32(unsigned char* le) {
	return le[3] | le[2] << 8 | le[1] << 16 | le[0] << 24;
}

static unsigned int conv_le16(unsigned char* le) {
	return le[1] | le[0] << 8;
}

//__android_log_print(ANDROID_LOG_VERBOSE, "cinfo", "offset %ud", gd3_offset);

// returns pointer to title string (can be empty)
static char* parseBytesForTitle(char* data, int size) {
	char* title = (char*)calloc(256, 1);

   	if (title == NULL)
   		return NULL;

	// Check Game Boy
	if (CHECK_HEADER_3(data,'G','B','S')) {
		memcpy(title, data + 0x10, 32);
		return title;
	}

	// Check NSF
	if (CHECK_HEADER_4(data,'N','E','S','M')) {
		memcpy(title, data + 0x0E, 32);
		return title;
	}

	// Check NSFe
	if (CHECK_HEADER_4(data,'N','S','F','E')) {
		unsigned int chunksize;
		unsigned char chunkheader[4];
		int pos = 4;
		while((pos - 8) < size) {
			memcpy(&chunksize, data + pos, 4);
			pos += 4;
			memcpy(&chunkheader, data + pos, 4);
			pos += 4;
			if (memcmp(chunkheader, "auth", 4) == 0) {
				memcpy(title, data + pos, strnlen(data + pos, 32));
				break;
			}
			if (chunksize == 0 || memcmp(chunkheader, "NEND", 4) == 0)
				break;
			pos += chunksize;
		}
		return title;
	}

	// Check SPC
	if (memcmp(data, "SNES-SPC700 Sound File Data", 27) == 0) {
		memcpy(title, data + 0x2E, 32);
		return title;
	}

	// Check SAP + CR/LF (0x0D, 0x0A), ToDO: check that data is long enough
	if (CHECK_HEADER_5(data, 'S','A','P','\x0d','\x0a')) {
		data[size-1] = 0; // so that strstr stops for sure
		char* start = strstr(data, "NAME \"");
		if (start != NULL) {
			char* end = strstr(start + 6, "\"");
			if (start != NULL && end != NULL && (end - start) > 7 && (end - start) < 256)
				memcpy(title, start + 6, end - start - 6);
		}
		return title;
	}

	// Check AY
	if (CHECK_HEADER_5(data, 'Z','X','A','Y','E')) {
		unsigned int offset_firstsong = (data[0x12] << 8 | data[0x13]) + 0x12;
		if (offset_firstsong < size) {
			unsigned int offset_name = data[offset_firstsong] << 8 | data[offset_firstsong + 1] + offset_firstsong;
			if (offset_name < (size - 40))
				memcpy(title, data+offset_name, strnlen(data + offset_name, 40));
		}
		return title;
	}

	// Check Sid
	if (CHECK_HEADER_4(data, 'P','S','I','D') || CHECK_HEADER_4(data, 'R','S','I','D')) {
		memcpy(title, data + 0x16, 32);
		return title;
	}

	// Check Hes
	if (CHECK_HEADER_4(data, 'H','E','S','M')) {
		if (data[0x40] >= ' ') {
			int len = 1;
			for (int i = 0; i < 0x30; ++i,++len)
				if (data[0x40 + i] == 0xFF || data[0x40 + i] < ' ')
					break;
			if (len > 3) {
				memcpy(title, data + 0x40, len);
				return title;
			}
		}
	}

	// Check YM
	if (!memcmp(data + 2, "-lh5-", 5)) {
		CLzhDepacker* depacker = new CLzhDepacker();
		lzhHeader_t* lzh_header = (lzhHeader_t*)data;
		unsigned int size_packed = lzh_header->packed;
		unsigned int size_original = lzh_header->original;
		if (lzh_header->level == 0 && size_packed < size_original) {
			unsigned char* depacked_data = (unsigned char*)malloc(size_original);
			if (depacked_data != NULL) {
				if (depacker->LzUnpack(data + sizeof(lzhHeader_t) + lzh_header->name_lenght + 2, size, depacked_data, size_original)) {
					char header[4];
					memcpy(header, depacked_data,4);
					//__android_log_print(ANDROID_LOG_VERBOSE, "cinfo", "==> %c%c%c%c", depacked_data[0],depacked_data[1],depacked_data[2],depacked_data[3]);
					if ((!memcmp(header, "YM5!", 4) || !memcmp(header, "YM6!", 4)) && !memcmp(depacked_data + 4, "LeOnArD!", 8)) {
						//__android_log_print(ANDROID_LOG_VERBOSE, "cinfo", "=> YM5, YM6");
						int ndrums = conv_le16(depacked_data + 20);
						int offset = conv_le16(depacked_data + 32) + 34;
						for (int i = 0; i < ndrums; ++i)
							offset += conv_le32(depacked_data + offset) + 4;
						memcpy(title, depacked_data + offset, strnlen((char*)depacked_data + offset, 255));
					} else if (!memcmp(header, "MIX1", 4) && !memcmp(depacked_data + 4, "LeOnArD!", 8)) {
						int nblocks = conv_le32(depacked_data + 20);
						int offset = 24;
						for (int i = 0; i < nblocks; ++i)
							offset += 12;
						memcpy(title, depacked_data + offset, strnlen((char*)depacked_data + offset, 255));
					} else if ((!memcmp(header, "YMT1", 4) || !memcmp(header, "YMT2", 4)) && !memcmp(depacked_data + 4, "LeOnArD!", 8)) {
						memcpy(title, depacked_data + 30, strnlen((char*)depacked_data + 30, 255));
					} else {
						memcpy(title, "Unknown\0", 8); // Default for YM2!, YM3!, YM3b, YM4!
					}
				}
			}
			free(depacked_data);
		}
		delete depacker;
		return title;
	}

	free(title);
	return NULL;
}

static char* parseVgmFileForTitle(gzFile gz) {
   	char* title = (char*)calloc(256, 1);

   	if (title == NULL)
   		return NULL;

	char gd3[3];
	unsigned int gd3_offset = 0;
	unsigned int garbage = 0;
	// read GD3 tag offset
	gzseek(gz, 0x14, SEEK_SET);
	gzread(gz, &gd3_offset, 4);
	gd3_offset += 0x14;
	gzseek(gz, gd3_offset, SEEK_SET);
	gzread(gz, &gd3, 3);
	if (CHECK_HEADER_3(gd3, 'G','d','3')) {
		gzread(gz, &garbage, 4);
		gzread(gz, &garbage, 4);
		short trackname[60];
		memset(trackname, 0, 60*2);
		gzread(gz, &trackname, 60*2);
		char clean_trackname[60];
		trackname[59] = 0;
		for (int i = 0, l = 60; i < l; ++i)
			clean_trackname[i] = trackname[i]>>8;
		memcpy(title, clean_trackname, 60); // not a problem, since one null terminated char is in there
		return title;
	}

	free(title);
	return NULL;
}

static char* parseModulesForTitle(gzFile gz) {
   	char* title = (char*)calloc(256, 1);

   	if (title == NULL)
   		return NULL;

   	char data[40];

	// Check .mod - header signatures taken from MikMod loader
   	gzseek(gz, 0x438, SEEK_SET);
   	gzread(gz, data, 4);
	if (!memcmp(data, "M.K.", 4) || !memcmp(data, "M!K!", 4) || !memcmp(data, "FLT", 3) || !memcmp(data, "EXO", 3)
			|| !memcmp(data, "OKTA", 4) || !memcmp(data, "CD81", 4)
			|| (!memcmp(data + 1, "CHN", 3) && isdigit(data[0]))
			|| (!memcmp(data + 2, "CH", 2) && isdigit(data[0]) && isdigit(data[1]))
			|| (!memcmp(data + 2, "CN", 2) && isdigit(data[0]) && isdigit(data[1])))
	{
	   	gzseek(gz, 0x00, SEEK_SET);
		gzread(gz, title, 20);
		return title;
	}

	// Check .xm
   	gzseek(gz, 0x00, SEEK_SET);
   	gzread(gz, data, 40);
   	if (!memcmp(data, "Extended Module: ",17) && data[37] == 0x1A) {
   		memcpy(title, data + 17, 20);
   		return title;
   	}

   	// Check .med
   	gzseek(gz, 0x00, SEEK_SET);
   	gzread(gz, data, 4);
   	if (!memcmp(data, "MMD0", 4) || !memcmp(data, "MMD1", 4)) {
   		gzseek(gz, 0x20, SEEK_SET);
   		unsigned char offset[4];
   		unsigned char slen[4];
   		gzread(gz, &offset, 4);
   		if (conv_le32(offset) > 0) {
   			gzseek(gz, conv_le32(offset) + 0x2C, SEEK_SET);
   			gzread(gz, &offset, 4);
   			gzread(gz, &slen, 4);
   			gzseek(gz, conv_le32(offset), SEEK_SET);
   			if (conv_le32(offset) > 0 && conv_le32(slen) > 0 && conv_le32(slen) < 255) {
   				if (gzread(gz, title, conv_le32(slen)) == conv_le32(slen))
   					return title;
   			}
   		}
   	}

   	// Check .s3m
   	gzseek(gz, 0x2C, SEEK_SET);
   	gzread(gz, data, 4);
   	if (!memcmp(data, "SCRM", 4)) {
   		gzseek(gz, 0x00, SEEK_SET);
   		gzread(gz, title, 27);
   		return title;
   	}

 	// Check .it
   	gzseek(gz, 0x00, SEEK_SET);
   	gzread(gz, data, 4);
   	if (!memcmp(data, "IMPM", 4)) {
   		gzseek(gz, 0x04, SEEK_SET);
   		gzread(gz, title, 25);
   		return title;
   	}

   	// .okt does not seem to support a song name: http://textfiles.com/music/FORMATS/okt-form.txt

   	free(title);
	return NULL;
}

// find first SPC in Rsn file and get a game name from it
static int rarExtractSpcBytes(UINT msg, LPARAM rsnSpcBuffer, LPARAM extractedData, LPARAM bytesProcessed) {
	static int offset = 0;
	if (msg == 0xFFFF)
		offset = 0;
	if (msg == UCM_PROCESSDATA)	{
		memcpy((char*)rsnSpcBuffer + offset, (char*)extractedData, bytesProcessed);
		offset += bytesProcessed;
		return 1;
	}
	return -1;
}

static char* parseRsnForTitle(char* path) {

	char* rsnEntry = NULL;
	char* title = NULL;

	HANDLE rarHandle;
	struct RAROpenArchiveData rarFileData;
	struct RARHeaderData rarFileHeader;

	rarFileData.ArcName = path;
	rarFileData.OpenMode = RAR_OM_EXTRACT;
	rarHandle = RAROpenArchive(&rarFileData);

	if (rarFileData.OpenResult == ERAR_SUCCESS)	{
		int res = RARReadHeader(rarHandle, &rarFileHeader);
		while(res == ERAR_SUCCESS)	{
			if (strcasestr(rarFileHeader.FileName, ".spc") != NULL && rarFileHeader.UnpSize > 66000 && rarFileHeader.UnpSize < 67000) {
				rsnEntry = (char*)malloc(rarFileHeader.UnpSize);
				if (rsnEntry != NULL) {
					rarExtractSpcBytes(0xFFFF, 0, 0, 0);
					RARSetCallback(rarHandle, rarExtractSpcBytes, (long)rsnEntry);
					RARProcessFile(rarHandle, RAR_EXTRACT, NULL, NULL);
				}
				break;
			} else {
				RARProcessFile(rarHandle, RAR_SKIP, NULL, NULL);
			}
			res = RARReadHeader(rarHandle, &rarFileHeader);
		}
	}
	RARCloseArchive(rarHandle);

	if (rsnEntry != NULL && !memcmp(rsnEntry, "SNES-SPC700 Sound File Data", 27)) {
		title = (char*)calloc(256, 1);
		if (title != NULL)
			memcpy(title, rsnEntry + 0x4E, 32);
	}

	free(rsnEntry);

	return title;
}
//
char* queryM3u(M3u_Playlist* m3) {

	char* title = NULL;

	if (m3->info().title != NULL && strlen(m3->info().title) > 3) {
		title = (char*)calloc(256, 1);
		if (title != NULL)
			memcpy(title, m3->info().title, strnlen(m3->info().title, 255));
	} else if (m3->size() > 0 && (*m3)[0].name != NULL && strlen((*m3)[0].name) > 3) {
		title = (char*)calloc(256, 1);
		if (title != NULL)
			memcpy(title, (*m3)[0].name, strnlen((*m3)[0].name, 255));
	}

	return title;
}

// path gets modified!
char* parseM3uForTitle(char* path) {
	// replace file extensions with .m3u
	char* ext = strrchr(path, '.');
	if (ext == NULL)
		return NULL;
	// is there enough space, to replace old extension with .m3u
	if (((path + strlen(path)) - ext) < 4)
		return NULL;

	memcpy(ext, ".m3u", 4);

	char* title = NULL;

	M3u_Playlist* m3 = new M3u_Playlist();
	blargg_err_t err = m3->load((const char*)path);
	if (err == NULL)
		title = queryM3u(m3);
	delete m3;

	return title;
}

// assume that title array is at least 256 bytes
extern "C" int Java_de_illogical_modo_FileBrowser_loadTitle(JNIEnv* env, jclass clazz, jstring path, jbyteArray title) {

	char cpath[1024];
	memset(cpath, 0, 1024);

	int clen = env->GetStringLength(path);
	if (clen > 1023)
		return -1;
	env->GetStringUTFRegion(path, 0, clen, cpath);

	gzFile gz = gzopen(cpath, "rb");
	if (gz == NULL)
		return -1;

	int buffersize = 800;

	char header[8];
	int in = gzread(gz, header, 8);
	if (in != 8) {
		gzclose(gz);
		return -1;
	}

	// Check for some formats which needs to read the entire file in
	// SAP files can be smaller than 800
	if (CHECK_HEADER_4(header, 'N', 'S', 'F', 'E') || !memcmp(header + 2, "-lh5-", 5) || !memcmp(header, "SAP", 3)) {
//		__android_log_print(ANDROID_LOG_VERBOSE, "cinfo", "=> %s", cpath);
		struct stat nsfe_stat;
	    if (stat(cpath, &nsfe_stat) == -1)
	    	return -1;
	    // Safe range for NSFE, SAP, YM detection routine
		if (nsfe_stat.st_size < 50 || nsfe_stat.st_size > 500000)
			return -1;
		buffersize = nsfe_stat.st_size;
	}

	gzseek(gz, 0, SEEK_SET);

	// Ok, we have the needed size of the file buffer
	char *data = (char*)malloc(buffersize);
	if (data == NULL) {
		gzclose(gz);
		return -1;
	}

	in = gzread(gz, data, buffersize);
	int title_len = 0;
	if (in == buffersize) {
		char* str = NULL;
		if (CHECK_HEADER_3(data, 'V','g','m')) {
			// VGM files are quite big or gzip compressed, so we handle that case extra
			str = parseVgmFileForTitle(gz);
		} else if (CHECK_HEADER_4(data, 'R','a','r','!')) {
			str = parseRsnForTitle(cpath);
		} else if (CHECK_HEADER_4(data, 'H','E','S','M')) {
			str = parseBytesForTitle(data, buffersize); // HES can contain at offset 0x40 some strings
			if (str == NULL)
				str = parseM3uForTitle(cpath);
		} else if (CHECK_HEADER_4(data, 'K','S','C','C')) {
			str = parseM3uForTitle(cpath);
		} else {
			// handle the easy formats where the title is stored at the beginning
			str = parseBytesForTitle(data, buffersize);
			// for mods we need to seek as the info can be all over the file
			if (str == NULL)
				str = parseModulesForTitle(gz);
		}

		if (str != NULL) {
			if (!memcmp(str, "       ", 5))
				memcpy(str, "??\0", 3);
			title_len = strnlen(str, 256);
			env->SetByteArrayRegion(title, 0, title_len, (jbyte*)str);
		}
		free(str);
	}

	gzclose(gz);
	free(data);

	return title_len;
}
