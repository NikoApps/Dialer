package io.github.nikoapps.dialer.settings

import io.github.nikoapps.commons.settings.HelperPin

/**
 * Niko Dialer's helper PIN. The salt must never change once Dialer is released: PINs already
 * saved on phones would stop matching, and the helper would be locked out of settings.
 */
val DialerHelperPin = HelperPin(salt = "niko-dialer-helper-pin:")
