package app.gocast.cast

sealed interface CastEvent {
    data object RequestCaptureConsent : CastEvent
}