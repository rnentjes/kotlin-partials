# Implementing Passkeys in Your Application

Here's a complete breakdown of what you need, from concept to implementation.

---

## 🧠 How Passkeys Work (Big Picture)

There are two flows: **Registration** and **Authentication**.

```
REGISTRATION:
User → Your Server: "I want to register"
Server → User: "Here's a challenge + options"
User → Browser/OS: WebAuthn API creates a key pair
Browser/OS → User → Server: Sends public key + signed challenge
Server: Stores the public key for that user

AUTHENTICATION:
User → Your Server: "I want to log in"
Server → User: "Here's a challenge"
User → Browser/OS: Passkey signs the challenge (biometric prompt)
Browser/OS → User → Server: Sends signed assertion
Server: Verifies signature with stored public key → issues session
```

---

## 📋 What You Need

### 1. A Server-Side WebAuthn Library

Since you're on **Kotlin/JVM**, the best option is Yubico's library:

```kotlin
// build.gradle.kts
dependencies {
  implementation("com.yubico:webauthn-server-core:2.6.1")
}
```

This handles all the cryptographic verification for you.

### 2. A Credential Storage

You need to persist registered credentials. At minimum, store:

| Field            | Type        | Purpose                            |
|------------------|-------------|------------------------------------|
| `credentialId`   | `ByteArray` | Unique ID of the passkey           |
| `publicKey`      | `ByteArray` | The public key (COSE format)       |
| `userId`         | `ByteArray` | A random handle (not the username) |
| `username`       | `String`    | Human-readable identifier          |
| `signatureCount` | `Long`      | Replay attack protection           |

This can be a database table, a file — whatever you're using for storage.

### 3. Server-Side Endpoints

You need **4 endpoints**:

| Endpoint                        | Purpose                                      |
|---------------------------------|----------------------------------------------|
| `POST /passkey/register/begin`  | Generate registration challenge + options    |
| `POST /passkey/register/finish` | Verify & store the new credential            |
| `POST /passkey/login/begin`     | Generate authentication challenge            |
| `POST /passkey/login/finish`    | Verify the signed assertion → create session |

### 4. Client-Side JavaScript

The browser does the heavy lifting via the **Web Authentication API**. You just need a small amount of JS.

---

## 🔧 Step-by-Step Implementation

### Step 1: Configure the WebAuthn Server (Server-side)

```kotlin
import com.yubico.webauthn.RelyingParty
import com.yubico.webauthn.data.RelyingPartyIdentity

val rpIdentity = RelyingPartyIdentity.builder()
  .id("yourdomain.com")       // Must match your domain
  .name("Simple Calendar")
  .build()

val rp = RelyingParty.builder()
  .identity(rpIdentity)
  .credentialRepository(yourCredentialRepo) // You implement this interface
  .origins(setOf("https://yourdomain.com"))
  .build()
```

You need to implement `CredentialRepository` — an interface from the library that looks up stored credentials by username or credential ID.

### Step 2: Registration — Begin (Server-side)

```kotlin
// POST /passkey/register/begin
fun beginRegistration(username: String): String {
  val user = UserIdentity.builder()
    .name(username)
    .displayName(username)
    .id(generateRandomBytes(32))  // random user handle
    .build()

  val options = rp.startRegistration(
    StartRegistrationOptions.builder()
      .user(user)
      .authenticatorSelection(
        AuthenticatorSelectionCriteria.builder()
          .residentKey(ResidentKeyRequirement.REQUIRED)  // enables passkey
          .userVerification(UserVerificationRequirement.PREFERRED)
          .build()
      )
      .build()
  )

  // Store `options` in session/cache — you need it in the finish step
  return options.toCredentialsCreateJson()  // JSON to send to browser
}
```

### Step 3: Registration — Browser (Client-side)

```javascript
async function registerPasskey() {
    // 1. Get options from your server
    const resp = await fetch('/passkey/register/begin', {
        method: 'POST',
        body: JSON.stringify({username: 'user@example.com'})
    });
    const options = await resp.json();

    // 2. Browser/OS handles key creation (biometric prompt appears here)
    const credential = await navigator.credentials.create({publicKey: options});

    // 3. Send the result back to your server
    await fetch('/passkey/register/finish', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(credential)
    });
}
```

> ⚠️ The `options` from the server need base64url encoding/decoding for `challenge` and `user.id`. Libraries like `@simplewebauthn/browser`
> handle this, or you can do it manually.

### Step 4: Registration — Finish (Server-side)

```kotlin
// POST /passkey/register/finish
fun finishRegistration(responseJson: String) {
  val pkc = PublicKeyCredential.parseRegistrationResponseJson(responseJson)

  val result = rp.finishRegistration(
    FinishRegistrationOptions.builder()
      .request(storedOptions)   // the options from begin step
      .response(pkc)
      .build()
  )

  // Store the credential:
  //   result.keyId.id          → credentialId
  //   result.publicKeyCose     → public key
  //   result.signatureCount    → initial count
}
```

### Step 5: Authentication — Begin (Server-side)

```kotlin
// POST /passkey/login/begin
fun beginLogin(): String {
  val options = rp.startAssertion(
    StartAssertionOptions.builder()
      .userVerification(UserVerificationRequirement.PREFERRED)
      .build()
  )

  // Store `options` in session/cache
  return options.toCredentialsGetJson()
}
```

### Step 6: Authentication — Browser (Client-side)

```javascript
async function loginWithPasskey() {
    // 1. Get challenge from server
    const resp = await fetch('/passkey/login/begin', {method: 'POST'});
    const options = await resp.json();

    // 2. Browser prompts for passkey (fingerprint/face/PIN on Android)
    const assertion = await navigator.credentials.get({publicKey: options});

    // 3. Send to server for verification
    const result = await fetch('/passkey/login/finish', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(assertion)
    });
    // → Server sets session cookie, redirect to app
}
```

### Step 7: Authentication — Finish (Server-side)

```kotlin
// POST /passkey/login/finish
fun finishLogin(responseJson: String) {
  val pkc = PublicKeyCredential.parseAssertionResponseJson(responseJson)

  val result = rp.finishAssertion(
    FinishAssertionOptions.builder()
      .request(storedOptions)
      .response(pkc)
      .build()
  )

  if (result.isSuccess) {
    // Update signatureCount in your DB
    // Create a session for result.username
  }
}
```

---

## 🔒 Important Requirements

| Requirement           | Detail                                                                                                                   |
|-----------------------|--------------------------------------------------------------------------------------------------------------------------|
| **HTTPS**             | Passkeys **only work over HTTPS** (or `localhost` for dev)                                                               |
| **Domain match**      | The `rpId` must match your actual domain                                                                                 |
| **Resident keys**     | Set `residentKey: REQUIRED` so the passkey is discoverable (no username needed at login)                                 |
| **Challenge storage** | The challenge from `begin` must be stored server-side (session/cache) and validated in `finish` — never trust the client |

---

## 🤔 What About First-Time Users?

Passkeys solve **authentication**, not **identity bootstrapping**. For the very first registration, you still need to know *who* the user
is. Options:

1. **Email verification** — send a code/link, then offer passkey registration
2. **Invite links** — you generate a link, user opens it and registers a passkey
3. **Existing login first** — keep your current OAuth for initial signup, then let users add a passkey as their primary login method

---

## 📱 What the User Experiences on Android

1. **Registration:** A bottom sheet appears → "Create a passkey for Simple Calendar" → fingerprint/face scan → done
2. **Login:** A bottom sheet appears → shows their account → fingerprint/face scan → logged in

It's the same native UI that Google Sign-In uses — equally smooth. 🎉

---

Want me to help you integrate this into your project's existing login flow?