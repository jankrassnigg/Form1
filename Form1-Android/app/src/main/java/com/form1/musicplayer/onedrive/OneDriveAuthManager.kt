package com.form1.musicplayer.onedrive

import android.app.Activity
import android.content.Context
import android.util.Log
import com.microsoft.identity.client.*
import com.microsoft.identity.client.exception.MsalException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages OneDrive authentication using Microsoft Authentication Library (MSAL)
 */
class OneDriveAuthManager(private val context: Context) {

    private var msalApp: ISingleAccountPublicClientApplication? = null
    private val scopes = arrayOf(
        "Files.Read"
    )

    companion object {
        private const val TAG = "OneDriveAuth"
    }

    /**
     * Initialize MSAL application
     */
    suspend fun initialize(): Boolean = suspendCancellableCoroutine { continuation ->
        PublicClientApplication.createSingleAccountPublicClientApplication(
            context,
            com.form1.musicplayer.R.raw.msal_config,
            object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                override fun onCreated(application: ISingleAccountPublicClientApplication?) {
                    msalApp = application
                    Log.d(TAG, "MSAL initialized successfully")
                    continuation.resume(true)
                }

                override fun onError(exception: MsalException?) {
                    Log.e(TAG, "MSAL initialization failed", exception)
                    continuation.resume(false)
                }
            }
        )
    }

    /**
     * Check if user is already signed in
     */
    suspend fun isSignedIn(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            msalApp?.getCurrentAccount()?.currentAccount != null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking sign in status", e)
            false
        }
    }

    /**
     * Get current signed-in account
     */
    suspend fun getCurrentAccount(): IAccount? = withContext(Dispatchers.IO) {
        return@withContext try {
            msalApp?.getCurrentAccount()?.currentAccount
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current account", e)
            null
        }
    }

    /**
     * Sign in interactively (opens browser)
     */
    suspend fun signIn(activity: Activity): AuthResult {
        val app = msalApp
        if (app == null) {
            return AuthResult(success = false, error = "MSAL not initialized")
        }

        // Check if there's an existing account on a background thread
        val existingAccount = withContext(Dispatchers.IO) {
            try {
                app.getCurrentAccount()?.currentAccount
            } catch (e: Exception) {
                Log.e(TAG, "Error checking existing account", e)
                null
            }
        }

        // Sign out existing account if present
        if (existingAccount != null) {
            Log.d(TAG, "Existing account found, signing out first")
            val signedOut = signOut()
            if (!signedOut) {
                Log.w(TAG, "Failed to sign out existing account, proceeding anyway")
            }
        }

        // Perform interactive sign-in
        return suspendCancellableCoroutine { continuation ->
            performSignIn(activity, app, continuation)
        }
    }

    private fun performSignIn(
        activity: Activity,
        app: ISingleAccountPublicClientApplication,
        continuation: CancellableContinuation<AuthResult>
    ) {
        val parameters = AcquireTokenParameters.Builder()
            .startAuthorizationFromActivity(activity)
            .withScopes(scopes.toList())
            .withCallback(object : AuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult?) {
                    Log.d(TAG, "Sign in successful")
                    val result = AuthResult(
                        success = true,
                        accessToken = authenticationResult?.accessToken,
                        account = authenticationResult?.account
                    )
                    continuation.resume(result)
                }

                override fun onError(exception: MsalException?) {
                    Log.e(TAG, "Sign in failed", exception)
                    val result = AuthResult(
                        success = false,
                        error = exception?.message ?: "Unknown error"
                    )
                    continuation.resume(result)
                }

                override fun onCancel() {
                    Log.d(TAG, "Sign in cancelled")
                    val result = AuthResult(
                        success = false,
                        error = "User cancelled"
                    )
                    continuation.resume(result)
                }
            })
            .build()

        app.acquireToken(parameters)
    }

    /**
     * Get access token (silently if possible)
     */
    suspend fun getAccessToken(): String? = suspendCancellableCoroutine { continuation ->
        val app = msalApp
        if (app == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        try {
            val account = app.getCurrentAccount()?.currentAccount
            if (account == null) {
                Log.w(TAG, "No account found")
                continuation.resume(null)
                return@suspendCancellableCoroutine
            }

            val parameters = AcquireTokenSilentParameters.Builder()
                .forAccount(account)
                .fromAuthority(account.authority)
                .withScopes(scopes.toList())
                .withCallback(object : SilentAuthenticationCallback {
                    override fun onSuccess(authenticationResult: IAuthenticationResult?) {
                        Log.d(TAG, "Token acquired silently")
                        continuation.resume(authenticationResult?.accessToken)
                    }

                    override fun onError(exception: MsalException?) {
                        Log.e(TAG, "Silent token acquisition failed", exception)
                        continuation.resume(null)
                    }
                })
                .build()

            app.acquireTokenSilentAsync(parameters)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting access token", e)
            continuation.resume(null)
        }
    }

    /**
     * Sign out
     */
    suspend fun signOut(): Boolean = suspendCancellableCoroutine { continuation ->
        val app = msalApp
        if (app == null) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }

        try {
            val account = app.getCurrentAccount()?.currentAccount
            if (account == null) {
                continuation.resume(true)
                return@suspendCancellableCoroutine
            }

            app.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
                override fun onSignOut() {
                    Log.d(TAG, "Sign out successful")
                    continuation.resume(true)
                }

                override fun onError(exception: MsalException) {
                    Log.e(TAG, "Sign out failed", exception)
                    continuation.resume(false)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error during sign out", e)
            continuation.resume(false)
        }
    }
}

/**
 * Result of authentication operation
 */
data class AuthResult(
    val success: Boolean,
    val accessToken: String? = null,
    val account: IAccount? = null,
    val error: String? = null
)
