package tech.relaycorp.relaynet.crypto

import tech.relaycorp.relaynet.RelaynetException

class SignedDataException(message: String, cause: Throwable? = null) :
    RelaynetException(message, cause)
