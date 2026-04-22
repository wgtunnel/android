#pragma once

#include <stdint.h>

int GoGenerateUniqueHandle(int32_t *handle);
void GoReleaseHandle(int32_t handle);

int bypass_socket(int fd);

/* Status callback bridge used by Go/C */
void awgNotifyStatus(int32_t handle, const char* interfaceName, int32_t code);