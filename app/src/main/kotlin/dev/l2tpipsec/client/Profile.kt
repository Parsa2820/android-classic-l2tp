package dev.l2tpipsec.client

data class ProfileInput(
    val server: String,
    val psk: String,
    val username: String,
    val password: String,
)

sealed class ProfileCheck {
    data object Ok : ProfileCheck()

    data class Invalid(val message: String) : ProfileCheck()
}

object ProfileValidator {
    fun validate(input: ProfileInput): ProfileCheck {
        val server = input.server.trim()
        val psk = input.psk
        val username = input.username.trim()
        val password = input.password
        if (server.isEmpty()) {
            return ProfileCheck.Invalid("Enter the VPN server hostname or IPv4 address.")
        }
        if (server.length > 253 || server.any { it.isWhitespace() }) {
            return ProfileCheck.Invalid("The server must be a hostname or IPv4 address, without spaces.")
        }
        if (psk.isEmpty()) {
            return ProfileCheck.Invalid("Enter the IPsec pre-shared key. This client does not do certificate IKEv1.")
        }
        if (username.isEmpty()) {
            return ProfileCheck.Invalid("Enter the PPP username.")
        }
        if (password.isEmpty()) {
            return ProfileCheck.Invalid("Enter the PPP password. The lab server expects MS-CHAPv2.")
        }
        return ProfileCheck.Ok
    }
}
