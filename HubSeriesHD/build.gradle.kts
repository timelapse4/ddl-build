// use an integer for version numbers
version = 3


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "ซีรี่ย์เกาหลี ฝรั่ง จีน ซับไทย/พากย์ไทย จาก hubserieshds.com"
    authors = listOf("timelapse4")

    /**
    * Status int as one of:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1

    tvTypes = listOf("AsianDrama")

    iconUrl = "https://hubserieshds.com/images/icon-192.png"
}

dependencies {
    // Needed explicitly because this extension uses withContext/Dispatchers/
    // suspendCancellableCoroutine directly (core lib doesn't pull this in on its own)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}
