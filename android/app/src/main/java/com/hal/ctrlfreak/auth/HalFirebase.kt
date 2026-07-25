package com.hal.ctrlfreak.auth

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

// Firebase auto-initializes from google-services.json (see docs/ANDROID_PLAN.md
// for how to get that file) — no manual FirebaseApp.initializeApp() call needed,
// unlike the extension's hal-firebase.ts which has to build its config by hand
// from .env.

val halAuth get() = Firebase.auth
val halDb get() = Firebase.firestore
