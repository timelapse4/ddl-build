// use an integer for version numbers
version = 1

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "DaddyLiveHD – 1000+ live sports & TV channels from dlhd.pk"
    authors = listOf("YourName")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Make sure to verify that the source is working before adding it to the list.
    tvTypes = listOf("Live")

    // optional
    iconUrl = "https://dlhd.pk/assets/logos/logo.png"

    language = "en"
}
