package tech.relaycorp.relaynet.wrappers.cms

import tech.relaycorp.relaynet.RelaynetException

internal class EnvelopedDataException(message: String, cause: Throwable) :
    RelaynetException(message, cause)
