package com.pararam2006.cmv

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform