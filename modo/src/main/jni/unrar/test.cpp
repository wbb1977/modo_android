
#define _UNIX
#define RARDLL

#define RSN_ENTRIES 255

#include <stdio.h>
#include <string.h>
#include <malloc.h>
#include "dll.hpp"

static HANDLE rarHandle;
static RAROpenArchiveData rarFileData;
static RARHeaderData rarFileHeader;

static char* rsnEntries[RSN_ENTRIES];
static int rsnSize[RSN_ENTRIES];
static int rsnCount;
static char *rsnInfo;

static void resetRsnEntries() {
	for (int i = 0; i < RSN_ENTRIES; i++) {
		if (rsnEntries[i] != NULL) {
			printf("Free Entry %d - %d Bytes\n", i, rsnSize[i]);
			free(rsnEntries [i]);
		}
		rsnEntries[i] = NULL;
		rsnSize[i] = 0;
	}

	rsnCount = 0;

	if (rsnInfo != NULL)
		free(rsnInfo);
	rsnInfo = NULL;
}

static char* strfind(char *str, char *tgt)
{
	int tlen = strlen(tgt);
	int max = strlen(str) - tlen;
	register int i;

	for (i = 0; i <= max; i++) {
	    if (strncasecmp(&str[i], tgt, tlen) == 0)
			return &str[i+tlen];
	}
	return NULL;
}

int rarExtractSpcBytes(UINT msg, LPARAM rsnSpcBuffer, LPARAM extractedData, LPARAM bytesProcessed) {
	if (msg == UCM_PROCESSDATA) {
		printf("--> SPC Callback: %d bytes processed, %p\n", bytesProcessed, rsnSpcBuffer);
		if (bytesProcessed > 66000) {
			memcpy((char*)rsnSpcBuffer, (char*)extractedData, bytesProcessed);
		}
		return 1;
	}
	return -1;
}

int rarExtractInfoBytes(UINT msg, LPARAM rsnInfoBuffer, LPARAM extractedData, LPARAM bytesProcessed) {
	if (msg == UCM_PROCESSDATA) {
		printf("--> Info Callback: %d bytes processed, %p\n", bytesProcessed, rsnInfoBuffer);
		memcpy((char*)rsnInfoBuffer, (char*)extractedData, bytesProcessed);
		return 1;
	}
	return -1;
}

int main(int argc, char* argv[]) {

	for (int i = 0; i < RSN_ENTRIES; i++) {
		rsnEntries[i] = NULL;
		rsnSize[i] = 0;
	}
	rsnInfo = NULL;

	resetRsnEntries();
	printf("\nUnrar Into Memory Learn 1.0\n\n");

	rarFileData.ArcName = "test.rar";
	rarFileData.OpenMode = RAR_OM_EXTRACT;
	rarHandle = RAROpenArchive(&rarFileData);

	printf("Opening test.rar: %d\n", rarFileData.OpenResult);

	if (rarFileData.OpenResult == ERAR_SUCCESS) {
		printf("-->Success\n");

		int res = RARReadHeader(rarHandle, &rarFileHeader);
		while(res == ERAR_SUCCESS) {
			printf("Header '%s' -> %d -> %d Bytes\n", rarFileHeader.FileName, res, rarFileHeader.UnpSize);
			if (strfind(rarFileHeader.FileName, ".spc") != NULL && rsnCount < RSN_ENTRIES && rarFileHeader.UnpSize > 66000 && rarFileHeader.UnpSize < 67000) {
				printf("--> SPC File, going to extract\n");
				rsnEntries[rsnCount] = (char*)malloc(rarFileHeader.UnpSize);
				if (rsnEntries[rsnCount] != NULL) {
					printf("--> Pointer to memory: %p\n", rsnEntries[rsnCount]);
					RARSetCallback(rarHandle, rarExtractSpcBytes, (long)rsnEntries[rsnCount]);
					RARProcessFile(rarHandle, RAR_EXTRACT, NULL, NULL);
					rsnSize[rsnCount] = rarFileHeader.UnpSize;
					rsnCount++;
				} 
			} else if (strfind(rarFileHeader.FileName, ".txt") != NULL && rsnInfo == NULL && rarFileHeader.UnpSize < 10000) {
				printf("--> TXT File, going to extract\n");
				rsnInfo = (char*)calloc(rarFileHeader.UnpSize + 1, 1);
				if (rsnInfo != NULL) {
					RARSetCallback(rarHandle, rarExtractInfoBytes, (long)rsnInfo);
					RARProcessFile(rarHandle, RAR_EXTRACT, NULL, NULL);
				}
			} else {
				printf("--> Skip\n");
				RARProcessFile(rarHandle, RAR_SKIP, NULL, NULL);
			}
			res = RARReadHeader(rarHandle, &rarFileHeader);
		}
		printf("Result: %d\n", res);
	}
	RARCloseArchive(rarHandle);
	
	printf("Tracks: %d\n", rsnCount);
	printf("Info: %d\n\n%s\n", rsnInfo == NULL ? 0 : strlen(rsnInfo), rsnInfo == NULL ? "<?>" : rsnInfo);

	return 0;
}
