# NeaRer Business Center — current implementation and next steps

## Current local-first implementation

The Business Center is currently a private, on-device business profile and catalogue workspace. It is not a public marketplace or hosted storefront.

The local profile stores:
- Business or brand name.
- Short description/tagline.
- Business category.
- Optional service area/location.
- Optional business hours.
- Optional public contact number.

The catalogue supports up to 100 locally stored product/service listings. Each listing has a bounded title, price text, item category, description, availability state, and optional product photo. Users can search by title, category, price, description and availability, and filter products versus services.

The catalogue uses a versioned JSON codec with a legacy unversioned-array migration. It bounds stored field lengths and listing count, rejects invalid image URI schemes, skips malformed/duplicate rows, and preserves unknown or malformed future catalogue versions as read-only instead of overwriting them.

## Photo handling

Selected photos are copied into NeaRer's private `files/business-photos` directory rather than retaining long-lived permissions to the original photo provider. Import rejects non-image MIME types and source files over 30 MB, decodes to a maximum 2048-pixel dimension and stores a JPEG thumbnail-quality copy. Photos are shared using the app's existing FileProvider and temporary URI read grants. The FileProvider path is restricted to the business-photos directory.

Deleting a listing or cancelling an unsaved edit removes an imported business photo only when no saved listing still references it. Asynchronous imports are invalidated when the editor closes or is reopened.

## Sharing available today

- Share a business profile card as text.
- Share one listing with its photo and descriptive text.
- Share a bounded text summary of the catalogue; product photos are shared with individual listings.
- Android's Sharesheet lets the user choose an available destination, which may include Bluetooth, Quick Share or another installed app.

This is not yet direct NeaRer Nearby business discovery or a dedicated encrypted catalogue-transfer protocol. The app does not silently upload business data.

## Not implemented / not claimed

- Public online storefront pages or indexed marketplace discovery.
- Online order management, payment processing, stock synchronisation or delivery tracking.
- In-app seller enquiries/chat or verified seller identity.
- Dedicated NeaRer Nearby catalogue distribution/import.
- Business analytics, customer management, reviews or promotion campaigns.

These are later phases. Do not present local drafts as publicly published listings, and do not imply that a share-sheet action is an order or a confirmed sale.

## Validation

- JVM tests cover versioned round-trip, legacy migration, field bounds, image URI scheme rejection, malformed rows, duplicate IDs, maximum listing count and unknown-version preservation.
- Android CI must compile the app and run all JVM tests on the exact PR head.
- Physical testing still needs photo selection from different Android providers, app restart, deletion/permission behavior, low-storage failure, and sharing to Bluetooth/Quick Share targets.

## Android platform references

- Secure sharing with FileProvider and temporary URI grants: https://developer.android.com/training/secure-file-sharing
- FileProvider API: https://developer.android.com/reference/androidx/core/content/FileProvider
- Android Sharesheet and content URI sharing: https://developer.android.com/develop/ui/compose/sharing/send
- Storage Access Framework and persistable URI permissions: https://developer.android.com/training/data-storage/shared/documents-files

The current photo-import design copies selected content into app-private storage, so it does not need to retain the original document-provider URI permission after import.
