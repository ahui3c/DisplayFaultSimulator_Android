# Play Console declaration notes

## Data safety

- Does the app collect or share any required user data types? No.
- Is all user data encrypted in transit? Not applicable; the app has no network access and transmits no user data.
- Account creation: No account support.
- Data deletion request: Not applicable; no off-device user data is retained. Local scenes can be removed in the app or by clearing app data/uninstalling.

## Foreground service: specialUse

Functionality: The user starts a transparent, touch-through simulated display-fault overlay. A foreground service keeps the explicitly selected visual effect visible above the home screen and other apps. A persistent notification accurately identifies the active effect and includes a Stop action. The user can also stop it from the app or Quick Settings tile.

Impact if deferred: The effect would not appear when the user presses Start or when a user-created countdown completes, so the app's core user-requested display simulation would fail.

Impact if interrupted: The visible simulation would disappear unexpectedly during a demonstration, recording, or harmless prank. No data would be lost, but the user-requested core experience would stop.

User control: The service is initiated by the user, remains perceptible through a persistent notification, and can be stopped immediately from the notification, app, or Quick Settings tile.

Required review asset: Public video demonstrating permission setup, starting an effect, the persistent notification, touch-through behavior, and stopping the effect.

## Ads and access

- Contains ads: No.
- App access restrictions: No login or special credentials.
- Government app: No.
- News app: No.
- Health app: No.
- Financial features: No.
