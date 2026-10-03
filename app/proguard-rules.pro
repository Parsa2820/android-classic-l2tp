# JNI looks these up by name from JNI_OnLoad. R8 must keep the static methods.
-keep class dev.l2tpipsec.client.VpnBridge { *; }
-keep class dev.l2tpipsec.client.TunnelVpnService { *; }
-keep class dev.l2tpipsec.client.VpnTunnelEvents { *; }
