dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}

// Use an integer for version numbers
version = 2

cloudstream {
    description = "HDPornFull - Full length HD adult streaming"
    authors = listOf("aicurv")

    /**
     * Status int as one of the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta-only
     */
    status = 1

    // MUST be NSFW for adult content to show up in the provider list
    tvTypes = listOf("NSFW")

    requiresResources = false

    language = "en"

    iconUrl = "https://www.google.com/s2/favicons?domain=hdpornfull.com&sz=%size%"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}
