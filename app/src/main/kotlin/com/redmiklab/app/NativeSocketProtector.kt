package com.redmiklab.app

fun interface NativeSocketProtector {
    fun protectSocket(fileDescriptor: Int): Boolean
}
