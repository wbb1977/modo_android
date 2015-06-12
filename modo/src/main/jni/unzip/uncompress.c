#include <malloc.h>
#include "unzip.h"

// returns buffer to uncompressed content of file 'name' in zip archive
// caller has to free buffer
char* getUncompressedData(char* zipfile, char *entry, int *len)
{
	*len = 0;
	char* uncompressedData = NULL;
	unzFile zip = unzOpen(zipfile);
	if (zip != NULL)
	{
		if (unzLocateFile(zip, entry, 2) == UNZ_OK)
		{
			unz_file_info fileInfo;
			unzGetCurrentFileInfo(zip, &fileInfo, NULL, 0, NULL, 0, NULL, 0);
			uncompressedData = (char*)malloc(fileInfo.uncompressed_size);
			if (unzOpenCurrentFile(zip) == UNZ_OK && uncompressedData != NULL)
			{
				if (unzReadCurrentFile(zip, uncompressedData, fileInfo.uncompressed_size) >= 0)
				{
					*len = fileInfo.uncompressed_size;
//					printf("File %s: %d vs %d bytes\n", name, fileInfo.compressed_size, fileInfo.uncompressed_size);
				}
				unzCloseCurrentFile(zip);
			}
		}
		unzClose(zip);
	}
	return uncompressedData;
}
