package drone.test.core

/**
 * Single access point to whichever backend the build flavour supplied.
 *
 * `UavBackendFactory` exists once per flavour, never in `src/main`, so a
 * missing implementation is a compile error rather than a runtime surprise.
 */
object Backend {
    val uav: UavBackend = UavBackendFactory.create()
}
