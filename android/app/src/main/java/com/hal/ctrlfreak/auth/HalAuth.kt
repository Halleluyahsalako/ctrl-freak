package com.hal.ctrlfreak.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

// Two separate concerns, easy to conflate — see docs/ANDROID_PLAN.md:
// 1. Signing into Firebase needs an ID token, from Credential Manager.
// 2. Calling the Drive API needs an OAuth access token carrying the
//    drive.file scope, from the separate Authorization API. Credential
//    Manager does not hand out scoped access tokens — this genuinely
//    requires two different Google APIs, not a shortcut I'm missing.

// Same value as GOOGLE_OAUTH_CLIENT_ID in the extension's .env — Firebase
// needs the *web* client ID here (not an Android-type client), since it's
// what verifies the ID token server-side.
private const val HAL_WEB_CLIENT_ID =
    "1005185040270-79g6gd3i8ls9pq4drgbasnht3si7v2bs.apps.googleusercontent.com"
private const val HAL_DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file"

suspend fun halSignIn(context: Context) {
    val googleIdOption = GetGoogleIdOption.Builder()
        .setServerClientId(HAL_WEB_CLIENT_ID)
        .setFilterByAuthorizedAccounts(false)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    val result = CredentialManager.create(context).getCredential(context, request)
    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(result.credential.data)

    val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
    halAuth.signInWithCredential(firebaseCredential).await()
}

fun halSignOut() {
    halAuth.signOut()
}

// Requests a Drive-scoped access token. If Google can grant it silently
// (already consented before), the token comes back directly. Otherwise
// AuthorizationResult carries a `pendingIntent` that the caller (an
// Activity/Compose screen, via ActivityResultContracts.StartIntentSenderForResult)
// must launch to show the consent screen — that UI wiring lives with the
// caller, not here, same as the extension only calls Drive auth lazily
// from the popup, not eagerly at sign-in.
suspend fun halRequestDriveAuthorization(context: Context): AuthorizationResult {
    val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(HAL_DRIVE_SCOPE)))
        .build()
    return Identity.getAuthorizationClient(context).authorize(request).await()
}
