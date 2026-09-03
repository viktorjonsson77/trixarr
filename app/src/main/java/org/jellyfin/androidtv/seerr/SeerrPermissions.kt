package org.jellyfin.androidtv.seerr

import org.jellyfin.androidtv.seerr.model.MediaKind

object SeerrPermissions {
	private const val ADMIN = 2L
	private const val REQUEST = 32L
	private const val REQUEST_VIEW = 16384L
	private const val REQUEST_MOVIE = 262144L
	private const val REQUEST_TV = 524288L

	fun canRequest(value: Long, kind: MediaKind): Boolean = has(value, ADMIN) ||
		has(value, REQUEST) && has(value, if (kind == MediaKind.Movie) REQUEST_MOVIE else REQUEST_TV)

	fun canViewRequests(value: Long) = has(value, ADMIN) || has(value, REQUEST_VIEW)
	fun canViewOwnRequests(value: Long) = canViewRequests(value) || canRequest(value, MediaKind.Movie) || canRequest(value, MediaKind.Series)
	private fun has(value: Long, permission: Long) = value and permission != 0L
}
