version = 4

cloudstream {
    description = "DaddyLiveHD – 1000+ live sports & TV channels from dlhd.pk"
    authors = listOf("timelapse4")
    status = 1
    tvTypes = listOf("Live")
    // ลอง path หลายแบบ — ใส่ path ที่มีโอกาสถูกที่สุดก่อน
    iconUrl = "https://dlhd.st/assets/logos/logo.png"
    language = "en"
}

android {
    namespace = "com.DaddyLiveHD"
}

dependencies {
    // Needed for async/awaitAll used in loadLinks to check stream folders in parallel
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}
