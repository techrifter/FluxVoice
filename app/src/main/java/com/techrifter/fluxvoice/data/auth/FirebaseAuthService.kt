package com.techrifter.fluxvoice.data.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.techrifter.fluxvoice.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

private const val TAG = "FirebaseAuthService"

@Singleton
class FirebaseAuthService @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    val currentUid: String? get() = auth.currentUser?.uid
    val currentUsername: String? get() = auth.currentUser?.displayName
    val isAnonymousSignedIn: Boolean get() = auth.currentUser != null

    /**
     * Signs the user in anonymously via Firebase Auth, sets their public anonymous username,
     * persists their metadata doc to Firestore under /anonymous_users/{uid}, and saves
     * their preferences locally.
     */
    suspend fun initializeAnonymousIdentity(
        username: String
    ): Result<String> {
        val cleanUsername = username.trim().ifBlank { generateRandomUsername() }
        return try {
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }
            val user = auth.currentUser ?: error("Firebase user is null after anonymous sign-in")
            val uid = user.uid

            // 1. Update Firebase Auth user display name
            try {
                val profileChange = userProfileChangeRequest {
                    displayName = cleanUsername
                }
                user.updateProfile(profileChange).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed updating auth profile displayName: ${e.message}")
            }

            // 2. Persist to Firestore under anonymous_users collection
            try {
                val batch = firestore.batch()

                // 2a. anonymous_users/{uid}
                val userDocRef = firestore.collection("anonymous_users").document(uid)
                val userDoc = hashMapOf<String, Any>(
                    "uid" to uid,
                    "username" to cleanUsername,
                    "deviceModel" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                    "androidSdk" to android.os.Build.VERSION.SDK_INT,
                    "createdAt" to System.currentTimeMillis()
                )
                batch.set(userDocRef, userDoc, SetOptions.merge())

                batch.commit().await()
                Log.d(TAG, "Anonymous profile successfully persisted to Firestore")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed persisting profile to Firestore (may be offline): ${e.message}")
            }

            // 3. Save locally in DataStore so user is permanently authenticated locally
            settingsRepository.updateAnonymousIdentity(cleanUsername)

            Log.i(TAG, "Anonymous identity created successfully")
            Result.success(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase anonymous sign-in failed, falling back to local-only identity: ${e.message}", e)
            // Safety net: preserve local-first guarantee. If device is offline, still let them proceed!
            settingsRepository.updateAnonymousIdentity(cleanUsername)
            Result.success(auth.currentUser?.uid ?: "local_anon_${System.currentTimeMillis()}")
        }
    }

    /**
     * Generates a friendly, modern anonymous username.
     */
    fun generateRandomUsername(): String {
        val names = listOf(
            "Alex", "Jordan", "Taylor", "Morgan", "Sam", "Chris", "Nova", "Echo",
            "Aero", "Atlas", "Sage", "River", "Kai", "Rowan", "Orion", "Pixel",
            "Casey", "Logan", "Riley", "Avery", "Sky", "Robin", "Finley", "Eden"
        )
        val suffixes = listOf(
            "Dev", "Tech", "Studio", "Voice", "Flow", "Wave", "Craft",
            "Spark", "Cloud", "User", "Explorer", "Maker"
        )
        val name = names.random()
        val suffix = suffixes.random()
        val num = Random.nextInt(10, 99)
        return "$name$suffix-$num"
    }

    /**
     * Updates the anonymous developer username locally, updates Firebase Auth display name,
     * and synchronizes the username with Firestore.
     */
    suspend fun updateUsername(newUsername: String): Result<String> {
        val cleanUsername = newUsername.trim().ifBlank { generateRandomUsername() }
        val user = auth.currentUser
        if (user != null) {
            try {
                val profileChange = userProfileChangeRequest {
                    displayName = cleanUsername
                }
                user.updateProfile(profileChange).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed updating auth profile display name: ${e.message}")
            }

            try {
                val batch = firestore.batch()
                val userRef = firestore.collection("anonymous_users").document(user.uid)
                batch.set(userRef, mapOf(
                    "username" to cleanUsername,
                    "updatedAt" to FieldValue.serverTimestamp()
                ), SetOptions.merge())

                batch.commit().await()
                Log.d(TAG, "Username successfully updated in Firestore: $cleanUsername")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed updating username in Firestore (proceeding with local update): ${e.message}")
            }
        }
        settingsRepository.updateAnonymousIdentity(cleanUsername)
        return Result.success(cleanUsername)
    }
}
