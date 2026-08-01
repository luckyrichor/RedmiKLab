package com.redmiklab.app

class ExportFileNameAutoSaver(private val persist: (String) -> Unit) {
    fun onUserEdited(fileName: String) {
        persist(fileName)
    }
}
