#pragma once

#include <stdint.h>

int bypass_socket(int fd);

/* Status callback bridge used by Go/C */
void awgNotifyStatus(int32_t handle, int32_t code);