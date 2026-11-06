package org.jellyfin.playback.core

import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jellyfin.playback.core.backend.BackendService
import org.jellyfin.playback.core.backend.PlayerBackend
import org.jellyfin.playback.core.plugin.PlayerService
import timber.log.Timber
import kotlin.reflect.KClass

class PlaybackManager internal constructor(
	backend: PlayerBackend,
	private val services: MutableList<PlayerService>,
	val options: PlaybackManagerOptions,
	parentJob: Job? = null,
) {
	val sessionOwner = org.jellyfin.playback.core.engine.PlaybackSessionOwner()
	internal val backendService = BackendService().also { service ->
		service.switchBackend(backend)
	}

	val backend: PlayerBackend get() = requireNotNull(backendService.backend)
	private var released = false
	fun release() {
		if (released) return
		released = true
		services.forEach { service -> runCatching { service.onRelease() }.onFailure { Timber.e(it, "Player service release failed") } }
		sessionOwner.finish("RELEASED")
		job.cancel()
		backendService.release()
	}

	private val job = SupervisorJob(parentJob)
	val state: PlayerState = MutablePlayerState(
		options = options,
		backendService = backendService,
		queue = getService()
	)

	init {
		services.forEach { it.initialize(this, state, Job(job)) }
	}

	fun addService(service: PlayerService) {
		Timber.i("Adding service $service")
		service.initialize(this, state, Job(job))
		services.add(service)
	}

	fun <T : PlayerService> getService(kclass: KClass<T>): T? {
		for (service in services) {
			@Suppress("UNCHECKED_CAST")
			if (kclass.isInstance(service)) return service as T
		}
		return null
	}

	inline fun <reified T : PlayerService> getService() = getService(T::class)

	fun removeService(service: PlayerService) {
		Timber.i("Removing service $service")
		service.onRelease()
		service.coroutineScope.cancel()
		services.remove(service)
	}
}
