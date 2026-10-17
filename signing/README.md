# Release signing key

created by chatgpt-codex

`judgement-release.p12` is a password-encrypted PKCS12 keystore for the new v2 signing identity. Its password is a randomly generated secret and is deliberately excluded from this public repository. The private local signing backup contains the password and alias. Keep that backup: the encrypted file alone cannot sign an APK.

- Alias: `judgement-release`
- Certificate SHA-256: `e1f3f71c9a4821c24ab8c03c755a90f1c79ace88b507ce8177b47b16441cb89d`
- Reuse this key for updates to v2. The original app must be uninstalled before installing v2.

Never commit the password, credentials JSON, or decrypted key. Signing commands should receive passwords through environment variables, not command-line literals.
