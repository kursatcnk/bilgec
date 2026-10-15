package com.bilgec.app.settings

data class SocialApp(
    val packageName: String,
    val displayName: String,
)

val supportedSocialApps = listOf(
    SocialApp("com.instagram.android", "Instagram"),
    SocialApp("com.zhiliaoapp.musically", "TikTok"),
    SocialApp("com.twitter.android", "X"),
    SocialApp("com.google.android.youtube", "YouTube"),
    SocialApp("com.facebook.katana", "Facebook"),
    SocialApp("com.reddit.frontpage", "Reddit"),
    SocialApp("com.snapchat.android", "Snapchat"),
)
