package dev.emortal.tnt.source

import java.io.InputStream

interface TNTSource {

    fun load(): InputStream

    fun save(bytes: ByteArray)

}