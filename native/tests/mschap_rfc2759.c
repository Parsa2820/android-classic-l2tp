/*
 * RFC 2759 section 9.2 MS-CHAPv2 test vector, plus the Failure-message parser.
 * This is the PPP authentication hook the lab server (xl2tpd) expects.
 */
#include "ppp_mschap.h"

#include <stdio.h>
#include <string.h>

static int expect_mem(const char *label, const uint8_t *got, const uint8_t *want, size_t n) {
  if (memcmp(got, want, n) == 0)
    return 0;
  fprintf(stderr, "FAIL %s\n got ", label);
  for (size_t i = 0; i < n; i++)
    fprintf(stderr, "%02x", got[i]);
  fprintf(stderr, "\nwant ");
  for (size_t i = 0; i < n; i++)
    fprintf(stderr, "%02x", want[i]);
  fprintf(stderr, "\n");
  return 1;
}

static int test_rfc2759(void) {
  const uint8_t auth[16] = {0x5B, 0x5D, 0x7C, 0x7D, 0x7B, 0x3F, 0x2F, 0x3E,
                            0x3C, 0x2C, 0x60, 0x21, 0x32, 0x26, 0x26, 0x28};
  uint8_t peer[16] = {0x21, 0x40, 0x23, 0x24, 0x25, 0x5E, 0x26, 0x2A,
                      0x28, 0x29, 0x5F, 0x2B, 0x3A, 0x33, 0x7C, 0x7E};
  const uint8_t nt[24] = {0x82, 0x30, 0x9E, 0xCD, 0x8D, 0x70, 0x8B, 0x5E, 0xA0, 0x8F, 0xAA, 0x39,
                          0x81, 0xCD, 0x83, 0x54, 0x42, 0x33, 0x11, 0x4A, 0x3D, 0x85, 0xD6, 0xDF};
  uint8_t value[49];
  if (ppp_mschapv2_response_value("User", "clientPass", auth, peer, value) != 0) {
    fprintf(stderr, "FAIL response_value returned error\n");
    return 1;
  }
  int failed = 0;
  failed |= expect_mem("peer challenge", value, peer, 16);
  failed |= expect_mem("reserved", value + 16, (const uint8_t *)"\0\0\0\0\0\0\0\0", 8);
  failed |= expect_mem("NT-Response", value + 24, nt, 24);
  if (value[48] != 0) {
    fprintf(stderr, "FAIL flags byte %u\n", value[48]);
    failed = 1;
  }
  return failed;
}

static int test_failure_message(void) {
  const char *msg = "E=691 R=1 C=00000000000000000000000000000000 V=3 M=Authentication failed";
  ppp_mschapv2_failure_info_t info;
  if (ppp_mschapv2_parse_failure((const uint8_t *)msg, strlen(msg), &info) != 0) {
    fprintf(stderr, "FAIL parse_failure\n");
    return 1;
  }
  if (!info.has_error_code || info.error_code != 691 || !info.has_retry || info.retry != 1 || !info.has_version ||
      info.version != 3 || !info.has_message || strcmp(info.message, "Authentication failed") != 0) {
    fprintf(stderr, "FAIL failure fields e=%d r=%d v=%d m=%s\n", info.error_code, info.retry, info.version,
            info.message);
    return 1;
  }
  return 0;
}

int main(void) {
  int failed = test_rfc2759() | test_failure_message();
  if (failed == 0)
    printf("mschap rfc2759 ok\n");
  return failed;
}
