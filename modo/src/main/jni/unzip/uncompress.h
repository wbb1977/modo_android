#ifndef RESOURCES_H
#define RESOURCES_H
#ifdef __cplusplus
	extern "C" {
#endif
extern char* getUncompressedData(char* zipfile, char *entry, int *len);
#ifdef __cplusplus
	}
#endif
#endif
