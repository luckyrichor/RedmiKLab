package com.redmiklab.reports

enum class ReportFormat(
    val suffix: String,
    val mimeType: String,
    val importable: Boolean,
) {
    ZIP(".zip", "application/zip", true),
    JSON(".json", "application/json", true),
    HTML(".html", "text/html", false),
    PDF(".pdf", "application/pdf", false),
    ;

    companion object {
        fun from(value: String?): ReportFormat =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ZIP
    }
}
