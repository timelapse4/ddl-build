// use an integer for version numbers
version = 1

cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

    description = "Free live sports streams (soccer, basketball, hockey, combat, baseball, football, racing, tennis, cricket) via the public StreamFree API."
    authors = listOf("timelapse4")

    /**
     * Status int as one of:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     */
    status = 1

    tvTypes = listOf("Live")

    iconUrl = "https://www.google.com/s2/favicons?domain=streamfree.top&sz=%size%"
}
