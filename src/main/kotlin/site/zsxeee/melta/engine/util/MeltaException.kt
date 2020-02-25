/*
 * Copyright (c) 2018 zsxeee All Rights Reserved.
 */

package site.zsxeee.melta.engine.util

class MeltaEngineRuntimeException(message: String, cause: Throwable? = null): MeltaException(message, cause)
class MeltaParseException(message: String, cause: Throwable? = null): MeltaException(message, cause)
class MeltaInvalidResultException(message: String, cause: Throwable? = null): MeltaException(message, cause)

open class MeltaException(message: String, cause: Throwable? = null):Exception(message, cause)