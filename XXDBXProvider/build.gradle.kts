dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}

// Use an integer for version numbers
version = 1

cloudstream {
    description = "XXDBX - Adult video database with MP4 streaming"
    authors = listOf("aicurv")

    /**
     * Status int as one of the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta-only
     */
    status = 1

    // MUST be NSFW for adult content to show up in the 18+ provider list
    tvTypes = listOf("NSFW")

    requiresResources = false

    language = "en"

    iconUrl = "https://www.google.com/s2/favicons?domain=xxdbx.com&sz=%size%"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}
