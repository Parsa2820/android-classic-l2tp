/*
 * Host hook for the Docker lab. Runs the same IKEv1 + L2TP + PPP path as the Android
 * client, with VpnService.protect() disabled because this process is not inside a TUN.
 *
 * Usage: l2tp-lab <server> <username> <password> <psk> [mtu]
 * Exit status is the engine code from engine.h (0 = IKE, L2TP, and PPP all finished).
 */
#include "engine.h"

#include <stdio.h>
#include <stdlib.h>

static void usage(const char *argv0) {
  fprintf(stderr, "usage: %s <server> <username> <password> <psk> [mtu]\n", argv0);
}

int main(int argc, char **argv) {
  if (argc < 5 || argc > 6) {
    usage(argv[0]);
    return TUNNEL_EXIT_BAD_ARGS;
  }
  int mtu = 1400;
  if (argc == 6) {
    mtu = atoi(argv[5]);
    if (mtu <= 0) {
      fprintf(stderr, "mtu must be a positive integer\n");
      return TUNNEL_EXIT_BAD_ARGS;
    }
  }

  engine_set_socket_protection_enabled(0);
  int rc = tunnel_negotiate(argv[1], argv[2], argv[3], argv[4], mtu);
  if (rc == TUNNEL_EXIT_OK) {
    uint8_t ip[4];
    uint8_t dns1[4];
    uint8_t dns2[4];
    tunnel_negotiated_client_ipv4(ip);
    tunnel_negotiated_dns_ipv4(dns1, dns2);
    printf("result=0 client_ip=%u.%u.%u.%u dns=%u.%u.%u.%u,%u.%u.%u.%u\n", ip[0], ip[1], ip[2], ip[3], dns1[0],
           dns1[1], dns1[2], dns1[3], dns2[0], dns2[1], dns2[2], dns2[3]);
    fflush(stdout);
  } else {
    fprintf(stderr, "result=%d (1=ike 2=l2tp 3=ppp 12=stopped)\n", rc);
  }
  tunnel_disconnect();
  return rc;
}
